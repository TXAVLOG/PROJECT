#!/bin/bash
# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Music - GitHub Release Upload Script
# 
# Features:
# - Auto-read version from version.properties
# - Extract changelog text from CHANGELOG.html
# - Create GitHub Release with description
# - Upload APK as release asset

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$SCRIPT_DIR/.env"
VERSION_FILE="$PROJECT_ROOT/version.properties"
CHANGELOG_FILE="$PROJECT_ROOT/app/src/main/assets/CHANGELOG.html"
BUILD_OUTPUT_DIR="$PROJECT_ROOT/TXABUILD"

# Load environment
if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
fi

# Get version info
VERSION_CODE=$(grep "versionCode" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')
VERSION_NAME=$(grep "versionName" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')

log_info "Preparing GitHub Release for v$VERSION_NAME"

# Function to extract text from CHANGELOG.html
extract_changelog_text() {
    if [ ! -f "$CHANGELOG_FILE" ]; then
        log_warning "CHANGELOG.html not found, using default message"
        echo "## TXA Music v$VERSION_NAME

Cập nhật phiên bản mới.

### Features
- Bug fixes and improvements
"
        return
    fi

    log_info "Extracting changelog from: $CHANGELOG_FILE"
    
    # Extract text content from HTML, removing style tags and HTML tags
    # Convert to Markdown format for GitHub Release
    
    local changelog_md=""
    
    # Read the HTML file and convert to markdown
    changelog_md+="## 📱 TXA Music v$VERSION_NAME"
    changelog_md+=$'\n\n'
    
    # Extract sections using sed/grep
    # Get "Có gì mới?" section
    local new_features=$(cat "$CHANGELOG_FILE" | \
        sed -n '/<div class="section new">/,/<\/div>/p' | \
        sed 's/<[^>]*>//g' | \
        sed 's/✨ Có gì mới?/### ✨ Có gì mới?/' | \
        grep -v "^$" | \
        sed 's/^[[:space:]]*//' | \
        tail -n +2)
    
    # Get "Sửa lỗi" section  
    local bug_fixes=$(cat "$CHANGELOG_FILE" | \
        sed -n '/<div class="section fix">/,/<\/div>/p' | \
        sed 's/<[^>]*>//g' | \
        sed 's/🐛 Sửa lỗi/### 🐛 Sửa lỗi/' | \
        grep -v "^$" | \
        sed 's/^[[:space:]]*//' | \
        tail -n +2)
    
    # Get "Cải thiện" section
    local improvements=$(cat "$CHANGELOG_FILE" | \
        sed -n '/<div class="section perf">/,/<\/div>/p' | \
        sed 's/<[^>]*>//g' | \
        sed 's/⚡ Cải thiện hiệu suất/### ⚡ Cải thiện hiệu suất/' | \
        grep -v "^$" | \
        sed 's/^[[:space:]]*//' | \
        tail -n +2)
    
    # Build the markdown
    if [ -n "$new_features" ]; then
        changelog_md+="### ✨ Có gì mới?"
        changelog_md+=$'\n'
        echo "$new_features" | while read -r line; do
            if [ -n "$line" ]; then
                changelog_md+="- $line"
                changelog_md+=$'\n'
            fi
        done
        changelog_md+=$'\n'
    fi
    
    if [ -n "$bug_fixes" ]; then
        changelog_md+="### 🐛 Sửa lỗi"  
        changelog_md+=$'\n'
        echo "$bug_fixes" | while read -r line; do
            if [ -n "$line" ]; then
                changelog_md+="- $line"
                changelog_md+=$'\n'
            fi
        done
        changelog_md+=$'\n'
    fi
    
    if [ -n "$improvements" ]; then
        changelog_md+="### ⚡ Cải thiện hiệu suất"
        changelog_md+=$'\n'
        echo "$improvements" | while read -r line; do
            if [ -n "$line" ]; then
                changelog_md+="- $line"
                changelog_md+=$'\n'
            fi
        done
    fi
    
    # Fallback: Simple extraction if complex parsing fails
    if [ -z "$changelog_md" ] || [ ${#changelog_md} -lt 100 ]; then
        changelog_md="## 📱 TXA Music v$VERSION_NAME"
        changelog_md+=$'\n\n'
        
        # Simple extraction: Get all <li> content
        local items=$(cat "$CHANGELOG_FILE" | grep -oP '(?<=<li>).*?(?=</li>)' | head -20)
        
        if [ -n "$items" ]; then
            changelog_md+="### Các thay đổi:"
            changelog_md+=$'\n'
            echo "$items" | while read -r item; do
                if [ -n "$item" ]; then
                    changelog_md+="- $item"
                    changelog_md+=$'\n'
                fi
            done
        else
            changelog_md+="Cập nhật phiên bản mới với nhiều cải tiến và sửa lỗi."
        fi
    fi
    
    echo "$changelog_md"
}

# Function to generate release notes file
generate_release_notes() {
    local notes_file="$BUILD_OUTPUT_DIR/RELEASE_NOTES.md"
    
    log_info "Generating release notes: $notes_file"
    
    extract_changelog_text > "$notes_file"
    
    # Append build info
    echo "" >> "$notes_file"
    echo "---" >> "$notes_file"
    echo "**Build Info:**" >> "$notes_file"
    echo "- Version Code: $VERSION_CODE" >> "$notes_file"
    echo "- Build Date: $(date '+%Y-%m-%d %H:%M:%S')" >> "$notes_file"
    echo "- Git Commit: $(git rev-parse --short HEAD 2>/dev/null || echo 'N/A')" >> "$notes_file"
    
    log_success "Release notes generated"
    cat "$notes_file"
}

# Function to create GitHub Release using gh CLI
create_github_release() {
    local tag_name="v${VERSION_NAME}"
    local release_title="TXA Music $VERSION_NAME"
    local notes_file="$BUILD_OUTPUT_DIR/RELEASE_NOTES.md"
    local apk_file="$BUILD_OUTPUT_DIR/TXAMusic-${VERSION_NAME}-${BUILD_TYPE:-debug}.apk"
    
    # Check if gh CLI is available
    if ! command -v gh &> /dev/null; then
        log_warning "GitHub CLI (gh) not installed"
        log_info "To upload to GitHub Releases, install gh CLI:"
        log_info "  sudo apt install gh"
        log_info "  gh auth login"
        return 0
    fi
    
    # Check if APK exists
    if [ ! -f "$apk_file" ]; then
        log_error "APK file not found: $apk_file"
        return 1
    fi
    
    log_info "Creating GitHub Release: $tag_name"
    
    # Delete existing tag if exists (for re-release)
    git tag -d "$tag_name" 2>/dev/null || true
    git push origin --delete "$tag_name" 2>/dev/null || true
    
    # Create new tag
    git tag -a "$tag_name" -m "Release $VERSION_NAME"
    git push origin "$tag_name"
    
    # Create release with APK
    gh release create "$tag_name" \
        --title "$release_title" \
        --notes-file "$notes_file" \
        "$apk_file" \
        --latest
    
    if [ $? -eq 0 ]; then
        log_success "GitHub Release created successfully!"
        log_info "URL: https://github.com/$(git remote get-url origin | sed 's/.*github.com[:/]\(.*\)\.git/\1/')/releases/tag/$tag_name"
    else
        log_error "Failed to create GitHub Release"
        return 1
    fi
}

# Main execution
main() {
    log_info "=== TXA GitHub Release Upload ==="
    
    # Generate release notes from CHANGELOG.html
    generate_release_notes
    
    # Create GitHub Release if enabled
    if [ "$UPLOAD_TO_GITHUB" = "true" ]; then
        create_github_release
    else
        log_info "GitHub Release upload disabled (set UPLOAD_TO_GITHUB=true to enable)"
    fi
    
    log_success "=== Upload Process Complete ==="
}

main "$@"
