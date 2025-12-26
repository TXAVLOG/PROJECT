#!/bin/bash
# FILE BY TXA - Contact: fb.com/vlog.txa.2311
# TXA Music - Linux/VPS Quick Build + GitHub Release
# Usage: ./TXAQuickBuild.sh [--release]

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Parse args
BUILD_TYPE="debug"
[[ "$1" == "--release" || "$1" == "-r" ]] && BUILD_TYPE="release"

# Config
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$SCRIPT_DIR/.env"

# Load .env
if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
else
    log_error ".env not found: $ENV_FILE"
    exit 1
fi

# Get version
VERSION_FILE="$PROJECT_ROOT/version.properties"
VERSION_NAME=$(grep "versionName" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')
VERSION_CODE=$(grep "versionCode" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')

log_info "=== TXA Music v$VERSION_NAME ($VERSION_CODE) - $BUILD_TYPE ==="

# Gradle opts for VPS
export GRADLE_OPTS="${GRADLE_OPTS:--Xmx4g -Xms1g}"

# 1. BUILD
log_info "Building $BUILD_TYPE APK..."
cd "$PROJECT_ROOT"
chmod +x gradlew

if [ "$BUILD_TYPE" = "release" ]; then
    ./gradlew clean assembleRelease
    APK_SOURCE="app/build/outputs/apk/release/app-release.apk"
else
    ./gradlew clean assembleDebug
    APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
fi

if [ ! -f "$APK_SOURCE" ]; then
    log_error "APK not found: $APK_SOURCE"
    exit 1
fi

# Copy to TXABUILD
BUILD_DIR="$PROJECT_ROOT/TXABUILD"
mkdir -p "$BUILD_DIR"
OUTPUT_FILE="$BUILD_DIR/TXAMusic-$VERSION_NAME-$BUILD_TYPE.apk"
cp "$APK_SOURCE" "$OUTPUT_FILE"

APK_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
log_success "Build successful! Size: $APK_SIZE"

# 2. GIT PUSH
log_info "Pushing to Git..."

if [ -n "$GIT_EMAIL" ] && [ -n "$GIT_NAME" ]; then
    git config user.email "$GIT_EMAIL"
    git config user.name "$GIT_NAME"
fi

git add .
git commit -m "build: TXAMusic-$VERSION_NAME-$BUILD_TYPE" || true

BRANCH=$(git rev-parse --abbrev-ref HEAD)
git push origin "$BRANCH:main" --force && log_success "Pushed to main branch from: $BRANCH" || log_error "Git push failed (continuing...)"

# 3. GITHUB RELEASE
log_info "Uploading to GitHub Release..."

# Check gh CLI
if ! command -v gh &> /dev/null; then
    log_error "GitHub CLI not installed."
    log_info "Install: curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg"
    exit 1
fi

if ! gh auth status &> /dev/null; then
    log_error "GitHub CLI not authenticated. Run: gh auth login"
    exit 1
fi

TAG="v$VERSION_NAME"
TITLE="TXA Music v$VERSION_NAME"
NOTES="TXA Music v$VERSION_NAME

- Build Type: $BUILD_TYPE
- Version Code: $VERSION_CODE
- Built: $(date '+%Y-%m-%d %H:%M')

Features:
- Music Player with Media3
- OTA Translation System
- Auto Update System"

# Create or update release
if gh release view "$TAG" &> /dev/null; then
    log_info "Updating release: $TAG"
    gh release edit "$TAG" --title "$TITLE" --notes "$NOTES"
else
    log_info "Creating release: $TAG"
    gh release create "$TAG" --title "$TITLE" --notes "$NOTES"
fi

# Upload APK
APK_NAME=$(basename "$OUTPUT_FILE")
gh release upload "$TAG" "$OUTPUT_FILE" --clobber

if [ $? -eq 0 ]; then
    log_success "Uploaded: $APK_NAME"
    log_info "Download: https://github.com/TXAVLOG/PROJECT/releases/download/$TAG/$APK_NAME"
else
    log_error "Upload failed!"
    exit 1
fi

log_success "=== BUILD COMPLETE ==="
