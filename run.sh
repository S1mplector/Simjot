#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${ROOT}/Simjot"
POM_FILE="${PROJECT_DIR}/pom.xml"

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
RUN_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) BUILD=false; shift ;;
    --build) BUILD=true; shift ;;
    --) shift; RUN_ARGS+=("$@"); break ;;
    *) RUN_ARGS+=("$1"); shift ;;
  esac
done

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

exec "${JAVA_HOME}/bin/java" -Xmx1G -jar "${JAR_PATH}" ${RUN_ARGS[@]+"${RUN_ARGS[@]}"}
