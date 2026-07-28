# Serialize Maven project into minimal text files (<= 128000 chars each)
$projectRoot = Get-Location
$targetDir = Join-Path $projectRoot "serialized"

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

# Collect only files: pom.xml, src/main/java, src/main/resources (excluding swagger-ui/pdfjs)
$files = Get-ChildItem -Path $projectRoot -Recurse -File |
        Where-Object {
            $_.Name -eq "pom.xml" -or
                    ($_.FullName -like "*\src\main\java\*") -or
                    ($_.FullName -like "*\src\main\resources\*" -and
                            $_.FullName -notlike "*\src\main\resources\app-ui\*" -and
                            $_.FullName -notlike "*\src\main\resources\swagger-ui\*" -and
                            $_.FullName -notlike "*\src\main\resources\pdfjs\*")
        }

# Build one big string with header and delimiters
$bigContent = "Project: $projectName`n`n"
$bigContent += 'instruction: each artifact is delimited by prefix "===== ARTIFACT:"' + "`n`n"

# Get project directory name (leaf folder)
$projectDirName = Split-Path $projectRoot -Leaf

foreach ($file in $files)
{
    # Get relative path from project root
    $relativePath = Resolve-Path -Relative $file.FullName

    # Prepend project directory name
    $artifact = Join-Path $projectDirName ($relativePath.TrimStart('.\'))

    $fileContent = Get-Content $file.FullName -Raw
    $delimiter = "`n===== ARTIFACT: $artifact =====`n"
    $bigContent += $delimiter + $fileContent + "`n"
}

# Function to split string into chunks of max length
function Split-StringByLength
{
    param([string]$text, [int]$length)
    $result = @()
    for ($i = 0; $i -lt $text.Length; $i += $length) {
        $end = [Math]::Min($length, $text.Length - $i)
        $result += $text.Substring($i, $end)
    }
    return $result
}

# Split into chunks of max 118000 characters
$chunks = Split-StringByLength -text $bigContent -length 118000

$counter = 1
foreach ($chunk in $chunks)
{
    $outFile = Join-Path $targetDir ("{0}_{1}.txt" -f $projectName, $counter)
    Set-Content -Path $outFile -Value $chunk
    $counter++
}

Write-Host "Serialization complete. Files written to $targetDir"
