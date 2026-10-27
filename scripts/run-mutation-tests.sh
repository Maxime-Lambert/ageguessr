#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root/ageguessr-api"

# PIT mute les classes compilees et lance les tests compiles : sur un clone neuf (CI),
# target/ est vide, d'ou test-compile avant mutationCoverage.
./mvnw -q test-compile org.pitest:pitest-maven:mutationCoverage
