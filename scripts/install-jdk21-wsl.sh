#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$HOME/jdk"
cd /tmp

if [ ! -x "$HOME/jdk/current/bin/java" ]; then
  echo "Downloading Temurin JDK 21..."
  curl -fsSL -o openjdk21.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"
  rm -rf "$HOME/jdk"/jdk-21* "$HOME/jdk/current"
  tar -xzf openjdk21.tar.gz -C "$HOME/jdk"
  dir="$(find "$HOME/jdk" -maxdepth 1 -type d -name 'jdk-21*' | head -1)"
  ln -sfn "$dir" "$HOME/jdk/current"
  rm -f openjdk21.tar.gz
  echo "Extracted to $dir"
fi

MARKER='# JDK 21 (AiFlow / FlightHub SDK)'
if ! grep -qF "$MARKER" "$HOME/.bashrc" 2>/dev/null; then
  cat >> "$HOME/.bashrc" <<'EOF'

# JDK 21 (AiFlow / FlightHub SDK)
export JAVA_HOME="$HOME/jdk/current"
export PATH="$JAVA_HOME/bin:$PATH"
EOF
  echo "Added JAVA_HOME to ~/.bashrc"
fi

export JAVA_HOME="$HOME/jdk/current"
export PATH="$JAVA_HOME/bin:$PATH"
echo "==== java -version ===="
java -version
echo "JAVA_HOME=$JAVA_HOME"
echo "Done."
