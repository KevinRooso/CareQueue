#!/usr/bin/env bash
# Security testing with no local installs – every tool runs in Docker.
#   Semgrep  – static analysis of our code (SAST): injection, unsafe XML parsing, etc.
#   Trivy    – known vulnerabilities (CVEs) in Maven/npm dependencies, and Dockerfile/compose/CI misconfig
#   Gitleaks – secrets accidentally committed to git (the whole history)
# Reports: reports/security/ (gitignored). Exit code 1 if any tool finds something.
# Suppressions live in the repo, each with a one-line reason: .semgrepignore / `nosemgrep` comments,
# .trivyignore, .gitleaksignore.
set -euo pipefail
cd "$(dirname "$0")/.."
# Git Bash on Windows rewrites "/src"-style arguments into C:\... paths; Docker needs them as they are.
export MSYS_NO_PATHCONV=1

# Pinned: version tag (readable) + digest (immutable). Bump deliberately, at least 7 days after a release.
SEMGREP_IMAGE="semgrep/semgrep:1.185.0@sha256:67518c174185a2043fdbed16c70631b79209499b3671178e7194379b48295a0d"
TRIVY_IMAGE="aquasec/trivy:0.76.2@sha256:8f6bb947dbc528902e11f2f5be52f7b519f6a39d5d148f41166f2040499768fb"
GITLEAKS_IMAGE="zricethezav/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f"

mkdir -p reports/security
FAIL=0

echo "▶ Semgrep (SAST)"
# Targets follow .semgrepignore, which includes .gitignore: tracked code only (no node_modules, .env, docs).
docker run --rm -v "$PWD:/src" -w /src "$SEMGREP_IMAGE" semgrep scan \
  --config p/java --config p/typescript --config p/secrets \
  --metrics=off --error --json-output=reports/security/semgrep.json . \
  || { echo "Semgrep found issues – see reports/security/semgrep.json"; FAIL=1; }

echo "▶ Trivy (dependencies & config)"
# Lockfiles and manifests only (package-lock.json, pom.xml, Dockerfile, compose, workflows): build output
# and installed packages are skipped so nothing is counted twice. .trivyignore is read from /src.
# pom.xml: Trivy resolves the parent, BOMs and transitive deps from the local Maven repository
# (filled by any ./mvnw build; CI runs dependency:resolve first). --offline-scan stops it fetching
# hundreds of POMs from Maven Central, which rate-limits it (HTTP 429) and fails the scan.
TRIVY_M2=()
if [ -d "$HOME/.m2/repository" ]; then TRIVY_M2=(-v "$HOME/.m2/repository:/root/.m2/repository:ro"); fi
docker run --rm -v "$PWD:/src" -w /src -v carequeue-trivy-cache:/root/.cache/trivy "${TRIVY_M2[@]}" "$TRIVY_IMAGE" fs \
  --scanners vuln,misconfig --severity HIGH,CRITICAL --exit-code 1 --offline-scan \
  --skip-dirs '**/node_modules' --skip-dirs '**/target' --skip-dirs '**/dist' --skip-dirs reports \
  . | tee reports/security/trivy.txt || FAIL=1

echo "▶ Gitleaks (secrets in git history)"
# Git refuses to read a repo owned by another user ("dubious ownership"); in the container the bind
# mount's owner differs, so trust it for this one command (command-scope config, nothing persisted).
GIT_TRUST=(-e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0='*')
GITLEAKS_MOUNTS=(-v "$PWD:/repo" -w /repo)   # -w: .gitleaksignore is read from the working dir
if [ -f .git ]; then
  # A git worktree: .git is a file pointing at the main repo's .git/worktrees/<name>. Mount the
  # shared .git directory and point git at it, so the history is visible inside the container.
  common_dir="$(cd "$(git rev-parse --git-common-dir)" && pwd)"
  worktree_name="$(basename "$(git rev-parse --absolute-git-dir)")"
  GITLEAKS_MOUNTS+=(-v "$common_dir:/git-common:ro"
    -e GIT_DIR="/git-common/worktrees/$worktree_name" -e GIT_WORK_TREE=/repo)
fi
docker run --rm "${GITLEAKS_MOUNTS[@]}" "${GIT_TRUST[@]}" "$GITLEAKS_IMAGE" git /repo \
  --redact --report-format json --report-path /repo/reports/security/gitleaks.json \
  || FAIL=1

if [ "$FAIL" = "1" ]; then echo "✖ Security scan found problems"; exit 1; fi
echo "✔ Security scans clean"
