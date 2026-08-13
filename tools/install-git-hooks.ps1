$ErrorActionPreference = "Stop"

$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
git -C $repositoryRoot -c "safe.directory=$repositoryRoot" config core.hooksPath .githooks
Write-Host "Installed repository Git hooks from .githooks."
Write-Host "Commits now run the Spotless formatting check before they are created."
