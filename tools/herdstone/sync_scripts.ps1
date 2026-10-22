# Копирует скрипты Herdstone в dev-мир CustomNPC+.
# Мод файлы из resources не грузит: NPC читает только <world>/customnpcs/scripts/ecmascript/.
$ErrorActionPreference = "Stop"
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$src = Join-Path $repo "src\main\resources\scripts\herdstone"
$dst = Join-Path $repo "run\saves\New World123\customnpcs\scripts\ecmascript\herdstone"

if (-not (Test-Path $src)) {
    throw "нет папки исходников: $src"
}

New-Item -ItemType Directory -Force -Path $dst | Out-Null
Copy-Item -Path (Join-Path $src "*.js") -Destination $dst -Force
$n = (Get-ChildItem -Path $dst -Filter "*.js").Count
Write-Host "herdstone: $n js -> $dst"
