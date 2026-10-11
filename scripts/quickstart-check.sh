#!/usr/bin/env bash
# Runs the two quickstarts (docs/quickstart-java.md and docs/quickstart-js.md) literally and fails on any
# deviation. It never publishes, pushes or signs anything and needs no secrets.
#
# How it works
#   1. It builds the artifacts the quickstarts install from a release, from this working tree:
#        - tierwise-core and tierwise-cli with ./mvnw install, into a temporary local Maven repository
#          (the fat JAR is java/tierwise-cli/<build dir>/tierwise-cli-<version>-all.jar);
#        - @visplabs/evaluator with pnpm, so that the quickstart can depend on it with a file: specifier.
#   2. It extracts the marked fenced blocks of each page (see "Markers") and runs them, in order, in one
#      shell in an empty temporary directory, with an empty HOME and an empty npm cache.
#   3. It compares the output of each block that is followed by an expected-output block, line by line, and
#      fails on the first difference. It prints the wall time of each quickstart.
#
# Markers. An HTML comment on the line before a fenced block says what the script does with the block:
#   <!-- qs:run -->            run the block in the shared shell (the working directory carries over)
#   <!-- qs:run fail -->       run the block in a subshell; it must exit with a non-zero status
#   <!-- qs:file PATH -->      write the block to PATH, relative to the working directory
#   <!-- qs:expect -->        the block is the exact output (standard output and error) of the previous run block
#   <!-- qs:skip REASON -->   the block is shown in the page and not run, for a reason (no release exists yet)
#
# What the script substitutes. These are the only differences between a reader's machine and this run, and
# each is stated in the pages:
#   - TIERWISE_REPO and TIERWISE_CLI_JAR are set to this working tree and its fat JAR, so that the "build
#     from source" path of each page runs. The release download blocks are skipped.
#   - MAVEN_ARGS points Maven at the temporary local repository, and "mvn" is a shim for the Maven
#     distribution that the Maven Wrapper downloads, so that a machine without Maven can run the check.
#   - When the working tree is not at version 0.1.0 (a development build), the literal "0.1.0" in blocks and
#     expected output is replaced by the version that was built.
#
# Usage
#   scripts/quickstart-check.sh [--java] [--js]      run both pages, or only the ones named
#
# Environment
#   TIERWISE_BUILD_DIR   Maven build directory for the artifacts, relative to each module (default target-quickstart).
#                        The script removes it again. Set it to something unique when other builds run in the tree.
#   KEEP_TMP=1           keep the temporary directory (and print its path) for inspection.
#   QS_MAX_SECONDS       the budget for one quickstart's steps, build excluded (default 300).
#
# Needs: bash, awk, sed, diff, java 21 or later (the reactor builds on 21), node 20 or later, npm and pnpm.

set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
BUILD_DIR=${TIERWISE_BUILD_DIR:-target-quickstart}
MAX_SECONDS=${QS_MAX_SECONDS:-300}
case "$BUILD_DIR" in
  "" | */* | .*) echo "quickstart-check: TIERWISE_BUILD_DIR must be a plain directory name, not '$BUILD_DIR'" >&2; exit 2 ;;
esac

want_java=0
want_js=0
for arg in "$@"; do
  case "$arg" in
    --java) want_java=1 ;;
    --js) want_js=1 ;;
    -h | --help) sed -n '2,/^$/p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "quickstart-check: unknown argument: $arg" >&2; exit 2 ;;
  esac
done
if [ "$want_java" = 0 ] && [ "$want_js" = 0 ]; then want_java=1; want_js=1; fi

die() { echo "quickstart-check: $*" >&2; exit 1; }
need() { command -v "$1" > /dev/null 2>&1 || die "$1 is required"; }

need awk; need sed; need diff; need java; need node; need npm
[ "$want_js" = 0 ] || need pnpm

TMP=$(mktemp -d "${TMPDIR:-/tmp}/tierwise-quickstart-check.XXXXXX")
cleanup() {
  status=$?
  # The build directories of this run. A normal "target" directory is never removed.
  if [ "$BUILD_DIR" != target ]; then rm -rf "$ROOT"/java/*/"$BUILD_DIR"; fi
  if [ "${KEEP_TMP:-0}" = 1 ]; then
    echo "quickstart-check: kept $TMP" >&2
  else
    rm -rf "$TMP"
  fi
  exit "$status"
}
trap cleanup EXIT

# The version of the working tree: the first project-level <version> of java/pom.xml.
VERSION=$(sed -n 's|^  <version>\(.*\)</version>$|\1|p' "$ROOT/java/pom.xml" | head -n 1)
[ -n "$VERSION" ] || die "cannot read the version from java/pom.xml"

now() {
  if [ -n "${EPOCHREALTIME:-}" ]; then echo "${EPOCHREALTIME/,/.}"; else date +%s; fi
}
elapsed() { awk -v a="$1" -v b="$2" 'BEGIN { printf "%.1f", b - a }'; }

# ---------------------------------------------------------------------------------------------------------
# 1. Build the artifacts that a reader gets from a release.

M2="$TMP/m2"
CLI_JAR=""
if [ "$want_java" = 1 ] || [ "$want_js" = 1 ]; then
  echo "== build: tierwise-core and tierwise-cli (version $VERSION) into a temporary Maven repository"
  build_start=$(now)
  (cd "$ROOT/java" && ./mvnw -B -ntp -q -DskipTests -Dtierwise.buildDir="$BUILD_DIR" -Dmaven.repo.local="$M2" \
    -pl tierwise-core,tierwise-cli -am install)
  CLI_JAR="$ROOT/java/tierwise-cli/$BUILD_DIR/tierwise-cli-$VERSION-all.jar"
  [ -f "$CLI_JAR" ] || die "the build did not write $CLI_JAR"
  # Keep a copy that does not depend on the build directory.
  mkdir -p "$TMP/artifacts"
  cp "$CLI_JAR" "$TMP/artifacts/tierwise-cli-$VERSION-all.jar"
  CLI_JAR="$TMP/artifacts/tierwise-cli-$VERSION-all.jar"
  echo "   built in $(elapsed "$build_start" "$(now)") s"
fi

if [ "$want_js" = 1 ]; then
  echo "== build: @visplabs/evaluator"
  build_start=$(now)
  pnpm --dir "$ROOT/js" install --frozen-lockfile --silent
  pnpm --dir "$ROOT/js" --filter @visplabs/evaluator run build > /dev/null
  [ -f "$ROOT/js/evaluator/dist/esm/index.js" ] || die "the evaluator build wrote no dist/esm/index.js"
  echo "   built in $(elapsed "$build_start" "$(now)") s"
fi

# A "mvn" for machines without Maven: the distribution that the Maven Wrapper downloads.
MAVEN_HOME_DIR=""
if [ "$want_java" = 1 ]; then
  MAVEN_HOME_DIR=$(cd "$ROOT/java" && ./mvnw -v | sed -n 's/^Maven home: //p')
  [ -x "$MAVEN_HOME_DIR/bin/mvn" ] || die "cannot find the Maven distribution of the Maven Wrapper"
  mkdir -p "$TMP/bin"
  printf '#!/bin/sh\nexec "%s/bin/mvn" "$@"\n' "$MAVEN_HOME_DIR" > "$TMP/bin/mvn"
  chmod +x "$TMP/bin/mvn"
fi

# ---------------------------------------------------------------------------------------------------------
# 2. Extract the marked blocks of a page into DIR: one NNN.blk file for each block, and steps.tsv with the
#    columns number, kind and argument.

extract() { # page dir
  awk -v dir="$2" '
    function fail(msg) { print "quickstart-check: " FILENAME ":" NR ": " msg > "/dev/stderr"; bad = 1; exit 1 }
    infence {
      if ($0 ~ /^```[ \t]*$/) { infence = 0; if (marked) close(file); next }
      if (marked) print $0 > file
      next
    }
    /^<!-- qs:[a-z]+( .*)? -->[ \t]*$/ {
      if (pending != "") fail("a marker follows another marker")
      line = $0
      sub(/^<!-- qs:/, "", line); sub(/ -->[ \t]*$/, "", line)
      kind = line; arg = ""
      if (index(line, " ") > 0) { kind = substr(line, 1, index(line, " ") - 1); arg = substr(line, index(line, " ") + 1) }
      if (kind != "run" && kind != "file" && kind != "expect" && kind != "skip") fail("unknown marker qs:" kind)
      pending = kind; pendingArg = arg; markerLine = NR
      next
    }
    /^```/ {
      infence = 1
      marked = (pending != "")
      if (marked) {
        n++
        file = sprintf("%s/%03d.blk", dir, n)
        printf "" > file
        printf "%03d\t%s\t%s\n", n, pending, pendingArg >> (dir "/steps.tsv")
        pending = ""
      }
      next
    }
    pending != "" && $0 !~ /^[ \t]*$/ { fail("the marker on line " markerLine " is not followed by a fenced block") }
    END { if (!bad && pending != "") fail("the marker on line " markerLine " is not followed by a fenced block") }
  ' "$1"
}

# ---------------------------------------------------------------------------------------------------------
# 3. Run one page.

substitute() { # file: replace the literal release version by the built version
  if [ "$VERSION" != "0.1.0" ]; then
    sed -i.bak "s/0\\.1\\.0/$VERSION/g" "$1" && rm -f "$1.bak"
  fi
}

normalise() { sed -e 's/[ \t]*$//' "$1"; }

run_page() { # page label
  local page=$1 label=$2
  local work="$TMP/$label"
  local blocks="$work/blocks" out="$work/out" home="$work/home" run="$work/run"
  mkdir -p "$blocks" "$out" "$home" "$run"
  : > "$blocks/steps.tsv"

  extract "$page" "$blocks" || die "cannot extract the blocks of $page"
  [ -s "$blocks/steps.tsv" ] || die "$page has no marked block"

  # Prepare the script that runs the page.
  local script="$work/steps.sh"
  {
    echo 'set -euo pipefail'
    echo 'STEP=000'
    echo "trap 'status=\$?; if [ \$status -ne 0 ]; then echo \"FAILED in step \$STEP\" >&2; [ ! -f \"$out/\$STEP.out\" ] || cat \"$out/\$STEP.out\" >&2; fi' EXIT"
  } > "$script"

  local expectations="$work/expectations.tsv"
  : > "$expectations"
  local number kind arg last_run="" runs=0 skipped=0 files=0
  while IFS=$'\t' read -r number kind arg; do
    substitute "$blocks/$number.blk"
    case "$kind" in
      run)
        runs=$((runs + 1))
        last_run=$number
        if [ "$arg" = fail ]; then
          {
            echo "STEP=$number"
            echo "if ( . \"$blocks/$number.blk\" ) > \"$out/$number.out\" 2>&1; then echo 'step $number was expected to fail and succeeded' >&2; exit 1; fi"
          } >> "$script"
        elif [ -z "$arg" ]; then
          {
            echo "STEP=$number"
            echo "{ . \"$blocks/$number.blk\"; } > \"$out/$number.out\" 2>&1"
          } >> "$script"
        else
          die "$page: unknown argument for qs:run: $arg"
        fi
        ;;
      file)
        files=$((files + 1))
        {
          echo "STEP=$number"
          echo "mkdir -p \"\$(dirname '$arg')\" && cp \"$blocks/$number.blk\" '$arg'"
        } >> "$script"
        ;;
      expect)
        [ -n "$last_run" ] || die "$page: an expected-output block comes before any run block"
        printf '%s\t%s\n' "$last_run" "$number" >> "$expectations"
        ;;
      skip)
        skipped=$((skipped + 1))
        ;;
    esac
  done < "$blocks/steps.tsv"

  echo "== $label: $runs run blocks, $files files, $skipped skipped, $(wc -l < "$expectations" | tr -d ' ') output checks"

  # Run the blocks in order, in one shell, with an empty HOME so that nothing on this machine is read.
  local start end
  start=$(now)
  (
    cd "$run"
    env -i \
      PATH="$TMP/bin:$PATH" \
      HOME="$home" \
      ${JAVA_HOME:+JAVA_HOME="$JAVA_HOME"} \
      ${HTTPS_PROXY:+HTTPS_PROXY="$HTTPS_PROXY"} \
      ${HTTP_PROXY:+HTTP_PROXY="$HTTP_PROXY"} \
      ${NO_PROXY:+NO_PROXY="$NO_PROXY"} \
      ${https_proxy:+https_proxy="$https_proxy"} \
      ${http_proxy:+http_proxy="$http_proxy"} \
      ${no_proxy:+no_proxy="$no_proxy"} \
      TMPDIR="$work" \
      LANG="${LANG:-C.UTF-8}" \
      MAVEN_ARGS="-B -ntp -Dmaven.repo.local=$M2" \
      npm_config_cache="$home/npm-cache" \
      npm_config_update_notifier=false \
      npm_config_fund=false \
      npm_config_audit=false \
      TIERWISE_REPO="$ROOT" \
      TIERWISE_CLI_JAR="$CLI_JAR" \
      bash "$script"
  ) || die "$label: a step failed (see above)"
  end=$(now)
  local seconds
  seconds=$(elapsed "$start" "$end")

  # Compare the outputs with the expected-output blocks.
  local checked=0 run_number expect_number
  while IFS=$'\t' read -r run_number expect_number; do
    if ! diff -u --label "expected ($page, block $expect_number)" --label "actual (block $run_number)" \
      <(normalise "$blocks/$expect_number.blk") <(normalise "$out/$run_number.out"); then
      die "$label: the output of block $run_number differs from the expected output"
    fi
    checked=$((checked + 1))
  done < "$expectations"

  echo "   $label: $checked outputs match, $seconds s"
  RESULT_LINES+=("$label	$runs	$checked	$seconds")
  awk -v s="$seconds" -v max="$MAX_SECONDS" 'BEGIN { exit (s + 0 > max + 0) ? 1 : 0 }' \
    || die "$label: took $seconds s, over the budget of $MAX_SECONDS s"
}

RESULT_LINES=()
[ "$want_java" = 0 ] || run_page "$ROOT/docs/quickstart-java.md" java
[ "$want_js" = 0 ] || run_page "$ROOT/docs/quickstart-js.md" js

echo
echo "Quickstart check passed (built version $VERSION)."
printf '%-6s %-12s %-14s %s\n' quickstart "run blocks" "outputs match" "seconds"
for line in "${RESULT_LINES[@]}"; do
  IFS=$'\t' read -r l r c s <<< "$line"
  printf '%-6s %-12s %-14s %s\n' "$l" "$r" "$c" "$s"
done
