#!/usr/bin/env bash
#
# Maintains CHANGELOG.md (Keep a Changelog 1.1.0 format).
#
# Usage: scripts/changelog.sh add <prev-tag|-> <ref> <version> <date>
#        scripts/changelog.sh sync
#        scripts/changelog.sh commit
#        scripts/changelog.sh show <version>
#
# "sync" adds to "## [Unreleased]" a bullet for each commit since the last tag
# that has none yet, and prints what it added. Each such bullet ends with a
# hidden marker (<!-- c:<hash of the subject> -->) so it still counts as that
# commit's after you reword it by hand; sync and add never repeat it. "commit"
# (make changelog) is sync followed by a commit of CHANGELOG.md alone, if it
# changed. "show" prints the bullets of a release as plain text.
#
# "add" writes the section "## [<version>] - <date>" right below
# "## [Unreleased]" and empties Unreleased. Its bullets are the ones under
# Unreleased (as you edited them, markers removed) plus the Conventional Commit
# subjects of <prev-tag>..<ref> ("-" as <prev-tag> means everything up to <ref>)
# that Unreleased does not have. It creates CHANGELOG.md if missing. Subjects
# map to categories:
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
SECTION_END_RE='^## \['
CONVENTIONAL='^([a-z]+)(\(([^)]*)\))?(!)?: (.+)$'

usage() {
  echo "Usage: $0 add <prev-tag|-> <ref> <version> <date>" >&2
  echo "       $0 sync" >&2
  echo "       $0 commit" >&2
  echo "       $0 show <version>" >&2
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

# Bullets of the section being built, one newline-terminated string per category.
declare -A entries=()
# Keys of the commits that already have a bullet (see key_of), and the bullets
# collect() added this run.
declare -A recorded=()
added=""

# A bullet recorded from a commit ends with the hidden marker <!-- c:<key> -->,
# so a bullet that was reworded by hand still counts as that commit's.
MARKER_RE=' <!-- c:([0-9a-f]+) -->'

key_of() { # <commit-subject>
  printf '%s' "$1" | sha1sum | cut -c1-7
}

strip_markers() {
  sed -E "s/$MARKER_RE//"
}

collect() { # <subject>: adds its bullet unless recorded; 1 when not user-facing
  local category scope line key
  [[ "$1" =~ $CONVENTIONAL ]] || return 1
  category="$(category_of "${BASH_REMATCH[1]}" "${BASH_REMATCH[4]}")"
  [[ -n "$category" ]] || return 1
  scope="${BASH_REMATCH[3]}"
  line="- ${scope:+$scope: }${BASH_REMATCH[5]}"
  key="$(key_of "$1")"
  if [[ -z "${recorded[$key]:-}" ]]; then
    recorded[$key]=1
    entries[$category]+="$line <!-- c:$key -->"$'\n'
    added+="$line"$'\n'
  fi
}

load_unreleased() { # fills entries from the bullets under "## [Unreleased]"
  local line category="" inside=0
  [[ -f "$CHANGELOG" ]] || return 0
  while IFS= read -r line; do
    if [[ "$line" == "## [Unreleased]" ]]; then
      inside=1
    elif (( ! inside )); then
      continue
    elif [[ "$line" =~ $SECTION_END_RE || "$line" =~ $LINK_RE ]]; then
      break
    elif [[ "$line" =~ ^###\ (.+)$ ]]; then
      category="${BASH_REMATCH[1]}"
    elif [[ -z "${line//[[:space:]]/}" || -z "$category" ]]; then
      continue
    else
      if [[ "$line" == "- "* ]]; then
        entries[$category]+="$line"$'\n'
      else # continuation of the previous bullet
        entries[$category]="${entries[$category]%$'\n'}"$'\n'"$line"$'\n'
      fi
      if [[ "$line" =~ $MARKER_RE ]]; then
        recorded[${BASH_REMATCH[1]}]=1
      fi
    fi
  done < "$CHANGELOG"
}

render_entries() {
  local category
  for category in "${CATEGORIES[@]}"; do
    if [[ -n "${entries[$category]:-}" ]]; then
      printf '\n### %s\n\n%s' "$category" "${entries[$category]}"
    fi
  done
}

collect_commits() { # <prev-tag|-> <ref>
  local range="$2" subject
  [[ "$1" == "-" ]] || range="$1..$2"
  while IFS= read -r subject; do
    collect "$subject" || true
  done < <(git log --reverse --no-merges --format=%s "$range" 2>/dev/null)
}

section() { # <prev-tag|-> <ref> <version> <date>
  local version="$3" date="$4"

  # the bullets already under Unreleased win, the commits they miss are added
  load_unreleased
  collect_commits "$1" "$2"

  if [[ ${#entries[@]} -eq 0 ]]; then
    entries[Changed]="- Maintenance and internal changes."$'\n'
  fi

  printf '## [%s] - %s\n' "$version" "$date"
  render_entries | strip_markers
}

# Replaces the body of "## [Unreleased]" in stdin by $INSERT (up to the next
# release heading or the compare links).
splice_unreleased() {
  INSERT="$1" END_RE="$SECTION_END_RE|$LINK_RE" awk '
    /^## \[Unreleased\]$/ && !done {
      print
      if (ENVIRON["INSERT"] != "") print ENVIRON["INSERT"]
      done = 1; skipping = 1; next
    }
    skipping && $0 ~ ENVIRON["END_RE"] { skipping = 0; print "" }
    skipping { next }
    { print }
  '
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
  create_if_missing
  local new_section
  new_section="$(section "$@")"

  local body
  body="$(grep -vE "$LINK_RE" "$CHANGELOG" | splice_unreleased $'\n'"$new_section")"

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

sync() { # adds the commits since the last tag that Unreleased does not have yet
  create_if_missing
  load_unreleased
  collect_commits "$(git describe --tags --abbrev=0 2>/dev/null || echo -)" HEAD

  if [[ -z "$added" ]]; then
    echo "Unreleased is already up to date."
    return
  fi
  local body
  body="$(splice_unreleased "$(render_entries)" < "$CHANGELOG")"
  printf '%s\n' "$body" > "$CHANGELOG"
  printf 'Added to Unreleased:\n%s' "$added"
}

commit() { # sync, then commit CHANGELOG.md on its own (other staged files stay staged)
  sync
  git add "$CHANGELOG"
  if git diff --cached --quiet -- "$CHANGELOG"; then
    return
  fi
  local -a body=()
  [[ -z "$added" ]] || body=(-m "$added")
  git commit -q -m "docs(changelog): update Unreleased entries" "${body[@]}" -- "$CHANGELOG"
  echo "Committed $CHANGELOG."
}

show() { # <version>: the bullets of a release as plain text
  VERSION="$1" LINK="$LINK_RE" awk '
    BEGIN { heading = "## [" ENVIRON["VERSION"] "]" }
    index($0, heading) == 1 { inside = 1; next }
    inside && (/^## \[/ || $0 ~ ENVIRON["LINK"]) { exit }
    !inside { next }
    /^### / { if (shown++) print ""; sub(/^### /, ""); print; next }
    /^- / { sub(/^- /, "  - "); print; next }
    NF { print "  " $0 }
  ' "$CHANGELOG" | strip_markers
}

[[ $# -ge 1 ]] || usage
command="$1"
shift
cd "$(git rev-parse --show-toplevel)"
case "$command" in
  add)
    [[ $# -eq 4 ]] || usage
    add "$@"
    ;;
  sync)
    [[ $# -eq 0 ]] || usage
    sync
    ;;
  commit)
    [[ $# -eq 0 ]] || usage
    commit
    ;;
  show)
    [[ $# -eq 1 ]] || usage
    show "$@"
    ;;
  *) usage ;;
esac
