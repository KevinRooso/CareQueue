#!/usr/bin/env bash
# Security testing with no local installs – every tool runs in Docker.
#   Semgrep  – static analysis of our code (SAST): injection, unsafe XML parsing, etc.
#   Trivy    – known vulnerabilities (CVEs) in Maven/npm dependencies and config
#   Gitleaks – secrets accidentally committed to git
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p reports/security

echo "▶ Semgrep (SAST)"
docker run --rm -v "$PWD:/src" semgrep/semgrep semgrep scan \
  --config p/java --config p/typescript --config p/secrets \
  --error --json --output /src/reports/security/semgrep.json /src || { echo "Semgrep found issues – see reports/security/semgrep.json"; FAIL=1; }

echo "▶ Trivy (dependencies & config)"
docker run --rm -v "$PWD:/src" aquasec/trivy fs --scanners vuln,misconfig \
  --severity HIGH,CRITICAL --exit-code 1 /src | tee reports/security/trivy.txt || FAIL=1

echo "▶ Gitleaks (secrets)"
docker run --rm -v "$PWD:/repo" zricethezav/gitleaks detect --source /repo \
  --report-path /repo/reports/security/gitleaks.json || FAIL=1

if [ "${FAIL:-0}" = "1" ]; then echo "✖ Security scan found problems"; exit 1; fi
echo "✔ Security scans clean"
