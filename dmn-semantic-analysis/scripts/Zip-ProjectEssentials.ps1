# Zip Maven project into one archive (excluding swagger-ui, pdfs, pdfjs)
$projectRoot = Get-Location
$targetDir = Join-Path $projectRoot "zipped"

# Ensure target directory exists, clear old files
if (Test-Path $targetDir)
{
    Remove-Item "$targetDir\*" -Force
}
else
{
    New-Item -ItemType Directory -Path $targetDir | Out-Null
}

# Determine root project name from parent pom.xml
$rootPom = Get-Item "$projectRoot\pom.xml"
[xml]$pomXml = Get-Content $rootPom.FullName
$projectName = $pomXml.project.artifactId
if (-not $projectName)
{
    $projectName = "root-project"
}

# Collect only files: pom.xml, src/main/java, src/main/resources (excluding swagger-ui, pdfs, pdfjs)
$files = Get-ChildItem -Path $projectRoot -Recurse -File |
        Where-Object {
            $_.Name -eq "pom.xml" -or
                    ($_.FullName -like "*\src\*")
        }

# Prepare zip file path
$zipFile = Join-Path $targetDir ("{0}.zip" -f $projectName)

# Remove old zip if exists
if (Test-Path $zipFile)
{
    Remove-Item $zipFile -Force
}

# Compress selected files into zip
Compress-Archive -Path $files.FullName -DestinationPath $zipFile

Write-Host "Zipping complete. Archive written to $zipFile"
