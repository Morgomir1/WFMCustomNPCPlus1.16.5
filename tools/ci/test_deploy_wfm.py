#!/usr/bin/env python3
"""Unit checks for two-stand deploy mapping (WFM-compatible)."""

from __future__ import annotations

import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from deploy_wfm import (
    DeployError,
    resolve_stand_env,
    validate_stand_env,
    required_keys,
    truthy,
    deploy,
)


PROD_ENV = {
    "PTERO_PANEL": "https://pterodactyl.silweb.ru",
    "PTERO_API_KEY": "prod-key",
    "PTERO_SERVER": "prod-server",
    "PTERO_MODS_DIR": "/mods",
    "LAUNCHER_BACKEND": "ptero",
    "LAUNCHER_PTERO_API_KEY": "launcher-key",
    "LAUNCHER_PTERO_SERVER": "launcher-server",
    "LAUNCHER_PTERO_DIR": "/mods",
}

TEST_ENV = {
    **PROD_ENV,
    "TEST_PTERO_API_KEY": "test-key",
    "TEST_PTERO_SERVER": "test-server",
    "TEST_PTERO_MODS_DIR": "/test-mods",
    "TEST_LAUNCHER_PTERO_API_KEY": "test-launcher-key",
    "TEST_LAUNCHER_PTERO_SERVER": "test-launcher-server",
    "TEST_LAUNCHER_PTERO_DIR": "/test-launcher-mods",
    "TEST_LAUNCHER_BACKEND": "ptero",
}


class StandMappingTests(unittest.TestCase):
    def test_prod_uses_prod_ids(self):
        cfg = resolve_stand_env("prod", PROD_ENV)
        self.assertEqual(cfg["ptero_server"], "prod-server")
        self.assertEqual(cfg["ptero_api_key"], "prod-key")
        self.assertEqual(cfg["launcher_ptero_server"], "launcher-server")
        validate_stand_env(cfg, "both")

    def test_test_overrides_prod(self):
        cfg = resolve_stand_env("test", TEST_ENV)
        self.assertEqual(cfg["ptero_server"], "test-server")
        self.assertEqual(cfg["ptero_api_key"], "test-key")
        self.assertEqual(cfg["ptero_mods_dir"], "/test-mods")
        self.assertEqual(cfg["launcher_ptero_server"], "test-launcher-server")
        self.assertEqual(cfg["launcher_ptero_dir"], "/test-launcher-mods")
        validate_stand_env(cfg, "both")

    def test_test_stand_requires_test_server(self):
        cfg = resolve_stand_env("test", PROD_ENV)
        with self.assertRaises(DeployError):
            validate_stand_env(cfg, "server")

    def test_sftp_requires_host_and_key(self):
        env = dict(PROD_ENV)
        env["LAUNCHER_BACKEND"] = "sftp"
        cfg = resolve_stand_env("prod", env)
        keys = list(required_keys(cfg, "launcher"))
        self.assertIn("launcher_sftp_host", keys)
        with self.assertRaises(DeployError):
            validate_stand_env(cfg, "launcher")

    def test_local_backend(self):
        env = dict(PROD_ENV)
        env["LAUNCHER_BACKEND"] = "local"
        env["LAUNCHER_LOCAL_PATH"] = "/tmp/mods"
        cfg = resolve_stand_env("prod", env)
        validate_stand_env(cfg, "launcher")

    def test_truthy(self):
        self.assertTrue(truthy("true"))
        self.assertTrue(truthy("1"))
        self.assertFalse(truthy(""))
        self.assertFalse(truthy("no"))

    def test_local_copy_uses_stable_remote_name(self):
        env = dict(PROD_ENV)
        env["LAUNCHER_BACKEND"] = "local"
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "built.jar"
            src.write_bytes(b"jar")
            dest = Path(tmp) / "mods"
            env["LAUNCHER_LOCAL_PATH"] = str(dest)
            deploy(
                stand="prod",
                target="launcher",
                server_jar=src,
                launcher_jar=src,
                remote_name="CustomNPCs v1.16.5.jar",
                restart_server=False,
                restart_launcher=False,
                environ=env,
            )
            copied = dest / "CustomNPCs v1.16.5.jar"
            self.assertTrue(copied.is_file())
            self.assertEqual(copied.read_bytes(), b"jar")


def main() -> int:
    suite = unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__])
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main())
