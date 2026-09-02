#!/usr/bin/env bash
#
# Release script: bumps the patch version, commits, tags, builds a signed
# release APK, and (after confirmation) pushes branch + tag to origin.
#
# Signing credentials must be provided via environment variables:
#   ANDROID_KEYSTORE_PATH
#   ANDROID_KEYSTORE_PASSWORD
#   ANDROID_KEY_ALIAS
#   ANDROID_KEY_PASSWORD
#
# Usage: scripts/release.sh

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

GRADLE_FILE="app/build.gradle.kts"
RELEASES_DIR="releases"

# --- Preconditions -----------------------------------------------------

for var in ANDROID_KEYSTORE_PATH ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [[ -z "${!var:-}" ]]; then
    echo "Error: $var is not set. Export all signing env vars before running this script." >&2
    exit 1
  fi
done

if [[ ! -f "$ANDROID_KEYSTORE_PATH" ]]; then
  echo "Error: keystore not found at ANDROID_KEYSTORE_PATH=$ANDROID_KEYSTORE_PATH" >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Error: working tree is not clean. Commit or stash changes before releasing." >&2
  git status --short
  exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"

# --- Compute new version ------------------------------------------------

CURRENT_VERSION_NAME="$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE_FILE")"
CURRENT_VERSION_CODE="$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE_FILE")"

IFS='.' read -ra VERSION_PARTS <<< "$CURRENT_VERSION_NAME"
LAST_IDX=$((${#VERSION_PARTS[@]} - 1))
VERSION_PARTS[$LAST_IDX]=$((${VERSION_PARTS[$LAST_IDX]} + 1))
NEW_VERSION_NAME="$(IFS=.; echo "${VERSION_PARTS[*]}")"
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

echo "Bumping version: $CURRENT_VERSION_NAME ($CURRENT_VERSION_CODE) -> $NEW_VERSION_NAME ($NEW_VERSION_CODE)"

# --- Apply version bump --------------------------------------------------

sed -i -E "s/(versionCode = )$CURRENT_VERSION_CODE/\1$NEW_VERSION_CODE/" "$GRADLE_FILE"
sed -i -E "s/(versionName = )\"$CURRENT_VERSION_NAME\"/\1\"$NEW_VERSION_NAME\"/" "$GRADLE_FILE"

# --- Build and test -------------------------------------------------------

echo "Running build and unit tests..."
./gradlew assembleDebug testDebugUnitTest

# --- Commit and tag --------------------------------------------------------

git add "$GRADLE_FILE"
git commit -m "chore: bump version to $NEW_VERSION_NAME ($NEW_VERSION_CODE)"
git tag -a "v$NEW_VERSION_NAME" -m "Release $NEW_VERSION_NAME"

echo "Created commit and tag v$NEW_VERSION_NAME."

# --- Build signed release APK ---------------------------------------------

echo "Building signed release APK..."
./gradlew assembleRelease

BUILT_APK="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$BUILT_APK" ]]; then
  echo "Error: expected signed APK not found at $BUILT_APK" >&2
  echo "Check that assembleRelease actually applied the signing config." >&2
  exit 1
fi

mkdir -p "$RELEASES_DIR"
OUTPUT_APK="$RELEASES_DIR/accounting-$NEW_VERSION_NAME.apk"
cp "$BUILT_APK" "$OUTPUT_APK"

echo "Signed APK: $OUTPUT_APK"

# --- Confirm before pushing ------------------------------------------------

read -r -p "Push branch '$CURRENT_BRANCH' and tag 'v$NEW_VERSION_NAME' to origin? [y/N] " CONFIRM
if [[ "$CONFIRM" =~ ^[Yy]$ ]]; then
  git push origin "$CURRENT_BRANCH"
  git push origin "v$NEW_VERSION_NAME"
  echo "Pushed branch and tag to origin."
else
  echo "Skipped push. Run manually when ready:"
  echo "  git push origin $CURRENT_BRANCH"
  echo "  git push origin v$NEW_VERSION_NAME"
fi

echo "Release $NEW_VERSION_NAME complete."
