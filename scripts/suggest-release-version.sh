#!/usr/bin/env bash
# Suggest semver bump for a main publish from commits in main..dev.
# Usage (repo root): ./scripts/suggest-release-version.sh
# Does not edit files; agent still needs user confirmation (rule 41).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

GRADLE="app/build.gradle.kts"
if [[ ! -f "$GRADLE" ]]; then
  echo "error: missing $GRADLE" >&2
  exit 1
fi

current_name="$(sed -n 's/^val ncarouselBaseVersionName = "\([^"]*\)".*/\1/p' "$GRADLE" | head -n1)"
current_code="$(sed -n 's/^val ncarouselLocalVersionCode = \([0-9][0-9]*\).*/\1/p' "$GRADLE" | head -n1)"

if [[ -z "$current_name" || -z "$current_code" ]]; then
  echo "error: could not parse version from $GRADLE" >&2
  exit 1
fi

IFS=. read -r major minor patch <<<"$current_name"
major="${major:-0}"
minor="${minor:-0}"
patch="${patch:-0}"
next_code=$((current_code + 1))

range="main..dev"
if ! git rev-parse --verify main >/dev/null 2>&1; then
  echo "error: branch main not found" >&2
  exit 1
fi
if ! git rev-parse --verify dev >/dev/null 2>&1; then
  echo "error: branch dev not found" >&2
  exit 1
fi

log="$(git log "$range" --pretty=format:'%s%n%b%n---' 2>/dev/null || true)"
oneline="$(git log "$range" --oneline 2>/dev/null || true)"

if [[ -z "$oneline" ]]; then
  echo "current: $current_name ($current_code)"
  echo "commits: (none in $range)"
  echo "suggest: patch → ${major}.${minor}.$((patch + 1)) / versionCode $next_code"
  echo "rationale: no commits on $range; default patch."
  exit 0
fi

lower="$(printf '%s\n' "$log" | tr '[:upper:]' '[:lower:]')"

level="patch"
reasons=()

has() {
  printf '%s\n' "$lower" | grep -Eqi "$1"
}

if has '(^|[[:space:]])(breaking|break:|major:)|breaking change'; then
  level="major"
  reasons+=("breaking change markers in commit subjects/bodies")
fi

# Notable product features even with weak prefixes
if [[ "$level" != "major" ]]; then
  if has 'folder browser|browse cartell|remote folder|feat(\(|:|/)|add:|new (settings|library|login)|user-visible'; then
    level="minor"
    reasons+=("new user-visible capability signals (e.g. feat/folder browser)")
  elif has '^[^ ]* feat|feat:'; then
    level="minor"
    reasons+=("feat-style commit subjects")
  fi
fi

if [[ "$level" == "patch" ]]; then
  if has 'fix(\(|:|/)|chore:|docs:'; then
    reasons+=("fix/chore/docs-style commits without clear new capability")
  else
    reasons+=("no strong feat/breaking signals; default patch")
  fi
fi

case "$level" in
  major)
    next_name="$((major + 1)).0.0"
    ;;
  minor)
    next_name="${major}.$((minor + 1)).0"
    ;;
  *)
    next_name="${major}.${minor}.$((patch + 1))"
    ;;
esac

echo "current: $current_name ($current_code)"
echo "range:   $range"
echo "commits:"
printf '%s\n' "$oneline" | sed 's/^/  /'
echo "suggest: $level → $next_name / versionCode $next_code"
echo "rationale: ${reasons[*]}"
echo "confirm: ask the user before editing Gradle (rule 41)."
