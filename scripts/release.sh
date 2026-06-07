#!/usr/bin/env bash
#
# Release helper for the Container Peeker mod.
#
# Builds the jar for the current (or bumped) version, tags it, pushes, and
# publishes a GitHub release with the jar attached.
#
# Usage:
#   scripts/release.sh                 Release the current version as-is
#   scripts/release.sh patch           Bump x.y.Z, then release
#   scripts/release.sh minor           Bump x.Y.0, then release
#   scripts/release.sh major           Bump X.0.0, then release
#   scripts/release.sh --set 2.3.1     Set an exact version, then release
#
# Flags:
#   -y, --yes      Skip the confirmation prompt
#   -h, --help     Show this help
#
# Notes:
#   - Aborts if there are uncommitted *tracked* changes (untracked files are OK).
#   - Requires git, gh (authenticated), a GitHub 'origin' remote, and JDK 21.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

GRADLE_PROPS="gradle.properties"

# ----- helpers --------------------------------------------------------------

err()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; }
info() { printf '\033[36m==>\033[0m %s\n' "$*"; }

usage() { sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//; $d'; }

read_prop() {
	# read_prop <key> -> value from gradle.properties
	local key="$1"
	grep -E "^${key}=" "$GRADLE_PROPS" | head -n1 | cut -d= -f2-
}

# ----- parse args -----------------------------------------------------------

BUMP=""        # patch|minor|major
EXPLICIT=""    # x.y.z
ASSUME_YES=0

while [[ $# -gt 0 ]]; do
	case "$1" in
		patch|minor|major) BUMP="$1"; shift ;;
		--set) EXPLICIT="${2:-}"; shift 2 ;;
		-y|--yes) ASSUME_YES=1; shift ;;
		-h|--help) usage; exit 0 ;;
		*) err "unknown argument: $1"; usage; exit 2 ;;
	esac
done

if [[ -n "$BUMP" && -n "$EXPLICIT" ]]; then
	err "use either a bump (patch/minor/major) or --set, not both"
	exit 2
fi

# ----- preflight ------------------------------------------------------------

command -v git >/dev/null || { err "git not found"; exit 1; }
command -v gh  >/dev/null || { err "gh (GitHub CLI) not found - install it first"; exit 1; }

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { err "not inside a git repo"; exit 1; }

# Abort on uncommitted *tracked* changes (staged or unstaged). Untracked is fine.
if ! git diff --quiet || ! git diff --cached --quiet; then
	err "you have uncommitted tracked changes; commit or stash them first"
	git --no-pager status --short --untracked-files=no >&2
	exit 1
fi

gh auth status >/dev/null 2>&1 || { err "gh is not authenticated; run 'gh auth login'"; exit 1; }
git remote get-url origin >/dev/null 2>&1 || { err "no 'origin' remote; add one (e.g. 'gh repo create')"; exit 1; }

# Prefer a JDK 21 (Loom requires it). macOS java_home if available.
if [[ -x /usr/libexec/java_home ]]; then
	if JH="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
		export JAVA_HOME="$JH"
	fi
fi

# ----- compute version ------------------------------------------------------

CURRENT="$(read_prop mod_version)"
ARCHIVE="$(read_prop archives_base_name)"
[[ -n "$CURRENT" ]] || { err "could not read mod_version from $GRADLE_PROPS"; exit 1; }
[[ -n "$ARCHIVE" ]] || { err "could not read archives_base_name from $GRADLE_PROPS"; exit 1; }

NEW="$CURRENT"
if [[ -n "$EXPLICIT" ]]; then
	NEW="$EXPLICIT"
elif [[ -n "$BUMP" ]]; then
	IFS='.' read -r MA MI PA <<<"$CURRENT"
	case "$BUMP" in
		major) MA=$((MA + 1)); MI=0; PA=0 ;;
		minor) MI=$((MI + 1)); PA=0 ;;
		patch) PA=$((PA + 1)) ;;
	esac
	NEW="${MA}.${MI}.${PA}"
fi

[[ "$NEW" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { err "version '$NEW' is not semver (x.y.z)"; exit 1; }

TAG="v${NEW}"
JAR="build/libs/${ARCHIVE}-${NEW}.jar"

if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
	err "tag ${TAG} already exists"
	exit 1
fi

# ----- confirm --------------------------------------------------------------

info "Release plan:"
echo "    current version : ${CURRENT}"
echo "    new version     : ${NEW}$( [[ "$NEW" != "$CURRENT" ]] && echo '  (will bump + commit gradle.properties)' )"
echo "    git tag         : ${TAG}"
echo "    artifact        : ${JAR}"
echo "    remote          : $(git remote get-url origin)"

if [[ "$ASSUME_YES" -ne 1 ]]; then
	read -r -p "Proceed? [y/N] " ans
	[[ "$ans" =~ ^[Yy]$ ]] || { info "aborted"; exit 0; }
fi

# ----- bump (if requested) --------------------------------------------------

if [[ "$NEW" != "$CURRENT" ]]; then
	info "Bumping version ${CURRENT} -> ${NEW}"
	# Portable in-place edit (BSD/macOS + GNU sed).
	tmp="$(mktemp)"
	sed "s/^mod_version=.*/mod_version=${NEW}/" "$GRADLE_PROPS" >"$tmp"
	mv "$tmp" "$GRADLE_PROPS"
	git add "$GRADLE_PROPS"
	git commit -m "chore(release): v${NEW}"
fi

# ----- build ----------------------------------------------------------------

info "Building ${JAR}"
./gradlew clean build

[[ -f "$JAR" ]] || { err "expected artifact not found: ${JAR}"; exit 1; }

# ----- tag + push -----------------------------------------------------------

info "Tagging ${TAG}"
git tag -a "${TAG}" -m "Container Peeker ${TAG}"

info "Pushing branch and tag to origin"
git push origin HEAD
git push origin "${TAG}"

# ----- github release -------------------------------------------------------

info "Creating GitHub release ${TAG}"
gh release create "${TAG}" "${JAR}" \
	--title "Container Peeker ${NEW}" \
	--generate-notes

info "Done: $(gh release view "${TAG}" --json url --jq .url 2>/dev/null || echo "${TAG} released")"
