#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
result_dir=${1:-dmn-benchmarks/results}
include=${2:-'.*(CreditApproval|TrafficViolation|ScalarArithmetic|Originations|RankedLoanProducts).*'}
mkdir -p "$repo_root/$result_dir"
cd "$repo_root"

mvn --batch-mode -pl dmn-benchmarks -am package -DskipTests
for threads in 1 2 4 8; do
  java -jar dmn-benchmarks/target/benchmarks.jar "$include" \
    -f 3 -wi 5 -i 5 -w 5s -r 10s -t "$threads" -prof gc \
    -rf json -rff "$result_dir/jmh-$threads-thread.json"
done

{
  printf 'timestamp_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'git_commit=%s\n' "$(git rev-parse HEAD)"
  if test -z "$(git status --porcelain)"; then printf 'git_status=clean\n'; else printf 'git_status=dirty\n'; fi
  printf 'java=%s\n' "$(java -version 2>&1 | head -n 1)"
  printf 'os=%s\n' "$(uname -a)"
  printf 'logical_processors=%s\n' "$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf unknown)"
  printf 'threads=1,2,4,8\nforks=3\nprofiler=gc\n'
} > "$result_dir/environment.properties"
