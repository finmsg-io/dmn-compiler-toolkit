#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
result_dir=${1:-dmn-benchmarks/results/dq-004}
mkdir -p "$repo_root/$result_dir"
cd "$repo_root"

mvn --batch-mode -pl dmn-benchmarks -am package -DskipTests
for threads in 1 2 4 8; do
  java -jar dmn-benchmarks/target/benchmarks.jar Mt564DataQualityBenchmark \
    -f 3 -wi 5 -i 5 -w 5s -r 10s -t "$threads" -prof gc \
    -rf json -rff "$result_dir/mt564-jmh-$threads-thread.json"
done

{
  printf 'timestamp_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'git_commit=%s\n' "$(git rev-parse HEAD)"
  if test -z "$(git status --porcelain)"; then printf 'git_status=clean\n'; else printf 'git_status=dirty\n'; fi
  printf 'model=dmn-models/src/main/resources/models/data-quality/swift-mt564-dqm.dmn\n'
  printf 'java=%s\n' "$(java -version 2>&1 | head -n 1)"
  printf 'os=%s\n' "$(uname -a)"
  printf 'logical_processors=%s\n' "$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf unknown)"
  printf 'threads=1,2,4,8\nwarmup=5x5s\nmeasurement=5x10s\nforks=3\nprofiler=gc\n'
} > "$result_dir/mt564-environment.properties"

python3 tools/summarize_mt564_benchmarks.py "$result_dir"
