#!/usr/bin/env bash
#═══════════════════════════════════════════════════════════════════════════════
# SIMJOT RUN SCRIPT
#═══════════════════════════════════════════════════════════════════════════════
# Builds (if needed) and runs Simjot with proper Java configuration.
#
# Usage:
#   ./run.sh [options] [-- java-args]
#
# Options:
#   --no-build    Skip building, use existing JAR
#   --build       Force rebuild (default if JAR missing)
#   --no-native   Run without native library (pure Java mode)
#   --            Pass remaining args to Java
#═══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${ROOT}/Simjot"
POM_FILE="${PROJECT_DIR}/pom.xml"
NATIVE_DIR="${PROJECT_DIR}/src/main/native"

REQUIRED_VERSION="24"
if [[ -f "${POM_FILE}" ]]; then
  REQUIRED_VERSION="$(awk -F'[<>]' '/maven.compiler.release/{print $3; exit}' "${POM_FILE}")"
  if [[ -z "${REQUIRED_VERSION}" ]]; then
    REQUIRED_VERSION="24"
  fi
fi

java_major_version() {
  local java_bin="$1"
  local version
  version="$("${java_bin}" -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
  if [[ "${version}" == "1" ]]; then
    version="$("${java_bin}" -version 2>&1 | awk -F'[\".]' '/version/ {print $3; exit}')"
  fi
  echo "${version}"
}

java_home_ok() {
  local home="$1"
  [[ -x "${home}/bin/java" ]] || return 1
  local major
  major="$(java_major_version "${home}/bin/java")"
  [[ -n "${major}" && "${major}" -ge "${REQUIRED_VERSION}" ]]
}

resolve_java_home() {
  if [[ -n "${JAVA_HOME:-}" ]] && java_home_ok "${JAVA_HOME}"; then
    echo "${JAVA_HOME}"
    return 0
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local mac_home
    mac_home="$(/usr/libexec/java_home -v "${REQUIRED_VERSION}" 2>/dev/null || true)"
    if [[ -n "${mac_home}" ]] && java_home_ok "${mac_home}"; then
      echo "${mac_home}"
      return 0
    fi
  fi

  for home in "/opt/homebrew/opt/openjdk@${REQUIRED_VERSION}" \
              "/usr/local/opt/openjdk@${REQUIRED_VERSION}" \
              "/opt/homebrew/opt/openjdk" \
              "/usr/local/opt/openjdk"; do
    if java_home_ok "${home}"; then
      echo "${home}"
      return 0
    fi
  done

  for home in /usr/lib/jvm/java-"${REQUIRED_VERSION}"* /usr/lib/jvm/jdk-"${REQUIRED_VERSION}"* /usr/lib/jvm/*"${REQUIRED_VERSION}"*; do
    if [[ -d "${home}" ]] && java_home_ok "${home}"; then
      echo "${home}"
      return 0
    fi
  done

  return 1
}

JAVA_HOME="$(resolve_java_home)" || {
  echo "Could not find a JDK ${REQUIRED_VERSION}+ installation. Set JAVA_HOME to a compatible JDK." >&2
  exit 1
}
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

BUILD=true
USE_NATIVE=true
RUN_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) BUILD=false; shift ;;
    --build) BUILD=true; shift ;;
    --no-native) USE_NATIVE=false; shift ;;
    --) shift; RUN_ARGS+=("$@"); break ;;
    *) RUN_ARGS+=("$1"); shift ;;
  esac
done

#═══════════════════════════════════════════════════════════════════════════════
# CHECK NATIVE LIBRARY
#═══════════════════════════════════════════════════════════════════════════════

NATIVE_LIB=""
NATIVE_OPTS=()

if [[ "${USE_NATIVE}" == true ]]; then
  # Detect platform-specific library name
  if [[ "$(uname)" == "Darwin" ]]; then
    LIB_NAME="libsimjot_native.dylib"
  elif [[ "$(uname)" == "Linux" ]]; then
    LIB_NAME="libsimjot_native.so"
  else
    LIB_NAME="libsimjot_native.dll"
  fi

  # Check for native library in various locations
  for loc in "${NATIVE_DIR}/${LIB_NAME}" \
             "${NATIVE_DIR}/build/${LIB_NAME}" \
             "${PROJECT_DIR}/src/main/resources/native/${LIB_NAME}"; do
    if [[ -f "${loc}" ]]; then
      NATIVE_LIB="${loc}"
      break
    fi
  done

  if [[ -n "${NATIVE_LIB}" ]]; then
    NATIVE_LIB_DIR="$(dirname "${NATIVE_LIB}")"
    echo -e "${GREEN}✓${NC} Native library found: ${NATIVE_LIB}"
    
    # Add library path for FFM
    NATIVE_OPTS+=(
      "-Djava.library.path=${NATIVE_LIB_DIR}"
      "--enable-native-access=ALL-UNNAMED"
    )
    
    # Platform-specific library path
    if [[ "$(uname)" == "Darwin" ]]; then
      export DYLD_LIBRARY_PATH="${NATIVE_LIB_DIR}:${DYLD_LIBRARY_PATH:-}"
    else
      export LD_LIBRARY_PATH="${NATIVE_LIB_DIR}:${LD_LIBRARY_PATH:-}"
    fi
  else
    echo -e "${YELLOW}⚠${NC}  Native library not found. Running in pure Java mode."
    echo -e "   To enable native acceleration, run: ${CYAN}./compile-native.sh${NC}"
    echo ""
  fi
fi

shopt -s nullglob
jars=( "${PROJECT_DIR}"/target/Simjot-*.jar )
shopt -u nullglob
JAR_PATH=""
if [[ ${#jars[@]} -gt 0 ]]; then
  JAR_PATH="$(ls -t "${PROJECT_DIR}"/target/Simjot-*.jar | head -n 1)"
fi

if [[ "${BUILD}" == true || -z "${JAR_PATH}" ]]; then
  echo "Building Simjot with JDK ${REQUIRED_VERSION}+..."
  mvn -f "${POM_FILE}" -DskipTests package
  JAR_PATH="$(ls -t "${PROJECT_DIR}"/target/Simjot-*.jar | head -n 1)"
fi

if [[ -z "${JAR_PATH}" || ! -f "${JAR_PATH}" ]]; then
  echo "Unable to locate the built Simjot jar in ${PROJECT_DIR}/target." >&2
  exit 1
fi

#═══════════════════════════════════════════════════════════════════════════════
# RUN SIMJOT
#═══════════════════════════════════════════════════════════════════════════════

# Build Java command with native options
JAVA_CMD=(
  "${JAVA_HOME}/bin/java"
  -Xmx1G
  "--enable-preview"
)

# Add native library options if available
if [[ ${#NATIVE_OPTS[@]} -gt 0 ]]; then
  JAVA_CMD+=("${NATIVE_OPTS[@]}")
fi

# Add JAR and user args
JAVA_CMD+=(-jar "${JAR_PATH}")
if [[ ${#RUN_ARGS[@]} -gt 0 ]]; then
  JAVA_CMD+=("${RUN_ARGS[@]}")
fi

exec "${JAVA_CMD[@]}"
