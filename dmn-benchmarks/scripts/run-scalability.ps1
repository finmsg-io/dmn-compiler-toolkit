param(
    [string]$ResultDirectory = "dmn-benchmarks/results",
    [string]$Include = ".*(CreditApproval|TrafficViolation|ScalarArithmetic|Originations|RankedLoanProducts).*",
    [int[]]$Threads = @(1, 2, 4, 8)
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$resultPath = Join-Path $repositoryRoot $ResultDirectory
New-Item -ItemType Directory -Force -Path $resultPath | Out-Null

Push-Location $repositoryRoot
try {
    mvn --batch-mode -pl dmn-benchmarks -am package -DskipTests
    foreach ($threadCount in $Threads) {
        $output = Join-Path $resultPath "jmh-$threadCount-thread.json"
        java -jar dmn-benchmarks/target/benchmarks.jar $Include `
            -f 3 -wi 5 -i 5 -w 5s -r 10s -t $threadCount -prof gc `
            -rf json -rff $output
    }
    @(
        "timestamp_utc=$([DateTime]::UtcNow.ToString('o'))"
        "git_commit=$(git rev-parse HEAD)"
        "git_status=$(if ((git status --porcelain).Length -eq 0) { 'clean' } else { 'dirty' })"
        "java=$(java -version 2>&1 | Select-Object -First 1)"
        "os=$([System.Runtime.InteropServices.RuntimeInformation]::OSDescription)"
        "logical_processors=$([Environment]::ProcessorCount)"
        "threads=$($Threads -join ',')"
        "forks=3"
        "profiler=gc"
    ) | Set-Content (Join-Path $resultPath "environment.properties")
} finally {
    Pop-Location
}
