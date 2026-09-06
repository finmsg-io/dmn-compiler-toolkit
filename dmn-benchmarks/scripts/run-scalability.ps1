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
    $proc = Get-CimInstance Win32_Processor | Select-Object -First 1
    $sys = Get-CimInstance Win32_ComputerSystem | Select-Object -First 1
    $osInfo = Get-CimInstance Win32_OperatingSystem | Select-Object -First 1
    $totalRamGb = [math]::Round($sys.TotalPhysicalMemory / 1GB, 2)
    $javaVersion = & java -version 2>&1 | Select-Object -First 1

    @(
        "timestamp_utc=$([DateTime]::UtcNow.ToString('o'))"
        "git_commit=$(git rev-parse HEAD)"
        "git_status=$(if ((git status --porcelain).Length -eq 0) { 'clean' } else { 'dirty' })"
        "cpu_processor=$($proc.Name.Trim())"
        "physical_cores=$($proc.NumberOfCores)"
        "logical_processors=$($proc.NumberOfLogicalProcessors)"
        "memory_total_gb=$totalRamGb GB"
        "computer_model=$($sys.Manufacturer.Trim()) $($sys.Model.Trim())"
        "os=$($osInfo.Caption) $($osInfo.OSArchitecture) (Version $($osInfo.Version))"
        "java=$javaVersion"
        "threads=$($Threads -join ',')"
        "forks=3"
        "warmup=5x5s"
        "measurement=5x10s"
        "profiler=gc"
    ) | Set-Content (Join-Path $resultPath "environment.properties")
} finally {
    Pop-Location
}
