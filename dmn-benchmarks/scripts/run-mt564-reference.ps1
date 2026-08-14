param(
    [string]$ResultDirectory = "dmn-benchmarks/results/dq-004",
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
        $output = Join-Path $resultPath "mt564-jmh-$threadCount-thread.json"
        java -jar dmn-benchmarks/target/benchmarks.jar Mt564DataQualityBenchmark `
            -f 3 -wi 5 -i 5 -w 5s -r 10s -t $threadCount -prof gc `
            -rf json -rff $output
    }
    @(
        "timestamp_utc=$([DateTime]::UtcNow.ToString('o'))"
        "git_commit=$(git rev-parse HEAD)"
        "git_status=$(if ((git status --porcelain).Length -eq 0) { 'clean' } else { 'dirty' })"
        "model=dmn-models/src/main/resources/models/data-quality/swift-mt564-dqm.dmn"
        "java=$(java -version 2>&1 | Select-Object -First 1)"
        "os=$([System.Runtime.InteropServices.RuntimeInformation]::OSDescription)"
        "logical_processors=$([Environment]::ProcessorCount)"
        "threads=$($Threads -join ',')"
        "warmup=5x5s"
        "measurement=5x10s"
        "forks=3"
        "profiler=gc"
    ) | Set-Content (Join-Path $resultPath "mt564-environment.properties")
    if ($Threads -join ',' -eq '1,2,4,8') {
        python tools/summarize_mt564_benchmarks.py $resultPath
    }
} finally {
    Pop-Location
}
