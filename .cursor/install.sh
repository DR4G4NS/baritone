#!/usr/bin/env bash
#
# Idempotent Cloud Agent setup for Baritone.
#
# The 26.1 branch targets Java 25 (see gradle.properties `java_version=25` and the
# GitHub Actions workflows, which use Zulu JDK 25). The default Cloud Agent image
# ships JDK 21, so this script provisions a JDK 25 toolchain that Gradle can use,
# then warms the Gradle cache by resolving dependencies and compiling every source
# set exercised by CI. It is safe to run repeatedly and against a warm snapshot.
set -euo pipefail

JVM_DIR=/usr/lib/jvm
JDK_LINK="$JVM_DIR/temurin-25-jdk"

log() { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }

install_jdk25() {
    if [ -x "$JDK_LINK/bin/java" ] && "$JDK_LINK/bin/java" -version 2>&1 | grep -q 'version "25'; then
        log "JDK 25 already present at $JDK_LINK; skipping download"
        return
    fi
    log "Installing Temurin JDK 25 (GA) into $JVM_DIR"
    local tmp
    tmp="$(mktemp -d)"
    curl -fL --retry 4 --retry-delay 4 -o "$tmp/jdk25.tar.gz" \
        "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
    sudo mkdir -p "$JVM_DIR"
    sudo tar xzf "$tmp/jdk25.tar.gz" -C "$JVM_DIR"
    local extracted
    extracted="$(find "$JVM_DIR" -maxdepth 1 -type d -name 'jdk-25*' | sort | tail -n1)"
    sudo ln -sfn "$extracted" "$JDK_LINK"
    rm -rf "$tmp"
}

install_jdk25

export JAVA_HOME="$JDK_LINK"
export PATH="$JAVA_HOME/bin:$PATH"

# Persist the toolchain for interactive shells (idempotent).
if ! grep -q 'temurin-25-jdk' "$HOME/.bashrc" 2>/dev/null; then
    {
        echo ''
        echo '# Baritone Cloud Agent: use the Temurin JDK 25 toolchain by default'
        echo 'export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk'
        echo 'export PATH="$JAVA_HOME/bin:$PATH"'
    } >> "$HOME/.bashrc"
fi

log "Active Java toolchain"
java -version

cd "$(dirname "$0")/.."
chmod +x gradlew client-gametest/gradlew

log "Warming Gradle: resolving dependencies and compiling all source sets"
./gradlew --no-daemon --console=plain \
    testClasses \
    testkitClasses \
    deterministicTestClasses \
    headlessReplayTestClasses \
    benchmarkClasses \
    :fabric:classes \
    :tweaker:classes

log "Baritone Cloud Agent environment is ready"
