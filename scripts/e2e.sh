#!/usr/bin/env bash
# End-to-end test (requirements section 12): compile -> publish -> Java and Node services -> promote -> roll back.
#
# Usage: scripts/e2e.sh [--skip-build] [--build-dir NAME] [--reactor-pom FILE] [-- MAVEN_ARGS...]
#
#   --skip-build        do not build the JavaScript packages first (js/node/dist must already exist for the Node part)
#   --build-dir NAME    Maven build directory of every module, passed as -Dtierwise.buildDir (default: target; a
#                       relative name is resolved per module, an absolute path is shared by all modules)
#   --reactor-pom FILE  the aggregator POM to build (default: java/pom.xml, which lists tierwise-e2e as a module)
#   -- MAVEN_ARGS...    everything after -- is passed to Maven unchanged
#
# 1. Builds the JavaScript packages (pnpm --dir js run build), so that js/node/dist exists.
# 2. Runs java/tierwise-e2e (EndToEndTest) and the modules it needs, through the Maven Wrapper. The tests of the other
#    modules are not run here; they have their own jobs. The test starts a server in-process on a random port, a Java
#    service (the Spring Boot starter) and a Node service (a child process), and prints the versions and timings.
# 3. Prints the report that the test wrote (<build dir>/e2e-report.json of tierwise-e2e).
#
# The Node part is skipped, with the reason on the console, when `node` is not on the PATH or js/node/dist is missing.
# The script publishes nothing: the server, its store and every token exist only for the test.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_JS=1
BUILD_DIR="target"
REACTOR_POM=""

usage() {
  sed -n "2,19p" "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      BUILD_JS=0
      shift
      ;;
    --build-dir)
      [[ $# -ge 2 ]] || { echo "--build-dir needs a value" >&2; exit 2; }
      BUILD_DIR="$2"
      shift 2
      ;;
    --reactor-pom)
      [[ $# -ge 2 ]] || { echo "--reactor-pom needs a value" >&2; exit 2; }
      REACTOR_POM="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    *)
      echo "unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

REACTOR_POM="${REACTOR_POM:-$ROOT/java/pom.xml}"
if [[ ! -f "$REACTOR_POM" ]]; then
  echo "e2e: no such POM: $REACTOR_POM" >&2
  exit 2
fi

if [[ "$BUILD_JS" -eq 1 ]]; then
  if command -v pnpm >/dev/null 2>&1; then
    (cd "$ROOT" && pnpm --dir js run build)
  else
    echo "e2e: pnpm is not on the PATH, so the JavaScript packages were not built." >&2
    echo "e2e: the Node part runs only if js/node/dist already exists." >&2
  fi
fi

# A relative build directory is resolved by Maven against each module; an absolute one is shared by every module.
if [[ "$BUILD_DIR" = /* ]]; then
  REPORT="$BUILD_DIR/e2e-report.json"
else
  REPORT="$ROOT/java/tierwise-e2e/$BUILD_DIR/e2e-report.json"
fi
rm -f "$REPORT"

# -Dtest limits the tests to this one in every module of the reactor; -am builds core, starter and server first.
(
  cd "$ROOT/java"
  ./mvnw -B -ntp -f "$REACTOR_POM" -pl :tierwise-e2e -am \
    -Dtierwise.buildDir="$BUILD_DIR" \
    -Dtest=EndToEndTest -Dsurefire.failIfNoSpecifiedTests=false \
    "$@" \
    verify
)

if [[ ! -f "$REPORT" ]]; then
  echo "e2e: the test wrote no report: $REPORT" >&2
  exit 1
fi
echo "e2e: report $REPORT"
cat "$REPORT"
