<#
.SYNOPSIS
Configures the existing Maven installation for the current PowerShell session.
.EXAMPLE
.\script\setup-maven.ps1
mvn --version
#>
[CmdletBinding()]
param(
    [string]$MavenHome = 'C:\10-tools\apache-maven-3.9.12'
)

$mavenCommand = Join-Path $MavenHome 'bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $mavenCommand -PathType Leaf)) {
    throw "Maven was not found at '$mavenCommand'. Install Maven there or pass -MavenHome."
}

$env:MAVEN_HOME = (Resolve-Path -LiteralPath $MavenHome).Path
$mavenBin = Join-Path $env:MAVEN_HOME 'bin'
$otherPaths = @($env:Path -split ';' | Where-Object {
    $_ -and $_.TrimEnd('\') -ine $mavenBin.TrimEnd('\')
})
$env:Path = (@($mavenBin) + $otherPaths) -join ';'

& $mavenCommand --version
if ($LASTEXITCODE -ne 0) {
    throw 'Maven verification failed. Check that JAVA_HOME points to a valid JDK.'
}
