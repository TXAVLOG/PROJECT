#!/bin/bash
# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Music - GitHub Releases Upload Script
# Alternative to committing APKs to git repository

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

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_ROOT/TXABUILD"
ENV_FILE="$SCRIPT_DIR/.env"

# Load environment
if [ ! -f "$ENV_FILE" ]; then
    log_error "Environment file not found: $ENV_FILE"
    exit 1
fi
source "$ENV_FILE"

# Get version info
VERSION_FILE="$PROJECT_ROOT/version.properties"
VERSION_NAME=$(grep "versionName" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')
VERSION_CODE=$(grep "versionCode" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')

# Check if GitHub CLI is installed
if ! command -v gh &> /dev/null; then
    log_error "GitHub CLI (gh) not installed"
    log_info "Install with: curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg"
    exit 1
fi

# Check authentication
if ! gh auth status &> /dev/null; then
    log_error "GitHub CLI not authenticated"
    log_info "Run: gh auth login"
    exit 1
fi

# Function to upload APK to GitHub Release
upload_to_github_release() {
    local apk_file="$1"
    local build_type="$2"
    
    log_info "Uploading to GitHub Release..."
    
    # Create or update release
    local release_tag="v$VERSION_NAME"
    local release_name="TXA Demo v$VERSION_NAME ($build_type)"
    local release_notes="TXA Demo v$VERSION_NAME ($build_type) build
    
Version: $VERSION_NAME
Build Code: $VERSION_CODE
Type: $build_type
Built: $(date '+%Y-%m-%d %H:%M:%S')

Features:
- OTA Translation System
- Extended URL Resolver (MediaFire, GitHub, Google Drive)
- File Manager UI
- Legacy Storage Support (Android 9)
- Multi-language Support"

    # Check if release exists
    if gh release view "$release_tag" &> /dev/null; then
        log_info "Updating existing release: $release_tag"
        gh release edit "$release_tag" --title "$release_name" --notes "$release_notes"
    else
        log_info "Creating new release: $release_tag"
        gh release create "$release_tag" --title "$release_name" --notes "$release_notes"
    fi
    
    # Upload APK
    local apk_basename=$(basename "$apk_file")
    log_info "Uploading $apk_basename..."
    
    gh release upload "$release_tag" "$apk_file" --clobber
    
    if [ $? -eq 0 ]; then
        log_success "APK uploaded to GitHub Release"
        log_info "Download URL: https://github.com/TXAVLOG/PROJECT/releases/download/$release_tag/$apk_basename"
    else
        log_error "Failed to upload APK"
        exit 1
    fi
}

# Main execution
main() {
    log_info "GitHub Release Upload for TXA Demo v$VERSION_NAME"
    
    # Find APK files
    if [ ! -d "$BUILD_DIR" ]; then
        log_error "Build directory not found: $BUILD_DIR"
        exit 1
    fi
    
    # Upload all APK files
    for apk_file in "$BUILD_DIR"/*.apk; do
        if [ -f "$apk_file" ]; then
            local filename=$(basename "$apk_file")
            local build_type=$(echo "$filename" | sed 's/.*-\(debug\|release\)\.apk/\1/')
            
            log_info "Processing: $filename ($build_type)"
            upload_to_github_release "$apk_file" "$build_type"
        fi
    done
    
    log_success "All APKs uploaded to GitHub Releases"
}

# Run main function
main "$@"
