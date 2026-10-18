#!/usr/bin/env bash
#
# ONE-OFF: seeds CHANGELOG.md with one section per existing release tag
# (v*, in version order, dated with the tag date), newest first, by calling
# scripts/changelog.sh once per tag. Commits after the last tag are not
# included: the next scripts/release.sh run picks them up.
#
# Refuses to run if CHANGELOG.md already exists, the working tree is not clean,
# or there is no tag. It does not commit: review CHANGELOG.md, then commit it.
#
# Usage: scripts/backfill-changelog.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$(git rev-parse --show-toplevel)"

if [[ -e CHANGELOG.md ]]; then
  echo "Error: CHANGELOG.md already exists; this script only seeds a new one." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Error: working tree is not clean. Commit or stash changes first." >&2
  exit 1
fi

mapfile -t tags < <(git tag --list 'v*' | sort -V)
if [[ ${#tags[@]} -eq 0 ]]; then
  echo "Error: no v* tag found, nothing to backfill." >&2
  exit 1
fi

prev="-"
for tag in "${tags[@]}"; do
  date="$(git for-each-ref "refs/tags/$tag" --format='%(creatordate:short)')"
  "$SCRIPT_DIR/changelog.sh" add "$prev" "$tag" "${tag#v}" "$date"
  prev="$tag"
done

echo "Wrote CHANGELOG.md with ${#tags[@]} releases. Review it, then commit it."
