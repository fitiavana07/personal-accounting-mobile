#!/usr/bin/env bash
# PreToolUse hook: keeps Claude Code from reading local.properties (it holds the
# release keystore passwords). Blocks Read, Grep and Bash calls that mention it.
# Exit code 2 blocks the tool call and shows stderr to Claude.

target="$(jq -r '[.tool_input.file_path, .tool_input.path, .tool_input.glob, .tool_input.command] | map(select(. != null)) | join("\n")')"

if grep -qE '(^|[^A-Za-z0-9_-])local\.properties' <<< "$target"; then
  echo "Blocked: local.properties holds release signing secrets and must not be read. Ask the user if you need a value from it." >&2
  exit 2
fi
