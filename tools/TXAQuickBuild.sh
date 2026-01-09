#!/bin/bash
# FILE BY TXA - Contact: fb.com/vlog.txa.2311
# TXA Music - Linux/VPS Quick Build + GitHub Release
# Usage: ./TXAQuickBuild.sh [--release]
# 
# Features:
# - Auto-read CHANGELOG.html for release notes
# - Dynamic version from version.properties
# - GitHub Release with full description

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }

# Parse args
BUILD_TYPE="debug"
[[ "$1" == "--release" || "$1" == "-r" ]] && BUILD_TYPE="release"

# Config
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$SCRIPT_DIR/.env"
CHANGELOG_FILE="$PROJECT_ROOT/app/src/main/assets/CHANGELOG.html"

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

# 2. Extract changelog from HTML
extract_changelog() {
    if [ ! -f "$CHANGELOG_FILE" ]; then
        log_warning "CHANGELOG.html not found, using default"
        echo "TXA Music v$VERSION_NAME - Cập nhật phiên bản mới."
        return
    fi
    
    log_info "Extracting changelog from CHANGELOG.html..."
    
    # Extract <li> items from HTML
    local items=$(grep -oP '(?<=<li>)[^<]+' "$CHANGELOG_FILE" 2>/dev/null | head -15)
    
    if [ -n "$items" ]; then
        echo "$items"
    else
        # Fallback: Remove all HTML tags
        cat "$CHANGELOG_FILE" | sed 's/<[^>]*>//g' | grep -v "^$" | head -20
    fi
}

# Generate release notes
generate_release_notes() {
    local notes=""
    notes+="## 📱 TXA Music v$VERSION_NAME"$'\n\n'
    
    # Add changelog items
    local changelog=$(extract_changelog)
    if [ -n "$changelog" ]; then
        notes+="### 📝 Changelog:"$'\n'
        while IFS= read -r line; do
            # Clean up the line
            line=$(echo "$line" | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//')
            if [ -n "$line" ]; then
                notes+="- $line"$'\n'
            fi
        done <<< "$changelog"
        notes+=$'\n'
    fi
    
    # Add build info
    notes+="### 📦 Build Info:"$'\n'
    notes+="- Build Type: \`$BUILD_TYPE\`"$'\n'
    notes+="- Version Code: \`$VERSION_CODE\`"$'\n'
    notes+="- Build Date: \`$(date '+%Y-%m-%d %H:%M')\`"$'\n'
    notes+="- Git Commit: \`$(git rev-parse --short HEAD 2>/dev/null || echo 'N/A')\`"$'\n'
    
    echo "$notes"
}

RELEASE_NOTES=$(generate_release_notes)
log_info "Generated release notes:"
echo "$RELEASE_NOTES"

# Save to file for reference
echo "$RELEASE_NOTES" > "$BUILD_DIR/RELEASE_NOTES.md"

# 3. GIT PUSH
log_info "Pushing to Git..."

if [ -n "$GIT_EMAIL" ] && [ -n "$GIT_NAME" ]; then
    git config user.email "$GIT_EMAIL"
    git config user.name "$GIT_NAME"
fi

git add .
git commit -m "build: TXAMusic-$VERSION_NAME-$BUILD_TYPE" || true

BRANCH=$(git rev-parse --abbrev-ref HEAD)
git push origin "$BRANCH:main" --force && log_success "Pushed to main branch from: $BRANCH" || log_error "Git push failed (continuing...)"

# 4. GITHUB RELEASE
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

# Create or update release with dynamic notes
if gh release view "$TAG" &> /dev/null; then
    log_info "Updating release: $TAG"
    gh release edit "$TAG" --title "$TITLE" --notes "$RELEASE_NOTES"
else
    log_info "Creating release: $TAG"
    gh release create "$TAG" --title "$TITLE" --notes "$RELEASE_NOTES"
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
