#!/usr/bin/env bash
# PreToolUse hook (Edit|Write): guards the Gradle build files.
#  - versionCode / versionName are bumped only by scripts/release.sh, never by hand.
#  - dependencies go through the version catalog (gradle/libs.versions.toml), never as
#    "group:name:version" literals in a *.gradle.kts file.
# Exit code 2 blocks the tool call and shows stderr to Claude.

input="$(cat)"
field() { jq -r ".tool_input.$1 // \"\"" <<< "$input"; }

file="$(field file_path)"
[[ "$file" == *.gradle.kts ]] || exit 0

old="$(field old_string)"
new="$(field new_string)$(field content)"

if grep -qE '(^|[^A-Za-z])(implementation|api|ksp|kapt|compileOnly|runtimeOnly|[a-z]+Implementation|[a-z]+Api)\([[:space:]]*(platform\([[:space:]]*)?"' <<< "$new"; then
  echo "Blocked: declare the library in gradle/libs.versions.toml and reference it as libs.* in the build file, not as a \"group:name:version\" literal." >&2
  exit 2
fi

if [[ "$file" == *build.gradle.kts ]]; then
  version='version(Code|Name)[[:space:]]*=[[:space:]]*"?[^"[:space:]]+"?'
  changed=false
  if [[ -n "$old" ]] && grep -qE "version(Code|Name)[[:space:]]*=" <<< "$old$new"; then
    changed=true
  elif [[ -z "$old" && -f "$file" && "$(jq -r '.tool_name' <<< "$input")" == "Write" ]]; then
    [[ "$(grep -oE "$version" "$file")" == "$(grep -oE "$version" <<< "$new")" ]] || changed=true
  fi
  if $changed; then
    echo "Blocked: versionCode/versionName are bumped only by scripts/release.sh (run by the user). Do not edit them." >&2
    exit 2
  fi
fi
