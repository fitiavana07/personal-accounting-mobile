#!/usr/bin/env bash
# Stop hook: when Claude is about to finish a turn and source or build files changed since the
# last successful check, runs the debug build and unit tests. On failure it exits with code 2,
# which keeps Claude working and shows it the failure output.

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# Already asked Claude to fix a failure this turn: do not loop forever, let the turn end.
[[ "$(jq -r '.stop_hook_active // false')" == "true" ]] && exit 0

stamp=.claude/.last-successful-check
inputs=(app/src app/build.gradle.kts gradle/libs.versions.toml build.gradle.kts settings.gradle.kts)

if [[ -f "$stamp" ]] && [[ -z "$(find "${inputs[@]}" -type f -newer "$stamp" -print -quit 2>/dev/null)" ]]; then
  exit 0
fi

started="$(mktemp)"
if output="$(./gradlew --console=plain assembleDebug testDebugUnitTest 2>&1)"; then
  mv "$started" "$stamp"
  exit 0
fi
rm -f "$started"

{
  echo "assembleDebug testDebugUnitTest failed. Fix the failure before finishing:"
  tail -n 40 <<< "$output"
} >&2
exit 2
