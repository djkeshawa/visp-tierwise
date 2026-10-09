#!/usr/bin/env bash
# Sets one version in every place that carries it, or checks that they all agree.
#
# Usage: scripts/set-version.sh <version>
#        scripts/set-version.sh --check [<version>]
#
#   <version>   a SemVer 2.0 version without a leading "v", for example 0.1.0 or 0.2.0-rc.1
#   --check     change nothing. Exit 1 unless every place carries the same version (and, when <version> is given,
#               that version). The release workflow runs it before it builds.
#
# Places that carry the version (the script lists each one it writes):
#   java/pom.xml                      the version of the parent POM
#   java/*/pom.xml                    the parent version of every module POM, including tierwise-e2e
#   js/package.json, js/*/package.json   the "version" of the workspace and of every package
#   java/tierwise-server/src/main/resources/openapi.yaml   info.version of the server API document
#   project-metadata.json             packageVersion
#
# The script edits files in place and never runs git. Review the result with `git diff`.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OPENAPI="java/tierwise-server/src/main/resources/openapi.yaml"
METADATA="project-metadata.json"

# SemVer 2.0.0 (semver.org), without build metadata: Maven and npm both read a "+" suffix differently.
SEMVER='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-((0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(\.(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?$'

usage() {
  sed -n '2,19p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

CHECK=0
VERSION=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --check) CHECK=1; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    *)
      [[ -z "$VERSION" ]] || { echo "only one version may be given" >&2; exit 2; }
      VERSION="$1"
      shift
      ;;
  esac
done

if [[ -n "$VERSION" ]] && ! [[ "$VERSION" =~ $SEMVER ]]; then
  echo "not a SemVer version without build metadata: $VERSION" >&2
  exit 2
fi
if [[ "$CHECK" -eq 0 && -z "$VERSION" ]]; then
  usage >&2
  exit 2
fi

# The places, as "kind:path" lines. kind says how the version is read and written.
places() {
  local file
  echo "pom:java/pom.xml"
  for file in "$ROOT"/java/*/pom.xml; do
    echo "pom:${file#"$ROOT"/}"
  done
  echo "json:js/package.json"
  for file in "$ROOT"/js/*/package.json; do
    echo "json:${file#"$ROOT"/}"
  done
  [[ -f "$ROOT/$OPENAPI" ]] && echo "openapi:$OPENAPI"
  [[ -f "$ROOT/$METADATA" ]] && echo "metadata:$METADATA"
  return 0
}

# Prints the version a file carries.
read_version() {
  local kind="$1" file="$ROOT/$2"
  case "$kind" in
    # The first <version> after the tierwise-parent artifactId: the project's own in java/pom.xml, the parent's in a module.
    pom) awk '/<artifactId>tierwise-parent<\/artifactId>/ { found = 1; next }
              found && /<version>[^<]*<\/version>/ { gsub(/.*<version>|<\/version>.*/, ""); print; exit }' "$file" ;;
    json) awk '/^  "version": "/ { gsub(/^  "version": "|",?$/, ""); print; exit }' "$file" ;;
    openapi) awk '/^info:/ { info = 1; next } info && /^  version: / { sub(/^  version: */, ""); print; exit }' "$file" ;;
    metadata) awk '/^  "packageVersion": "/ { gsub(/^  "packageVersion": "|",?$/, ""); print; exit }' "$file" ;;
  esac
}

# Rewrites the version in a file. Prints nothing; the caller reads the file back to confirm.
write_version() {
  local kind="$1" file="$ROOT/$2" new="$3" tmp
  tmp="$(mktemp "${TMPDIR:-/tmp}/set-version.XXXXXX")"
  case "$kind" in
    pom) awk -v v="$new" '/<artifactId>tierwise-parent<\/artifactId>/ { found = 1 }
                          found && !done && /<version>[^<]*<\/version>/ { sub(/<version>[^<]*<\/version>/, "<version>" v "</version>"); done = 1 }
                          { print }' "$file" > "$tmp" ;;
    json) awk -v v="$new" '!done && /^  "version": "/ { sub(/"version": "[^"]*"/, "\"version\": \"" v "\""); done = 1 } { print }' "$file" > "$tmp" ;;
    openapi) awk -v v="$new" '/^info:/ { info = 1 } info && !done && /^  version: / { sub(/version: .*/, "version: " v); done = 1 } { print }' "$file" > "$tmp" ;;
    metadata) awk -v v="$new" '!done && /^  "packageVersion": "/ { sub(/"packageVersion": "[^"]*"/, "\"packageVersion\": \"" v "\""); done = 1 } { print }' "$file" > "$tmp" ;;
  esac
  # cat keeps the mode and ownership of the original file, which mv would replace.
  cat "$tmp" > "$file"
  rm -f "$tmp"
}

status=0
first=""
while IFS=: read -r kind path; do
  [[ -n "$kind" ]] || continue
  current="$(read_version "$kind" "$path")"
  if [[ -z "$current" ]]; then
    echo "no version found in $path" >&2
    status=1
    continue
  fi
  if [[ "$CHECK" -eq 1 ]]; then
    [[ -n "$first" ]] || first="$current"
    expected="${VERSION:-$first}"
    if [[ "$current" != "$expected" ]]; then
      echo "MISMATCH: $path has $current, expected $expected" >&2
      status=1
    fi
  else
    write_version "$kind" "$path" "$VERSION"
    after="$(read_version "$kind" "$path")"
    if [[ "$after" != "$VERSION" ]]; then
      echo "could not set the version in $path (found '$after')" >&2
      status=1
    else
      echo "$path: $current -> $VERSION"
    fi
  fi
done < <(places)

if [[ "$CHECK" -eq 1 && "$status" -eq 0 ]]; then
  echo "All versions agree: ${VERSION:-$first}"
fi
exit "$status"
