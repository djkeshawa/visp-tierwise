#!/usr/bin/env bash
# S3 integration tests (ADR 0023): runs the opt-in *IntegrationTest classes of tierwise-server-s3 and
# tierwise-spring-boot-starter-s3 against a local MinIO, so the conditional requests (If-None-Match: * and If-Match
# on PutObject, If-None-Match on GetObject) are checked against a real S3-compatible server and not only the fakes.
#
# Usage: scripts/s3-it.sh [--build-dir NAME] [-- MAVEN_ARGS...]
#
#   --build-dir NAME    Maven build directory of every module, passed as -Dtierwise.buildDir (default: target)
#   -- MAVEN_ARGS...    everything after -- is passed to Maven unchanged
#
# Environment:
#   S3_IT_MODE          binary | docker; default: docker when MINIO_IMAGE is set, otherwise binary
#   MINIO_BIN           the MinIO server binary (binary mode)
#   MINIO_IMAGE         the container image (docker mode); must be pinned by digest; there is no default (ADR 0027)
#   MINIO_PORT          the local port (default 19000)
#
# 1. Starts MinIO on 127.0.0.1:$MINIO_PORT with throwaway root credentials and an empty temporary data directory.
# 2. Exports TIERWISE_S3_IT_ENDPOINT, TIERWISE_S3_IT_BUCKET, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY and AWS_REGION.
#    The tests create the bucket themselves (create-if-absent), so the MinIO client `mc` is not needed.
# 3. Runs Maven with -Dtest='*IntegrationTest', then checks that both classes really ran (no class was skipped).
# 4. Stops MinIO and deletes its data, also on failure or interrupt.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="target"
MAVEN_ARGS=()

# MinIO's own Docker Hub image and dl.min.io downloads are gone (the open-source server was archived upstream), so the
# default is the official server binary from MinIO's GitHub release, pinned by SHA-256 (ADR 0027; CI downloads it).
# Docker mode runs whatever image MINIO_IMAGE names, pinned by digest; no third-party image is chosen here.

usage() {
  sed -n "2,21p" "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build-dir)
      [[ $# -ge 2 ]] || { echo "--build-dir needs a value" >&2; exit 2; }
      BUILD_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      MAVEN_ARGS=("$@")
      break
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

PORT="${MINIO_PORT:-19000}"
ENDPOINT="http://127.0.0.1:${PORT}"
ACCESS_KEY="tierwise-it"
SECRET_KEY="tierwise-it-$(date +%s)-$$"
BUCKET="tierwise-it"

MODE="${S3_IT_MODE:-}"
if [[ -z "$MODE" ]]; then
  if [[ -n "${MINIO_IMAGE:-}" ]]; then
    MODE="docker"
  else
    MODE="binary"
  fi
fi

if curl -fsS -o /dev/null "${ENDPOINT}/minio/health/live" 2>/dev/null; then
  echo "Something already answers on ${ENDPOINT}. Set MINIO_PORT to a free port." >&2
  exit 1
fi

DATA_DIR=""
MINIO_PID=""
CONTAINER=""

cleanup() {
  if [[ -n "$MINIO_PID" ]]; then
    kill "$MINIO_PID" 2>/dev/null || true
    wait "$MINIO_PID" 2>/dev/null || true
  fi
  if [[ -n "$CONTAINER" ]]; then
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  fi
  if [[ -n "$DATA_DIR" ]]; then
    rm -rf "$DATA_DIR"
  fi
}
trap cleanup EXIT
trap 'exit 130' INT TERM

case "$MODE" in
  docker)
    IMAGE="${MINIO_IMAGE:-}"
    if [[ "$IMAGE" != *@sha256:* ]]; then
      echo "MINIO_IMAGE must be pinned by digest (name@sha256:...): $IMAGE" >&2
      exit 2
    fi
    CONTAINER="tierwise-s3-it-$$"
    echo "Starting MinIO in docker: $IMAGE"
    docker run -d --rm --name "$CONTAINER" -p "127.0.0.1:${PORT}:9000" \
      -e MINIO_ROOT_USER="$ACCESS_KEY" -e MINIO_ROOT_PASSWORD="$SECRET_KEY" "$IMAGE" >/dev/null
    ;;
  binary)
    if [[ -z "${MINIO_BIN:-}" || ! -x "${MINIO_BIN}" ]]; then
      echo "MINIO_BIN does not name an executable MinIO server binary (or set MINIO_IMAGE for docker mode)." >&2
      exit 2
    fi
    DATA_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tierwise-s3-it.XXXXXX")"
    echo "Starting MinIO from $MINIO_BIN ($("$MINIO_BIN" --version | head -n 1))"
    MINIO_ROOT_USER="$ACCESS_KEY" MINIO_ROOT_PASSWORD="$SECRET_KEY" MINIO_BROWSER=off \
      "$MINIO_BIN" server --quiet --address "127.0.0.1:${PORT}" "$DATA_DIR/data" >"$DATA_DIR/minio.log" 2>&1 &
    MINIO_PID=$!
    ;;
  *)
    echo "S3_IT_MODE must be docker or binary: $MODE" >&2
    exit 2
    ;;
esac

for _ in $(seq 1 60); do
  if curl -fsS -o /dev/null "${ENDPOINT}/minio/health/live" 2>/dev/null; then
    break
  fi
  if [[ -n "$MINIO_PID" ]] && ! kill -0 "$MINIO_PID" 2>/dev/null; then
    echo "MinIO exited during startup:" >&2
    cat "$DATA_DIR/minio.log" >&2 || true
    exit 1
  fi
  sleep 0.5
done
curl -fsS -o /dev/null "${ENDPOINT}/minio/health/live" || { echo "MinIO did not become ready at ${ENDPOINT}" >&2; exit 1; }
echo "MinIO is ready at ${ENDPOINT}"

export TIERWISE_S3_IT_ENDPOINT="$ENDPOINT"
export TIERWISE_S3_IT_BUCKET="$BUCKET"
export AWS_ACCESS_KEY_ID="$ACCESS_KEY"
export AWS_SECRET_ACCESS_KEY="$SECRET_KEY"
export AWS_REGION="us-east-1"
# Keep a developer's profile or session token from leaking into the test client.
unset AWS_SESSION_TOKEN AWS_PROFILE

cd "$ROOT/java"
./mvnw -B -ntp verify -pl tierwise-server-s3,tierwise-spring-boot-starter-s3 -am \
  -Dtest='*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false \
  "-Dtierwise.buildDir=${BUILD_DIR}" ${MAVEN_ARGS[@]+"${MAVEN_ARGS[@]}"}

# A class that the environment condition disabled would leave Maven green, so check that both really ran.
status=0
for report in \
  "tierwise-server-s3:io.github.visplabs.tierwise.server.s3.S3BundleStoreIntegrationTest" \
  "tierwise-spring-boot-starter-s3:io.github.visplabs.tierwise.spring.s3.S3BundleSourceIntegrationTest"; do
  module="${report%%:*}"
  class="${report#*:}"
  if [[ "$BUILD_DIR" = /* ]]; then
    dir="$BUILD_DIR"
  else
    dir="$ROOT/java/$module/$BUILD_DIR"
  fi
  file="$dir/surefire-reports/TEST-${class}.xml"
  if [[ ! -f "$file" ]]; then
    echo "No report for ${class} (${file})" >&2
    status=1
    continue
  fi
  header="$(grep -m 1 -o '<testsuite [^>]*>' "$file")"
  tests="$(sed -n 's/.* tests="\([0-9]*\)".*/\1/p' <<<"$header")"
  skipped="$(sed -n 's/.* skipped="\([0-9]*\)".*/\1/p' <<<"$header")"
  echo "${class}: ${tests} tests, ${skipped} skipped"
  if [[ -z "$tests" || "$tests" == "0" || "$skipped" != "0" ]]; then
    echo "${class} did not run against MinIO" >&2
    status=1
  fi
done
exit "$status"
