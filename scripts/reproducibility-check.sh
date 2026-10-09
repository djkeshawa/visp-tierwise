#!/usr/bin/env bash
# Reproducibility check (requirements section 1.2, ADR 0022).
#
# Usage: scripts/reproducibility-check.sh [--worktree] [--work DIR] [--keep] [--skip-java] [--skip-js]
#
#   --worktree   copy the working tree (without .git, node_modules, build output) instead of cloning HEAD. Use it
#                to check uncommitted changes. The release workflow uses the default, a clean clone.
#   --work DIR   build under DIR (it must not exist or must be empty) instead of a temporary directory
#   --keep       keep the work directory when the check passes (it is always kept when it fails)
#   --skip-java  do not build or compare the Java artifacts
#   --skip-js    do not build or compare the JavaScript packages
#
# What it does
# 1. Puts two independent copies of the sources in two directories with names of different lengths
#    (<work>/a/tierwise and <work>/b/other-name/tierwise-copy), so a path baked into an artifact shows up.
# 2. Builds the Java reactor in each with `./mvnw -Prelease -DskipTests package`: the JARs, sources JARs, Javadoc JARs,
#    the fat CLI JAR, the executable server JAR and the CycloneDX SBOMs. Nothing is signed or deployed.
# 3. Installs, builds and packs the JavaScript packages in each (`pnpm pack`), and compiles the sample projects with
#    each CLI JAR into bundles.
# 4. Compares the SHA-256 of every file. A difference prints a listing diff of the two archives or files and the
#    script exits non-zero.
#
# Needs: JDK 21 (JAVA_HOME or java on PATH), Node 22+, pnpm, tar, unzip, sha256sum (or shasum), and git unless
# --worktree is given. The Maven and pnpm caches are shared by the two builds; that is safe, because they hold
# inputs, not outputs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
USE_WORKTREE=0
KEEP=0
DO_JAVA=1
DO_JS=1
WORK=""
SAMPLES="commerce retail-pos saas-entitlements"

usage() {
  sed -n '2,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --worktree) USE_WORKTREE=1; shift ;;
    --keep) KEEP=1; shift ;;
    --skip-java) DO_JAVA=0; shift ;;
    --skip-js) DO_JS=0; shift ;;
    --work)
      [[ $# -ge 2 ]] || { echo "--work needs a value" >&2; exit 2; }
      WORK="$2"
      shift 2
      ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if command -v sha256sum >/dev/null 2>&1; then
  sha256() { sha256sum "$@"; }
else
  sha256() { shasum -a 256 "$@"; }
fi

if [[ -z "$WORK" ]]; then
  WORK="$(mktemp -d "${TMPDIR:-/tmp}/tierwise-repro.XXXXXX")"
else
  mkdir -p "$WORK"
  [[ -z "$(ls -A "$WORK")" ]] || { echo "--work directory is not empty: $WORK" >&2; exit 2; }
  WORK="$(cd "$WORK" && pwd)"
fi
COPY_A="$WORK/a/tierwise"
COPY_B="$WORK/b/other-name/tierwise-copy"
FAILED=0

log() { printf '\n== %s\n' "$*"; }

populate() {
  local target="$1"
  mkdir -p "$target"
  if [[ "$USE_WORKTREE" -eq 1 ]]; then
    # The working tree without VCS data, installed packages and build output. Source and docs are copied as they are.
    (cd "$ROOT" && tar -cf - \
      --exclude='./.git' \
      --exclude='node_modules' \
      --exclude='./java/*/target' --exclude='./java/*/target-*' --exclude='./java/target' --exclude='./java/target-*' \
      --exclude='./js/*/dist' \
      .) | (cd "$target" && tar -xf -)
  else
    git clone --quiet --no-hardlinks "$ROOT" "$target"
  fi
}

# Writes "<sha256>  <path relative to the copy>" for every file matching the finds, sorted by path.
manifest() {
  local copy="$1"; shift
  (cd "$copy" && find "$@" -type f 2>/dev/null | LC_ALL=C sort | while IFS= read -r file; do sha256 "$file"; done)
}

# Compares two manifests and explains each difference.
compare() {
  local label="$1" manifest_a="$2" manifest_b="$3" count
  if [[ ! -s "$manifest_a" ]]; then
    echo "FAIL: $label: no files found in the first build"
    FAILED=1
    return
  fi
  count="$(wc -l < "$manifest_a" | tr -d ' ')"
  if diff -q "$manifest_a" "$manifest_b" >/dev/null; then
    echo "OK: $label: $count files are byte-identical"
    return
  fi
  FAILED=1
  echo "FAIL: $label: the two builds differ"
  diff "$manifest_a" "$manifest_b" || true
  # Name each differing file and show what differs inside it.
  local path
  while IFS= read -r path; do
    [[ -n "$path" ]] || continue
    explain "$path"
  done < <({
    awk 'NR==FNR { h[$2] = $1; next } h[$2] != $1 { print $2 }' "$manifest_a" "$manifest_b"
    awk 'NR==FNR { h[$2] = 1; next } !($2 in h) { print $2 }' "$manifest_b" "$manifest_a"
  } | LC_ALL=C sort -u)
}

explain() {
  local path="$1" a="$COPY_A/$1" b="$COPY_B/$1"
  echo "--- difference in $path"
  if [[ ! -f "$b" ]]; then echo "(missing in the second build)"; return; fi
  case "$path" in
    *.jar|*.zip)
      diff <(unzip -lv "$a" | sed '1d; s/^ *//') <(unzip -lv "$b" | sed '1d; s/^ *//') | head -40 || true
      ;;
    *.tgz)
      diff <(tar -tvzf "$a") <(tar -tvzf "$b") | head -40 || true
      ;;
    *)
      diff "$a" "$b" | head -40 || true
      ;;
  esac
}

# Builds the Java reactor in one copy.
build_java() {
  local copy="$1"
  (cd "$copy/java" && chmod 0755 ./mvnw && ./mvnw -B -ntp -q -Prelease -DskipTests clean package)
}

build_js() {
  local copy="$1"
  pnpm --dir "$copy/js" install --frozen-lockfile --silent
  pnpm --dir "$copy/js" run build >/dev/null
  mkdir -p "$copy/pack"
  local package
  for package in "$copy"/js/*/package.json; do
    # Only packages with a "files" list are packages that would be published; spec-tools is a private checker.
    if node -e 'process.exit(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).files ? 0 : 1)' "$package"; then
      # Each tarball carries the license texts (the package.json "files" list names them). They live once, at the root.
      cp "$copy/LICENSE" "$copy/NOTICE" "$(dirname "$package")/"
      pnpm --dir "$(dirname "$package")" pack --pack-destination "$copy/pack" >/dev/null
    fi
  done
}

compile_samples() {
  local copy="$1" jar sample
  mkdir -p "$copy/bundles"
  jar="$(ls "$copy"/java/tierwise-cli/target/tierwise-cli-*-all.jar)"
  for sample in $SAMPLES; do
    (cd "$copy" && java -jar "$jar" compile "samples/$sample" --output "$copy/bundles/$sample-bundle.json" >/dev/null)
  done
}

log "Work directory: $WORK"
if [[ "$USE_WORKTREE" -eq 1 ]]; then
  echo "Mode: copy of the working tree (--worktree). Not a clean clone."
else
  echo "Mode: clean clone of HEAD."
fi
populate "$COPY_A"
populate "$COPY_B"

for copy in "$COPY_A" "$COPY_B"; do
  if [[ "$DO_JAVA" -eq 1 ]]; then
    log "Java build in $copy"
    build_java "$copy"
  fi
  if [[ "$DO_JS" -eq 1 ]]; then
    log "JavaScript build and pack in $copy"
    build_js "$copy"
  fi
  if [[ "$DO_JAVA" -eq 1 ]]; then
    log "Sample bundles in $copy"
    compile_samples "$copy"
  fi
done

log "Comparison"
if [[ "$DO_JAVA" -eq 1 ]]; then
  # The artifacts Maven would deploy: every JAR, and the SBOM of every module.
  manifest "$COPY_A" java -path '*/target/*' \( -name '*.jar' -o -name 'bom.json' \) > "$WORK/java-a.txt"
  manifest "$COPY_B" java -path '*/target/*' \( -name '*.jar' -o -name 'bom.json' \) > "$WORK/java-b.txt"
  compare "Java JARs and SBOMs" "$WORK/java-a.txt" "$WORK/java-b.txt"
  manifest "$COPY_A" bundles > "$WORK/bundles-a.txt"
  manifest "$COPY_B" bundles > "$WORK/bundles-b.txt"
  compare "sample bundles" "$WORK/bundles-a.txt" "$WORK/bundles-b.txt"
fi
if [[ "$DO_JS" -eq 1 ]]; then
  manifest "$COPY_A" pack > "$WORK/js-a.txt"
  manifest "$COPY_B" pack > "$WORK/js-b.txt"
  compare "npm pack tarballs" "$WORK/js-a.txt" "$WORK/js-b.txt"
fi

if [[ "$FAILED" -ne 0 ]]; then
  echo
  echo "NOT REPRODUCIBLE. The builds are kept in $WORK"
  exit 1
fi
echo
echo "Reproducible: both builds are byte-identical."
if [[ "$KEEP" -eq 1 ]]; then
  echo "Builds kept in $WORK"
else
  rm -rf "$WORK"
fi
