# scripts/zip-modules.ps1
[CmdletBinding()]
param (
# Automatically resolves to the parent root directory (one level up from scripts/)
    [string]$ParentDir = (Resolve-Path (Join-Path -Path $PSScriptRoot -ChildPath "..")).Path
)

$ErrorActionPreference = 'Stop'

# Define output directory under parent/zips
$ZipOutputDir = Join-Path -Path $ParentDir -ChildPath "zips"

# Ensure target directory exists and clear old archives
if (Test-Path $ZipOutputDir) {
    Remove-Item "$ZipOutputDir\*" -Force -Recurse
} else {
    New-Item -ItemType Directory -Path $ZipOutputDir | Out-Null
}

Write-Host "Parent Directory: $ParentDir" -ForegroundColor Gray
Write-Host "Output Directory: $ZipOutputDir" -ForegroundColor Cyan

# --- 1. Process Parent Module ---
$ParentPomPath = Join-Path -Path $ParentDir -ChildPath "pom.xml"

if (-not (Test-Path $ParentPomPath)) {
    Write-Error "Parent pom.xml not found at $ParentPomPath. Please verify script location."
}

[xml]$ParentPomXml = Get-Content -Path $ParentPomPath

# Determine Parent Zip Name using <artifactId>
$ParentName = $ParentPomXml.project.artifactId
if (-not $ParentName) { $ParentName = (Get-Item $ParentDir).Name }
$ParentZipPath = Join-Path -Path $ZipOutputDir -ChildPath "$ParentName.zip"

Write-Host "`n[Processing Parent Module: $ParentName]" -ForegroundColor Yellow

# Collect Parent files (pom.xml and docs directory recursively)
$ParentFiles = Get-ChildItem -Path $ParentDir -Depth 2 -File | Where-Object {
    $_.Name -eq "pom.xml" -or $_.FullName -like "*\docs\*"
}

if ($ParentFiles) {
    Compress-Archive -Path $ParentFiles.FullName -DestinationPath $ParentZipPath -Force
    Write-Host " -> Parent zipped to: $ParentZipPath" -ForegroundColor Green
} else {
    Write-Host " -> No matching parent files (pom.xml or docs) found." -ForegroundColor DarkGray
}

# --- 2. Extract Submodules from Parent pom.xml ---
$SubmoduleRelativePaths = $ParentPomXml.project.modules.module

if (-not $SubmoduleRelativePaths) {
    Write-Host "`nNo submodules found in parent pom.xml." -ForegroundColor Yellow
    exit
}

# Ensure array format even if only 1 submodule exists
$SubmoduleRelativePaths = @($SubmoduleRelativePaths)

# --- 3. Process Submodules ---
foreach ($relPath in $SubmoduleRelativePaths) {
    # Resolve relative module path from parent directory (supports nested modules)
    $ModuleDir = [System.IO.Path]::GetFullPath((Join-Path -Path $ParentDir -ChildPath $relPath))

    if (-not (Test-Path $ModuleDir)) {
        Write-Warning "Module directory not found: $ModuleDir (Skipping)"
        continue
    }

    $ModulePomPath = Join-Path -Path $ModuleDir -ChildPath "pom.xml"
    if (-not (Test-Path $ModulePomPath)) {
        Write-Warning "No pom.xml in module directory: $ModuleDir (Skipping)"
        continue
    }

    # Extract submodule artifactId for ZIP name
    [xml]$ModulePomXml = Get-Content -Path $ModulePomPath
    $ModuleName = $ModulePomXml.project.artifactId
    if (-not $ModuleName) { $ModuleName = (Get-Item $ModuleDir).Name }

    $ModuleZipPath = Join-Path -Path $ZipOutputDir -ChildPath "$ModuleName.zip"
    Write-Host "`n[Processing Submodule: $ModuleName]" -ForegroundColor Yellow

    # Collect submodule files: pom.xml or src/* (excluding swagger-ui, pdfs, pdfjs)
    $ModuleFiles = Get-ChildItem -Path $ModuleDir -Recurse -File | Where-Object {
        $isPom = $_.Name -eq "pom.xml"
        $isSrc = $_.FullName -like "*\src\*"

        # Exclusions based on reference rules
        $isExcluded = $_.FullName -like "*\swagger-ui\*" -or
                $_.FullName -like "*\pdfs\*" -or
                $_.FullName -like "*\pdfjs\*"

        ($isPom -or $isSrc) -and (-not $isExcluded)
    }

    if ($ModuleFiles) {
        Compress-Archive -Path $ModuleFiles.FullName -DestinationPath $ModuleZipPath -Force
        Write-Host " -> Submodule zipped to: $ModuleZipPath" -ForegroundColor Green
    } else {
        Write-Host " -> No relevant files found in $ModuleName to zip." -ForegroundColor DarkGray
    }
}

Write-Host "`nAll operations completed successfully!" -ForegroundColor BrightWhite