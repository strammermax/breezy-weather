#!/usr/bin/env bash
# Lokale CI-equivalent check: spotless (lint/format) + release-achtige build,
# zonder de destructieve `gradlew clean` (die sloopt locales_config.xml, zie
# projectgeheugen "Build quirks"). Gebruikt --rerun-tasks in plaats daarvan
# zodat gegenereerde resources zoals locales_config.xml altijd vers zijn.
#
# Gebruik: ./scripts/ci-check.sh
set -euo pipefail
cd "$(dirname "$0")/.."

for candidate in \
    "/c/Program Files/Android/Android Studio1/jbr" \
    "/c/Program Files/Android/Android Studio/jbr" \
    "/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot"; do
    if [ -d "$candidate" ]; then
        export JAVA_HOME="$candidate"
        break
    fi
done

if [ -z "${JAVA_HOME:-}" ]; then
    echo "Geen bruikbare JDK gevonden, zie build-quirks memory." >&2
    exit 1
fi

echo "== Using JAVA_HOME: $JAVA_HOME"

echo "== [1/2] spotlessCheck (zelfde stap als GitHub Actions 'Lint')"
./gradlew.bat spotlessCheck --console=plain

echo "== [2/2] assembleBasicDebug --rerun-tasks (CI-equivalente build, zonder 'clean')"
./gradlew.bat :app:assembleBasicDebug --rerun-tasks --console=plain

echo "== OK: spotless + build zijn groen, zoals CI dat ook zou vinden."
