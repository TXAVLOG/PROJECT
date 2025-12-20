#!/bin/bash
# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Demo - Quick Build Script (Debug/Release)
# Usage: ./TXAQuickBuild.sh [debug|release]

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Get build type from parameter or default to debug
BUILD_TYPE="${1:-debug}"
if [ "$BUILD_TYPE" != "debug" ] && [ "$BUILD_TYPE" != "release" ]; then
    log_error "Invalid build type: $BUILD_TYPE. Use 'debug' or 'release'"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$SCRIPT_DIR/.env"

# Load environment
if [ ! -f "$ENV_FILE" ]; then
    log_error "Environment file not found: $ENV_FILE"
    exit 1
fi
source "$ENV_FILE"

# Get version
VERSION_FILE="$PROJECT_ROOT/version.properties"
VERSION_NAME=$(grep "versionName" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')
VERSION_CODE=$(grep "versionCode" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')

log_info "Quick Build: TXA Demo v$VERSION_NAME ($VERSION_CODE) - $BUILD_TYPE"

# Kill processes
log_info "Stopping background processes..."
pkill -9 java || true
pkill -9 gradle || true
cd "$PROJECT_ROOT" && ./gradlew --stop || true

# Clean and build
log_info "Building $BUILD_TYPE APK..."
cd "$PROJECT_ROOT"

if [ "$BUILD_TYPE" = "debug" ]; then
    ./gradlew clean assembleDebug
    APK_FILE="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
else
    ./gradlew clean assembleRelease
    APK_FILE="$PROJECT_ROOT/app/build/outputs/apk/release/app-release.apk"
fi

# Check build result
if [ ! -f "$APK_FILE" ]; then
    log_error "Build failed - APK not found: $APK_FILE"
    exit 1
fi

# Copy to output
BUILD_DIR="$PROJECT_ROOT/TXABUILD"
mkdir -p "$BUILD_DIR"
OUTPUT_FILE="$BUILD_DIR/TXADEMO-$VERSION_NAME-$BUILD_TYPE.apk"
cp "$APK_FILE" "$OUTPUT_FILE"

# Git config and upload
git config user.email "$GIT_EMAIL"
git config user.name "$GIT_NAME"
git add "$OUTPUT_FILE"
git commit -m "build: TXADEMO-$VERSION_NAME-$BUILD_TYPE" || true
git push origin main || true

log_success "Build completed!"
log_info "Output: $OUTPUT_FILE"
log_info "Size: $(du -h "$OUTPUT_FILE" | cut -f1)"
