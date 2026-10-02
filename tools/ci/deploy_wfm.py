#!/usr/bin/env python3
"""Deploy CustomNPCs (same stand/target contract as the WFM mod)."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Dict, Iterable, Mapping, Optional, Tuple

DEFAULT_PANEL = "https://pterodactyl.silweb.ru"
DEFAULT_MODS_DIR = "/mods"
DEFAULT_UPLOAD_TIMEOUT = 300
DEFAULT_UPLOAD_ATTEMPTS = 3


def env_get(name: str, default: str = "") -> str:
    value = os.environ.get(name)
    if value is None:
        return default
    return value.strip()


def truthy(value: str) -> bool:
    return value.lower() in {"1", "true", "yes", "on"}


def as_int(value: str, default: int) -> int:
    if not value:
        return default
    try:
        return int(value)
    except ValueError:
        return default


def ssl_context(insecure: bool) -> ssl.SSLContext:
    if insecure:
        ctx = ssl._create_unverified_context()
        return ctx
    return ssl.create_default_context()


class DeployError(RuntimeError):
    pass


def resolve_stand_env(stand: str, environ: Optional[Mapping[str, str]] = None) -> Dict[str, str]:
    """Map GitHub env vars onto one stand. test* overrides, otherwise prod values."""
    env = dict(os.environ if environ is None else environ)
    stand = stand.lower()
    if stand not in {"prod", "test"}:
        raise DeployError("stand must be prod or test")

    def pick(*names: str, default: str = "") -> str:
        for name in names:
            raw = env.get(name)
            if raw is not None and str(raw).strip():
                return str(raw).strip()
        return default

    if stand == "prod":
        return {
            "stand": stand,
            "ptero_panel": pick("PTERO_PANEL", default=DEFAULT_PANEL),
            "ptero_api_key": pick("PTERO_API_KEY"),
            "ptero_server": pick("PTERO_SERVER"),
            "ptero_mods_dir": pick("PTERO_MODS_DIR", default=DEFAULT_MODS_DIR),
            "ptero_insecure": pick("PTERO_INSECURE"),
            "ptero_upload_timeout": pick("PTERO_UPLOAD_TIMEOUT"),
            "ptero_upload_attempts": pick("PTERO_UPLOAD_ATTEMPTS"),
            "ptero_warn_seconds": pick("PTERO_WARN_SECONDS"),
            "ptero_restart_command": pick("PTERO_RESTART_COMMAND"),
            "launcher_backend": pick("LAUNCHER_BACKEND", default="ptero"),
            "launcher_local_path": pick("LAUNCHER_LOCAL_PATH"),
            "launcher_sftp_host": pick("LAUNCHER_SFTP_HOST"),
            "launcher_sftp_user": pick("LAUNCHER_SFTP_USER"),
            "launcher_sftp_key": pick("LAUNCHER_SFTP_KEY"),
            "launcher_sftp_path": pick("LAUNCHER_SFTP_PATH"),
            "launcher_sftp_port": pick("LAUNCHER_SFTP_PORT", default="22"),
            "launcher_ptero_panel": pick("LAUNCHER_PTERO_PANEL", "PTERO_PANEL", default=DEFAULT_PANEL),
            "launcher_ptero_api_key": pick("LAUNCHER_PTERO_API_KEY", "PTERO_API_KEY"),
            "launcher_ptero_server": pick("LAUNCHER_PTERO_SERVER"),
            "launcher_ptero_dir": pick("LAUNCHER_PTERO_DIR", default=DEFAULT_MODS_DIR),
            "launcher_ptero_insecure": pick("LAUNCHER_PTERO_INSECURE", "PTERO_INSECURE"),
            "launcher_warn_seconds": pick("LAUNCHER_WARN_SECONDS"),
            "launcher_restart_command": pick("LAUNCHER_RESTART_COMMAND"),
        }

    return {
        "stand": stand,
        "ptero_panel": pick("TEST_PTERO_PANEL", "PTERO_PANEL", default=DEFAULT_PANEL),
        "ptero_api_key": pick("TEST_PTERO_API_KEY", "PTERO_API_KEY"),
        "ptero_server": pick("TEST_PTERO_SERVER"),
        "ptero_mods_dir": pick("TEST_PTERO_MODS_DIR", "PTERO_MODS_DIR", default=DEFAULT_MODS_DIR),
        "ptero_insecure": pick("TEST_PTERO_INSECURE", "PTERO_INSECURE"),
        "ptero_upload_timeout": pick("PTERO_UPLOAD_TIMEOUT"),
        "ptero_upload_attempts": pick("PTERO_UPLOAD_ATTEMPTS"),
        "ptero_warn_seconds": pick("TEST_PTERO_WARN_SECONDS", "PTERO_WARN_SECONDS"),
        "ptero_restart_command": pick("PTERO_RESTART_COMMAND", "TEST_PTERO_RESTART_COMMAND"),
        "launcher_backend": pick("TEST_LAUNCHER_BACKEND", "LAUNCHER_BACKEND", default="ptero"),
        "launcher_local_path": pick("TEST_LAUNCHER_LOCAL_PATH", "LAUNCHER_LOCAL_PATH"),
        "launcher_sftp_host": pick("TEST_LAUNCHER_SFTP_HOST", "LAUNCHER_SFTP_HOST"),
        "launcher_sftp_user": pick("TEST_LAUNCHER_SFTP_USER", "LAUNCHER_SFTP_USER"),
        "launcher_sftp_key": pick("TEST_LAUNCHER_SFTP_KEY", "LAUNCHER_SFTP_KEY"),
        "launcher_sftp_path": pick("TEST_LAUNCHER_SFTP_PATH", "LAUNCHER_SFTP_PATH"),
        "launcher_sftp_port": pick("TEST_LAUNCHER_SFTP_PORT", "LAUNCHER_SFTP_PORT", default="22"),
        "launcher_ptero_panel": pick(
            "TEST_LAUNCHER_PTERO_PANEL",
            "LAUNCHER_PTERO_PANEL",
            "TEST_PTERO_PANEL",
            "PTERO_PANEL",
            default=DEFAULT_PANEL,
        ),
        "launcher_ptero_api_key": pick(
            "TEST_LAUNCHER_PTERO_API_KEY",
            "LAUNCHER_PTERO_API_KEY",
            "TEST_PTERO_API_KEY",
            "PTERO_API_KEY",
        ),
        "launcher_ptero_server": pick("TEST_LAUNCHER_PTERO_SERVER", "LAUNCHER_PTERO_SERVER"),
        "launcher_ptero_dir": pick(
            "TEST_LAUNCHER_PTERO_DIR",
            "LAUNCHER_PTERO_DIR",
            default=DEFAULT_MODS_DIR,
        ),
        "launcher_ptero_insecure": pick(
            "TEST_LAUNCHER_PTERO_INSECURE",
            "LAUNCHER_PTERO_INSECURE",
            "TEST_PTERO_INSECURE",
            "PTERO_INSECURE",
        ),
        "launcher_warn_seconds": pick("TEST_LAUNCHER_WARN_SECONDS", "LAUNCHER_WARN_SECONDS"),
        "launcher_restart_command": pick("LAUNCHER_RESTART_COMMAND", "TEST_LAUNCHER_RESTART_COMMAND"),
    }


def required_keys(stand_env: Mapping[str, str], target: str) -> Iterable[str]:
    target = target.lower()
    keys = []
    if target in {"server", "both"}:
        keys.extend(["ptero_api_key", "ptero_server"])
    if target in {"launcher", "both"}:
        backend = (stand_env.get("launcher_backend") or "ptero").lower()
        if backend == "local":
            keys.append("launcher_local_path")
        elif backend == "sftp":
            keys.extend(
                [
                    "launcher_sftp_host",
                    "launcher_sftp_user",
                    "launcher_sftp_key",
                    "launcher_sftp_path",
                ]
            )
        else:
            keys.extend(["launcher_ptero_api_key", "launcher_ptero_server"])
    return keys


def validate_stand_env(stand_env: Mapping[str, str], target: str) -> None:
    missing = [key for key in required_keys(stand_env, target) if not stand_env.get(key)]
    if missing:
        raise DeployError(
            "Не заданы переменные для стенда {stand}/{target}: {keys}".format(
                stand=stand_env.get("stand"),
                target=target,
                keys=", ".join(missing),
            )
        )


def _http_request(
    url: str,
    *,
    method: str = "GET",
    headers: Optional[Dict[str, str]] = None,
    data: Optional[bytes] = None,
    insecure: bool = False,
    timeout: int = 60,
) -> Tuple[int, bytes]:
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, context=ssl_context(insecure), timeout=timeout) as resp:
            return resp.getcode(), resp.read()
    except urllib.error.HTTPError as exc:
        body = exc.read() if exc.fp else b""
        raise DeployError("HTTP {code} {url}: {body}".format(code=exc.code, url=url, body=body[:500])) from exc
    except urllib.error.URLError as exc:
        raise DeployError("URL error {url}: {err}".format(url=url, err=exc.reason)) from exc


def ptero_headers(api_key: str) -> Dict[str, str]:
    return {
        "Authorization": "Bearer {0}".format(api_key),
        "Accept": "Application/vnd.pterodactyl.v1+json",
        "Content-Type": "application/json",
    }


def ptero_send_command(
    panel: str,
    api_key: str,
    server: str,
    command: str,
    *,
    insecure: bool = False,
) -> None:
    url = "{panel}/api/client/servers/{server}/command".format(
        panel=panel.rstrip("/"), server=server
    )
    payload = json.dumps({"command": command}).encode("utf-8")
    _http_request(url, method="POST", headers=ptero_headers(api_key), data=payload, insecure=insecure)


def ptero_power(
    panel: str,
    api_key: str,
    server: str,
    signal: str = "restart",
    *,
    insecure: bool = False,
) -> None:
    url = "{panel}/api/client/servers/{server}/power".format(
        panel=panel.rstrip("/"), server=server
    )
    payload = json.dumps({"signal": signal}).encode("utf-8")
    _http_request(url, method="POST", headers=ptero_headers(api_key), data=payload, insecure=insecure)


def _multipart(field: str, filename: str, content: bytes) -> Tuple[bytes, str]:
    boundary = "----CursorDeploy{0}".format(os.urandom(8).hex())
    disposition = (
        'Content-Disposition: form-data; name="{field}"; filename="{filename}"'.format(
            field=field, filename=filename.replace('"', "")
        )
    )
    parts = [
        "--{0}".format(boundary).encode("utf-8"),
        disposition.encode("utf-8"),
        b"Content-Type: application/java-archive",
        b"",
        content,
        "--{0}--".format(boundary).encode("utf-8"),
        b"",
    ]
    body = b"\r\n".join(parts)
    return body, "multipart/form-data; boundary={0}".format(boundary)


def ptero_upload(
    panel: str,
    api_key: str,
    server: str,
    directory: str,
    local_path: Path,
    remote_name: str,
    *,
    insecure: bool = False,
    timeout: int = DEFAULT_UPLOAD_TIMEOUT,
    attempts: int = DEFAULT_UPLOAD_ATTEMPTS,
) -> None:
    directory = directory if directory.startswith("/") else "/{0}".format(directory)
    get_url = "{panel}/api/client/servers/{server}/files/upload".format(
        panel=panel.rstrip("/"), server=server
    )
    last_error: Optional[Exception] = None
    content = local_path.read_bytes()
    for attempt in range(1, max(1, attempts) + 1):
        try:
            _, raw = _http_request(get_url, headers=ptero_headers(api_key), insecure=insecure, timeout=60)
            parsed = json.loads(raw.decode("utf-8"))
            signed = parsed["attributes"]["url"]
            upload_url = "{signed}{sep}directory={dir}".format(
                signed=signed,
                sep="&" if "?" in signed else "?",
                dir=urllib.parse.quote(directory, safe="/"),
            )
            body, content_type = _multipart("files", remote_name, content)
            headers = {
                "Content-Type": content_type,
                "Accept": "application/json",
            }
            _http_request(
                upload_url,
                method="POST",
                headers=headers,
                data=body,
                insecure=insecure,
                timeout=timeout,
            )
            print("Uploaded {name} -> {panel} {server}:{directory}/{name}".format(
                name=remote_name, panel=panel, server=server, directory=directory
            ))
            return
        except Exception as exc:  # noqa: BLE001 — retry upload
            last_error = exc
            print("Upload attempt {0}/{1} failed: {2}".format(attempt, attempts, exc), file=sys.stderr)
            time.sleep(min(8, attempt * 2))
    raise DeployError("Pterodactyl upload failed: {0}".format(last_error))


def warn_and_restart(
    panel: str,
    api_key: str,
    server: str,
    *,
    insecure: bool,
    warn_seconds: int,
    restart_command: str,
    label: str,
) -> None:
    if warn_seconds > 0:
        msg = "say [{label}] Рестарт через {sec} сек (деплой мода)".format(label=label, sec=warn_seconds)
        try:
            ptero_send_command(panel, api_key, server, msg, insecure=insecure)
        except DeployError as exc:
            print("Warn command failed ({0}): {1}".format(label, exc), file=sys.stderr)
        time.sleep(warn_seconds)
    if restart_command:
        print("Sending command to {0}: {1}".format(label, restart_command))
        ptero_send_command(panel, api_key, server, restart_command, insecure=insecure)
    else:
        print("Restarting {0} via panel".format(label))
        ptero_power(panel, api_key, server, "restart", insecure=insecure)


def copy_local(local_path: Path, dest_dir: str, remote_name: str) -> None:
    dest = Path(dest_dir)
    dest.mkdir(parents=True, exist_ok=True)
    target = dest / remote_name
    shutil.copy2(local_path, target)
    print("Copied {src} -> {dst}".format(src=local_path, dst=target))


def copy_sftp(
    local_path: Path,
    host: str,
    user: str,
    key_data: str,
    remote_dir: str,
    remote_name: str,
    port: int,
) -> None:
    remote_dir = remote_dir.rstrip("/")
    with tempfile.TemporaryDirectory() as tmp:
        key_path = Path(tmp) / "sftp_key"
        key_path.write_text(key_data if key_data.endswith("\n") else key_data + "\n", encoding="utf-8")
        key_path.chmod(0o600)
        remote = "{user}@{host}:{dir}/{name}".format(
            user=user, host=host, dir=remote_dir, name=remote_name
        )
        cmd = [
            "scp",
            "-o",
            "StrictHostKeyChecking=accept-new",
            "-P",
            str(port),
            "-i",
            str(key_path),
            str(local_path),
            remote,
        ]
        print("SCP {0} -> {1}".format(local_path.name, remote))
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            raise DeployError("scp failed: {0}".format(result.stderr.strip() or result.stdout.strip()))


def deploy(
    *,
    stand: str,
    target: str,
    server_jar: Path,
    launcher_jar: Path,
    remote_name: str,
    restart_server: bool,
    restart_launcher: bool,
    environ: Optional[Mapping[str, str]] = None,
) -> None:
    target = target.lower()
    if target not in {"both", "launcher", "server"}:
        raise DeployError("target must be both, launcher or server")
    cfg = resolve_stand_env(stand, environ)
    validate_stand_env(cfg, target)

    timeout = as_int(cfg["ptero_upload_timeout"], DEFAULT_UPLOAD_TIMEOUT)
    attempts = as_int(cfg["ptero_upload_attempts"], DEFAULT_UPLOAD_ATTEMPTS)

    if target in {"server", "both"}:
        if not server_jar.is_file():
            raise DeployError("Server jar not found: {0}".format(server_jar))
        ptero_upload(
            cfg["ptero_panel"],
            cfg["ptero_api_key"],
            cfg["ptero_server"],
            cfg["ptero_mods_dir"] or DEFAULT_MODS_DIR,
            server_jar,
            remote_name,
            insecure=truthy(cfg["ptero_insecure"]),
            timeout=timeout,
            attempts=attempts,
        )
        if restart_server:
            warn_and_restart(
                cfg["ptero_panel"],
                cfg["ptero_api_key"],
                cfg["ptero_server"],
                insecure=truthy(cfg["ptero_insecure"]),
                warn_seconds=as_int(cfg["ptero_warn_seconds"], 0),
                restart_command=cfg["ptero_restart_command"],
                label="server",
            )

    if target in {"launcher", "both"}:
        if not launcher_jar.is_file():
            raise DeployError("Launcher jar not found: {0}".format(launcher_jar))
        backend = (cfg["launcher_backend"] or "ptero").lower()
        if backend == "local":
            copy_local(launcher_jar, cfg["launcher_local_path"], remote_name)
        elif backend == "sftp":
            copy_sftp(
                launcher_jar,
                cfg["launcher_sftp_host"],
                cfg["launcher_sftp_user"],
                cfg["launcher_sftp_key"],
                cfg["launcher_sftp_path"],
                remote_name,
                as_int(cfg["launcher_sftp_port"], 22),
            )
        else:
            ptero_upload(
                cfg["launcher_ptero_panel"],
                cfg["launcher_ptero_api_key"],
                cfg["launcher_ptero_server"],
                cfg["launcher_ptero_dir"] or DEFAULT_MODS_DIR,
                launcher_jar,
                remote_name,
                insecure=truthy(cfg["launcher_ptero_insecure"]),
                timeout=timeout,
                attempts=attempts,
            )
        if restart_launcher:
            if not cfg["launcher_ptero_server"] or not cfg["launcher_ptero_api_key"]:
                print("Launcher restart skipped: no LAUNCHER_PTERO_* credentials", file=sys.stderr)
            else:
                warn_and_restart(
                    cfg["launcher_ptero_panel"],
                    cfg["launcher_ptero_api_key"],
                    cfg["launcher_ptero_server"],
                    insecure=truthy(cfg["launcher_ptero_insecure"]),
                    warn_seconds=as_int(cfg["launcher_warn_seconds"], 0),
                    restart_command=cfg["launcher_restart_command"],
                    label="launcher",
                )


def parse_args(argv: Optional[Iterable[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Deploy CustomNPCs to Pterodactyl stands (WFM-compatible).")
    parser.add_argument("--stand", required=True, choices=["prod", "test"])
    parser.add_argument("--target", required=True, choices=["both", "launcher", "server"])
    parser.add_argument("--server-jar", required=True)
    parser.add_argument("--launcher-jar", required=True)
    parser.add_argument(
        "--remote-name",
        default="CustomNPCs v1.16.5.jar",
        help="Filename on the stand (overwrite in mods folder)",
    )
    parser.add_argument("--restart-server", action="store_true")
    parser.add_argument("--restart-launcher", action="store_true")
    return parser.parse_args(list(argv) if argv is not None else None)


def main(argv: Optional[Iterable[str]] = None) -> int:
    args = parse_args(argv)
    try:
        deploy(
            stand=args.stand,
            target=args.target,
            server_jar=Path(args.server_jar),
            launcher_jar=Path(args.launcher_jar),
            remote_name=args.remote_name,
            restart_server=args.restart_server,
            restart_launcher=args.restart_launcher,
        )
    except DeployError as exc:
        print("Deploy failed: {0}".format(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
