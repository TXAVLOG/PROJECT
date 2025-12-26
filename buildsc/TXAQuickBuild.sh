#!/bin/bash
# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Music - Quick Build Script (Debug/Release)
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

usage() {
    cat <<EOF
Usage: ./buildsc/TXAQuickBuild.sh [--release|--debug]

Options:
  --release, -r      Build release APK (mặc định là debug nếu không truyền)
  --debug, -d        Ép build debug (default)
  -h, --help         Hiển thị hướng dẫn
EOF
}

BUILD_TYPE="debug"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --release|-r)
            BUILD_TYPE="release"
            shift
            ;;
        --debug|-d)
            BUILD_TYPE="debug"
            shift
            ;;
        --type|-t)
            shift
            if [[ -z "${1:-}" ]]; then
                log_error "--type cần giá trị (debug hoặc release)"
                usage
                exit 1
            fi
            BUILD_TYPE="$1"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

if [ "$BUILD_TYPE" != "debug" ] && [ "$BUILD_TYPE" != "release" ]; then
    log_error "Invalid build type: $BUILD_TYPE. Use 'debug' or 'release'"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$SCRIPT_DIR/.env"
GRADLEW="$PROJECT_ROOT/gradlew"

# Load environment
if [ ! -f "$ENV_FILE" ]; then
    log_error "Environment file not found: $ENV_FILE"
    exit 1
fi
source "$ENV_FILE"

# Optimize Gradle memory usage (VPS has 16GB RAM)
export GRADLE_OPTS="${GRADLE_OPTS:--Xmx8g -Xms4g -XX:MaxMetaspaceSize=1024m -XX:+HeapDumpOnOutOfMemoryError}"
log_info "Gradle JVM options: $GRADLE_OPTS"

# Get version
VERSION_FILE="$PROJECT_ROOT/version.properties"
VERSION_NAME=$(grep "versionName" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')
VERSION_CODE=$(grep "versionCode" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')

log_info "Quick Build: TXA Music v$VERSION_NAME ($VERSION_CODE) - $BUILD_TYPE"

# Ensure gradlew exists
if [ ! -f "$GRADLEW" ]; then
    log_error "Gradle wrapper not found: $GRADLEW"
    exit 1
fi
chmod +x "$GRADLEW" || true

# Kill processes
log_info "Stopping background processes..."
pkill -9 java || true
pkill -9 gradle || true
cd "$PROJECT_ROOT" && "$GRADLEW" --stop || true

# Clean and build
log_info "Building $BUILD_TYPE APK..."
cd "$PROJECT_ROOT"

if [ "$BUILD_TYPE" = "debug" ]; then
    "$GRADLEW" clean assembleDebug
    APK_FILE="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
else
    "$GRADLEW" clean assembleRelease
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
OUTPUT_FILE="$BUILD_DIR/TXAMusic-$VERSION_NAME-$BUILD_TYPE.apk"
cp "$APK_FILE" "$OUTPUT_FILE"

# Git config and upload
# Validate Git configuration
if [ -z "$GIT_EMAIL" ] || [ -z "$GIT_NAME" ]; then
    log_error "Git configuration missing in .env file"
    log_error "Please set GIT_EMAIL and GIT_NAME in buildsc/.env"
    log_error "Example:"
    log_error "GIT_EMAIL=your-email@example.com"
    log_error "GIT_NAME=Your Name"
    exit 1
fi

log_info "Configuring Git as: $GIT_NAME <$GIT_EMAIL>"
git config user.email "$GIT_EMAIL"
git config user.name "$GIT_NAME"

# Force add APK file bypassing gitignore
git add -f "$OUTPUT_FILE"
git commit -m "build: TXAMusic-$VERSION_NAME-$BUILD_TYPE" || true

# Auto-detect current branch and push
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
log_info "Pushing to branch: $CURRENT_BRANCH"
git push origin "$CURRENT_BRANCH" || true

log_success "Build completed!"
log_info "Output: $OUTPUT_FILE"
log_info "Size: $(du -h "$OUTPUT_FILE" | cut -f1)"
