#!/usr/bin/env bash
# PreToolUse hook (Bash): keeps Claude Code from bypassing the git hooks in .githooks
# (commit-msg), either with `git commit --no-verify` / `-n` or by changing core.hooksPath.
# Exit code 2 blocks the tool call and shows stderr to Claude.

command="$(jq -r '.tool_input.command // ""')"

if grep -qE 'core\.hooksPath' <<< "$command"; then
  echo "Blocked: core.hooksPath must stay .githooks, it enables the commit-msg hook (Conventional Commits). If a commit is rejected, fix the message instead." >&2
  exit 2
fi

if grep -qE 'git[^|;&]*[[:space:]]commit[^|;&]*([[:space:]]--no-verify|[[:space:]]-[a-zA-Z]*n[a-zA-Z]*([[:space:]]|$))' <<< "$command"; then
  echo "Blocked: git commit --no-verify skips the commit-msg hook (Conventional Commits, no Co-Authored-By). Fix the commit message instead." >&2
  exit 2
fi
