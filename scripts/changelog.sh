#!/usr/bin/env bash
#
# Maintains CHANGELOG.md (Keep a Changelog 1.1.0 format).
#
# Usage: scripts/changelog.sh add <prev-tag|-> <ref> <version> <date>
#
# Adds the section "## [<version>] - <date>" right below "## [Unreleased]",
# built from the Conventional Commit subjects of <prev-tag>..<ref> ("-" as
# <prev-tag> means everything up to <ref>), creating CHANGELOG.md if missing:
#
#   feat -> Added          refactor, perf, any "!" breaking change -> Changed
#   fix  -> Fixed          revert -> Removed
#
# chore, docs, test, style and non-conventional subjects are left out. Compare
# links at the end of the file are derived from the origin remote (skipped when
# it is not a web URL). Called by scripts/release.sh; scripts/backfill-changelog.sh
# calls it once per existing tag.

set -euo pipefail

CHANGELOG="CHANGELOG.md"
CATEGORIES=(Added Changed Deprecated Removed Fixed Security)
LINK_RE='^\[[^]]+\]: '

usage() {
  echo "Usage: $0 add <prev-tag|-> <ref> <version> <date>" >&2
  exit 2
}

category_of() { # <type> <breaking-marker>
  if [[ -n "$2" ]]; then
    echo Changed
    return
  fi
  case "$1" in
    feat) echo Added ;;
    fix) echo Fixed ;;
    refactor | perf) echo Changed ;;
    revert) echo Removed ;;
  esac
}

section() { # <prev-tag|-> <ref> <version> <date>
  local prev="$1" ref="$2" version="$3" date="$4"
  local range="$ref"
  [[ "$prev" == "-" ]] || range="$prev..$ref"

  local -A entries=()
  local subject category scope
  local conventional='^([a-z]+)(\(([^)]*)\))?(!)?: (.+)$'
  while IFS= read -r subject; do
    [[ "$subject" =~ $conventional ]] || continue
    category="$(category_of "${BASH_REMATCH[1]}" "${BASH_REMATCH[4]}")"
    [[ -n "$category" ]] || continue
    scope="${BASH_REMATCH[3]}"
    entries[$category]+="- ${scope:+$scope: }${BASH_REMATCH[5]}"$'\n'
  done < <(git log --reverse --no-merges --format=%s "$range")

  if [[ ${#entries[@]} -eq 0 ]]; then
    entries[Changed]="- Maintenance and internal changes."$'\n'
  fi

  printf '## [%s] - %s\n' "$version" "$date"
  for category in "${CATEGORIES[@]}"; do
    if [[ -n "${entries[$category]:-}" ]]; then
      printf '\n### %s\n\n%s' "$category" "${entries[$category]}"
    fi
  done
}

web_base() { # https URL of the origin repository, empty if there is none
  local url
  url="$(git remote get-url origin 2>/dev/null || true)"
  url="${url%.git}"
  if [[ "$url" =~ ^git@([^:]+):(.+)$ ]]; then
    echo "https://${BASH_REMATCH[1]}/${BASH_REMATCH[2]}"
  elif [[ "$url" =~ ^(https?://)([^/@]*@)?(.+)$ ]]; then
    echo "${BASH_REMATCH[1]}${BASH_REMATCH[3]}"
  fi
}

create_if_missing() {
  [[ -f "$CHANGELOG" ]] && return
  cat > "$CHANGELOG" <<'EOF'
# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions are numbered by `scripts/release.sh`.

## [Unreleased]
EOF
}

add() { # <prev-tag|-> <ref> <version> <date>
  local prev="$1" version="$3"
  local new_section
  new_section="$(section "$@")"
  create_if_missing

  local body
  body="$(grep -vE "$LINK_RE" "$CHANGELOG" | SECTION="$new_section" awk '
    { print }
    /^## \[Unreleased\]$/ && !done { print ""; print ENVIRON["SECTION"]; done = 1 }
  ')"

  local -a links=()
  local base
  base="$(web_base)"
  if [[ -n "$base" ]]; then
    links+=("[Unreleased]: $base/compare/v$version...HEAD")
    if [[ "$prev" == "-" ]]; then
      links+=("[$version]: $base/releases/tag/v$version")
    else
      links+=("[$version]: $base/compare/$prev...v$version")
    fi
  fi
  local old
  while IFS= read -r old; do
    [[ -n "$old" ]] && links+=("$old")
  done < <(grep -E "$LINK_RE" "$CHANGELOG" | grep -vE '^\[Unreleased\]: ' || true)

  {
    printf '%s\n' "$body"
    if [[ ${#links[@]} -gt 0 ]]; then
      printf '\n'
      printf '%s\n' "${links[@]}"
    fi
  } > "$CHANGELOG"
}

[[ $# -ge 1 ]] || usage
command="$1"
shift
case "$command" in
  add)
    [[ $# -eq 4 ]] || usage
    cd "$(git rev-parse --show-toplevel)"
    add "$@"
    ;;
  *) usage ;;
esac
