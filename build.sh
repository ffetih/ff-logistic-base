#!/usr/bin/env bash
# Installs the BOM and the parent, then builds the reactor.
# The parent imports the BOM. Maven does not resolve that import from a
# sibling module, so a clean machine needs these two installs first.
#
#   ./build.sh
#   ./build.sh -DskipTests
set -euo pipefail
cd "$(dirname "$0")"
MVN=(./mvnw -B)

echo "[1/3] BOM and parent"
"${MVN[@]}" -q -f ff-base-bom/pom.xml install
"${MVN[@]}" -q -f ff-base-parent/pom.xml install

echo "[2/3] Reactor"
"${MVN[@]}" install "$@"

echo "[3/3] Done."
