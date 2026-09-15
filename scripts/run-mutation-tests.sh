#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root/ageguessr-api"

./mvnw -q org.pitest:pitest-maven:mutationCoverage
