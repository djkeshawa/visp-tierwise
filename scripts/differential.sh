#!/usr/bin/env bash
# Cross-language differential run (requirements section 12; implementation plan 4.4).
#
# Usage: scripts/differential.sh [--seed N] [--cases N] [--out DIR] [--skip-build]
#
#   --seed N       the run seed (default 1); the same seed gives the same cases
#   --cases N      the number of cases to write and replay (default 2000)
#   --out DIR      where cases.jsonl is written (default java/tierwise-core/target/differential)
#   --skip-build   do not build the JavaScript packages first (they must already be built)
#
# 1. The Java harness (DifferentialHarnessTest) draws random models, bundles and calls, evaluates each in Java, and
#    writes DIR/cases.jsonl: one case per line, with the bundle, the inputs, the Java result or error code, and the
#    listener events.
# 2. The JavaScript replay (js/evaluator/test/differential.test.mjs) replays every line through the built evaluator and
#    compares the results, the codes and the events.
#
# The script exits non-zero when the Java harness fails, when the case count is wrong, or when any case differs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SEED=1
CASES=2000
OUT=""
BUILD=1

usage() {
  sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --seed)
      [[ $# -ge 2 ]] || { echo "--seed needs a value" >&2; exit 2; }
      SEED="$2"
      shift 2
      ;;
    --cases)
      [[ $# -ge 2 ]] || { echo "--cases needs a value" >&2; exit 2; }
      CASES="$2"
      shift 2
      ;;
    --out)
      [[ $# -ge 2 ]] || { echo "--out needs a value" >&2; exit 2; }
      OUT="$2"
      shift 2
      ;;
    --skip-build)
      BUILD=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! [[ "$SEED" =~ ^[0-9]+$ ]] || ! [[ "$CASES" =~ ^[1-9][0-9]*$ ]]; then
  echo "--seed must be a non-negative integer and --cases a positive integer" >&2
  exit 2
fi

OUT="${OUT:-$ROOT/java/tierwise-core/target/differential}"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
rm -f "$OUT/cases.jsonl"

echo "differential: seed=$SEED cases=$CASES out=$OUT"

# Step 1: the Java side writes the cases. The empty excludedGroups lets this one test run; the test itself is gated by
# the tierwise.differential.out property, so no other test is affected.
(
  cd "$ROOT/java"
  ./mvnw -B -ntp -pl tierwise-core \
    -Dtest=DifferentialHarnessTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtierwise.excludedGroups= \
    -Dtierwise.differential.out="$OUT" \
    -Dtierwise.differential.seed="$SEED" \
    -Dtierwise.differential.cases="$CASES" \
    test
)

LINES="$(wc -l < "$OUT/cases.jsonl")"
if [[ "$LINES" -ne "$CASES" ]]; then
  echo "expected $CASES cases in $OUT/cases.jsonl, found $LINES" >&2
  exit 1
fi

# Step 2: the JavaScript side replays the same cases.
if [[ "$BUILD" -eq 1 ]]; then
  (cd "$ROOT" && pnpm --dir js run build)
fi
(
  cd "$ROOT/js/evaluator"
  TIERWISE_DIFF_DIR="$OUT" node --test test/differential.test.mjs
)

echo "differential: $CASES cases replayed with no difference (seed $SEED)"
