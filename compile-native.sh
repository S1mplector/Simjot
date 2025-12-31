#!/usr/bin/env bash
#═══════════════════════════════════════════════════════════════════════════════
# SIMJOT NATIVE LIBRARY COMPILATION SCRIPT
#═══════════════════════════════════════════════════════════════════════════════
# This script compiles the native C/C++ library for Simjot.
# It handles:
#   - Native library (libsimjot_native.dylib/.so/.dll)
#   - Optional Haskell FFI library
#   - Proper library placement for Java FFM integration
#
# Usage:
#   ./compile-native.sh [options]
#
# Options:
#   --clean       Clean build directories before compiling
#   --release     Build with optimizations (default: debug)
#   --haskell     Also build the Haskell FFI library
#   --skip-tests  Skip running native tests
#   --help        Show this help message
#═══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Resolve paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/Simjot"
NATIVE_DIR="${PROJECT_DIR}/src/main/native"
HASKELL_DIR="${PROJECT_DIR}/src/main/haskell"
BUILD_DIR="${NATIVE_DIR}/build"

# Default options
CLEAN=false
BUILD_TYPE="Debug"
BUILD_HASKELL=false
RUN_TESTS=true

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --clean)
            CLEAN=true
            shift
            ;;
        --release)
            BUILD_TYPE="Release"
            shift
            ;;
        --haskell)
            BUILD_HASKELL=true
            shift
            ;;
        --skip-tests)
            RUN_TESTS=false
            shift
            ;;
        --help|-h)
            head -25 "$0" | tail -20
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

#═══════════════════════════════════════════════════════════════════════════════
# UTILITY FUNCTIONS
#═══════════════════════════════════════════════════════════════════════════════

info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_command() {
    if ! command -v "$1" &>/dev/null; then
        error "$1 is not installed or not in PATH"
        return 1
    fi
    return 0
}

#═══════════════════════════════════════════════════════════════════════════════
# CHECK PREREQUISITES
#═══════════════════════════════════════════════════════════════════════════════

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}                    SIMJOT NATIVE LIBRARY COMPILER                             ${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════════════${NC}"
echo ""

info "Checking prerequisites..."

# Check for CMake
if ! check_command cmake; then
    echo ""
    echo "To install CMake:"
    echo "  macOS:  brew install cmake"
    echo "  Ubuntu: sudo apt install cmake"
    echo "  Fedora: sudo dnf install cmake"
    exit 1
fi
CMAKE_VERSION=$(cmake --version | head -1 | awk '{print $3}')
success "CMake ${CMAKE_VERSION} found"

# Check for C/C++ compiler
if [[ "$(uname)" == "Darwin" ]]; then
    if ! check_command clang; then
        error "Clang is required on macOS. Install Xcode Command Line Tools:"
        echo "  xcode-select --install"
        exit 1
    fi
    COMPILER="clang"
    COMPILER_VERSION=$(clang --version | head -1)
else
    if ! check_command gcc; then
        error "GCC is required. Install build-essential:"
        echo "  sudo apt install build-essential"
        exit 1
    fi
    COMPILER="gcc"
    COMPILER_VERSION=$(gcc --version | head -1)
fi
success "C/C++ compiler: ${COMPILER_VERSION}"

# Check for Haskell tools if requested
if [[ "${BUILD_HASKELL}" == true ]]; then
    if ! check_command ghc; then
        warn "GHC not found. Skipping Haskell build."
        warn "Install with: brew install ghc cabal-install"
        BUILD_HASKELL=false
    elif ! check_command cabal; then
        warn "Cabal not found. Skipping Haskell build."
        warn "Install with: brew install cabal-install"
        BUILD_HASKELL=false
    else
        GHC_VERSION=$(ghc --version | awk '{print $NF}')
        success "GHC ${GHC_VERSION} found"
    fi
fi

#═══════════════════════════════════════════════════════════════════════════════
# BUILD NATIVE LIBRARY
#═══════════════════════════════════════════════════════════════════════════════

echo ""
info "Building native library (${BUILD_TYPE} mode)..."

cd "${NATIVE_DIR}"

# Clean if requested
if [[ "${CLEAN}" == true ]]; then
    info "Cleaning build directory..."
    rm -rf "${BUILD_DIR}"
    rm -f libsimjot_native.dylib libsimjot_native.so libsimjot_native.dll
fi

# Create build directory
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"

# Configure with CMake
info "Configuring with CMake..."
cmake .. \
    -DCMAKE_BUILD_TYPE="${BUILD_TYPE}" \
    -DSIMJOT_NATIVE_BUILD_TESTS=ON

# Build
info "Compiling native library..."
cmake --build . --parallel

# Check if library was built
if [[ "$(uname)" == "Darwin" ]]; then
    LIB_NAME="libsimjot_native.dylib"
elif [[ "$(uname)" == "Linux" ]]; then
    LIB_NAME="libsimjot_native.so"
else
    LIB_NAME="libsimjot_native.dll"
fi

if [[ -f "${BUILD_DIR}/${LIB_NAME}" ]]; then
    success "Built ${LIB_NAME}"
    
    # Copy to native directory for easy access
    cp "${BUILD_DIR}/${LIB_NAME}" "${NATIVE_DIR}/"
    success "Copied to ${NATIVE_DIR}/${LIB_NAME}"
    
    # Also copy to resources for packaging
    RESOURCES_DIR="${PROJECT_DIR}/src/main/resources/native"
    mkdir -p "${RESOURCES_DIR}"
    cp "${BUILD_DIR}/${LIB_NAME}" "${RESOURCES_DIR}/"
    success "Copied to ${RESOURCES_DIR}/${LIB_NAME}"
else
    error "Failed to build ${LIB_NAME}"
    exit 1
fi

# Run tests if requested
if [[ "${RUN_TESTS}" == true ]]; then
    echo ""
    info "Running native tests..."
    if [[ -f "${BUILD_DIR}/simjot_native_cli" ]]; then
        if "${BUILD_DIR}/simjot_native_cli"; then
            success "Native tests passed"
        else
            warn "Some native tests failed"
        fi
    else
        warn "Test binary not found, skipping tests"
    fi
fi

#═══════════════════════════════════════════════════════════════════════════════
# BUILD HASKELL LIBRARY (OPTIONAL)
#═══════════════════════════════════════════════════════════════════════════════

if [[ "${BUILD_HASKELL}" == true ]]; then
    echo ""
    info "Building Haskell FFI library..."
    
    cd "${HASKELL_DIR}"
    
    # Update cabal package index
    cabal update
    
    # Configure with native library path
    export LIBRARY_PATH="${NATIVE_DIR}:${LIBRARY_PATH:-}"
    export LD_LIBRARY_PATH="${NATIVE_DIR}:${LD_LIBRARY_PATH:-}"
    export DYLD_LIBRARY_PATH="${NATIVE_DIR}:${DYLD_LIBRARY_PATH:-}"
    
    # Build
    if cabal build; then
        success "Haskell library built successfully"
    else
        warn "Haskell build failed (native library linkage issue)"
        warn "The Haskell library requires the native library to be properly linked"
    fi
fi

#═══════════════════════════════════════════════════════════════════════════════
# SUMMARY
#═══════════════════════════════════════════════════════════════════════════════

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}                         BUILD COMPLETE                                        ${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${GREEN}Native library:${NC} ${NATIVE_DIR}/${LIB_NAME}"
echo -e "${GREEN}Resources:${NC}      ${RESOURCES_DIR}/${LIB_NAME}"
echo ""
echo -e "You can now run Simjot with native acceleration:"
echo -e "  ${CYAN}./run.sh${NC}"
echo ""
echo -e "Or build a distributable package:"
echo -e "  ${CYAN}./Simjot/packaging/jpackage-macos.sh${NC}  (macOS DMG)"
echo -e "  ${CYAN}./Simjot/packaging/build-linux-deb.sh${NC} (Linux DEB)"
echo ""
