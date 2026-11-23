#!/usr/bin/env bash
# One command to verify the project.
#   ./scripts/check.sh            back-end tests + front-end tests, lint, build
#   ./scripts/check.sh --fast     back-end unit tests only (no Testcontainers) + front-end typecheck/tests
#   ./scripts/check.sh --security also run Semgrep, Trivy and Gitleaks (needs Docker)
set -euo pipefail
cd "$(dirname "$0")/.."

FAST=false; SECURITY=false
for arg in "$@"; do
  case $arg in --fast) FAST=true;; --security) SECURITY=true;; esac
done

step() { printf '\n\033[1;36m▶ %s\033[0m\n' "$1"; }

if [ -d backend ]; then
  step "Back end"
  if $FAST; then
    (cd backend && ./mvnw -q -Dgroups='!integration & !eval' test)
  else
    (cd backend && ./mvnw -q -Dgroups='!eval' verify)   # includes JaCoCo report: backend/target/site/jacoco
  fi
fi

if [ -d frontend ]; then
  step "Front end"
  (cd frontend && npx tsc -b && npm test -- --run)
  if ! $FAST; then (cd frontend && npm run lint && npm run build); fi
fi

if $SECURITY; then
  step "Security scans"
  ./scripts/security-scan.sh
fi

printf '\n\033[1;32m✔ All checks passed\033[0m\n'
