#!/bin/bash
# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Music - VPS Ubuntu Build Script
# 
# Features:
# - Auto-generate keystore if not exists
# - Kill old Java/Gradle processes
# - Clean Gradle cache
# - Build debug/release APK
# - Copy to TXABUILD folder
# - Upload to repository
# - Error handling with immediate stop on failure

set -e  # Exit immediately if any command fails

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$SCRIPT_DIR/.env"
KEYSTORE_FILE="$PROJECT_ROOT/app/txamusic.keystore"
BUILD_OUTPUT_DIR="$PROJECT_ROOT/TXABUILD"

# Load environment variables
if [ ! -f "$ENV_FILE" ]; then
    log_error "Environment file not found: $ENV_FILE"
    log_error "Please copy .env.example to .env and update values"
    exit 1
fi

source "$ENV_FILE"

# Validate required environment variables
required_vars=("KEYSTORE_PASSWORD" "KEY_PASSWORD" "GIT_EMAIL" "GIT_NAME")
for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        log_error "Required environment variable not set: $var"
        exit 1
    fi
done

# Get version from version.properties
VERSION_FILE="$PROJECT_ROOT/version.properties"
if [ ! -f "$VERSION_FILE" ]; then
    log_error "Version file not found: $VERSION_FILE"
    exit 1
fi

VERSION_CODE=$(grep "versionCode" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')
VERSION_NAME=$(grep "versionName" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' ')

if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
    log_error "Failed to read version from $VERSION_FILE"
    exit 1
fi

log_info "Building TXA Music v$VERSION_NAME ($VERSION_CODE)"

# Function to kill processes
kill_processes() {
    log_info "Stopping Gradle daemon gracefully..."
    cd "$PROJECT_ROOT"
    ./gradlew --stop || true
    
    log_info "Waiting for processes to stop..."
    sleep 2
    
    log_info "Force stopping remaining Gradle processes..."
    pkill -15 gradle || true
    sleep 1
    pkill -9 gradle || true
    
    log_info "Force stopping remaining Java processes..."
    pkill -15 java || true
    sleep 1
    pkill -9 java || true
}

# Function to check disk space
check_disk_space() {
    local required_space_gb=5
    local available_space=$(df / | awk 'NR==2 {print int($4/1024/1024)}')
    
    if [ "$available_space" -lt "$required_space_gb" ]; then
        log_error "Insufficient disk space. Required: ${required_space_gb}GB, Available: ${available_space}GB"
        exit 1
    fi
    
    log_info "Disk space check passed: ${available_space}GB available"
}

# Function to validate APK
validate_apk() {
    local apk_file="$1"
    
    if [ ! -f "$apk_file" ]; then
        log_error "APK file not found: $apk_file"
        exit 1
    fi
    
    # Check file size (should be at least 1MB)
    local file_size=$(stat -c%s "$apk_file")
    local min_size=1048576  # 1MB in bytes
    
    if [ "$file_size" -lt "$min_size" ]; then
        log_error "APK file too small: $file_size bytes (minimum: $min_size bytes)"
        exit 1
    fi
    
    # Check if it's a valid APK (ZIP archive)
    if ! file "$apk_file" | grep -q "Zip archive"; then
        log_error "APK file is not a valid ZIP archive"
        exit 1
    fi
    
    log_success "APK validation passed: $(du -h "$apk_file" | cut -f1)"
}

# Function to clean Gradle cache
clean_gradle() {
    log_info "Cleaning Gradle cache..."
    cd "$PROJECT_ROOT"
    
    # Clean project
    if [ "$CLEAN_BUILD" = "true" ]; then
        log_info "Performing clean build..."
        ./gradlew clean
    fi
    
    # Clean Gradle cache
    rm -rf ~/.gradle/caches
    rm -rf "$PROJECT_ROOT/.gradle"
    rm -rf "$PROJECT_ROOT/build"
    rm -rf "$PROJECT_ROOT/app/build"
    
    log_success "Gradle cache cleaned"
}

# Function to verify keystore alias
verify_keystore() {
    if [ -f "$KEYSTORE_FILE" ]; then
        log_info "Verifying keystore alias..."
        
        # Check if keystore contains the correct alias
        if keytool -list -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" -alias txamusic &> /dev/null; then
            log_success "Keystore alias verified: txamusic"
        else
            log_error "Keystore alias 'txamusic' not found or invalid password"
            log_error "Please check keystore file or regenerate with correct alias"
            exit 1
        fi
    fi
}

# Function to generate keystore
generate_keystore() {
    if [ ! -f "$KEYSTORE_FILE" ]; then
        log_info "Generating keystore: $KEYSTORE_FILE"
        
        keytool -genkey \
            -v \
            -keystore "$KEYSTORE_FILE" \
            -alias txamusic \
            -keyalg RSA \
            -keysize 2048 \
            -validity 10000 \
            -storepass "$KEYSTORE_PASSWORD" \
            -keypass "$KEY_PASSWORD" \
            -dname "CN=TXA Music, OU=TXA, O=TXAVLOG, L=Ho Chi Minh, ST=HCM, C=VN" \
            -noprompt
        
        if [ $? -eq 0 ]; then
            log_success "Keystore generated successfully"
            
            # Create backup immediately after generation
            log_info "Creating initial keystore backup..."
            "$SCRIPT_DIR/TXABackupKeystore.sh"
        else
            log_error "Failed to generate keystore"
            exit 1
        fi
    else
        log_info "Keystore already exists: $KEYSTORE_FILE"
    fi
    
    # Verify keystore alias
    verify_keystore
}

# Function to configure git
configure_git() {
    log_info "Configuring Git..."
    cd "$PROJECT_ROOT"
    
    git config user.email "$GIT_EMAIL"
    git config user.name "$GIT_NAME"
    
    log_success "Git configured"
}

# Function to build APK
build_apk() {
    log_info "Building $BUILD_TYPE APK..."
    cd "$PROJECT_ROOT"
    
    case "$BUILD_TYPE" in
        "debug")
            ./gradlew assembleDebug
            APK_FILE="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
            ;;
        "release")
            ./gradlew assembleRelease
            APK_FILE="$PROJECT_ROOT/app/build/outputs/apk/release/app-release.apk"
            ;;
        *)
            log_error "Invalid build type: $BUILD_TYPE"
            exit 1
            ;;
    esac
    
    if [ ! -f "$APK_FILE" ]; then
        log_error "APK file not found: $APK_FILE"
        log_error "Build may have failed"
        exit 1
    fi
    
    # Validate the built APK
    validate_apk "$APK_FILE"
    
    log_success "APK built and validated: $APK_FILE"
}

# Function to copy APK to output directory
copy_apk() {
    log_info "Creating output directory..."
    mkdir -p "$BUILD_OUTPUT_DIR"
    
    OUTPUT_FILE="$BUILD_OUTPUT_DIR/TXAMusic-$VERSION_NAME-$BUILD_TYPE.apk"
    
    log_info "Copying APK to: $OUTPUT_FILE"
    cp "$APK_FILE" "$OUTPUT_FILE"
    
    if [ $? -eq 0 ]; then
        log_success "APK copied to: $OUTPUT_FILE"
    else
        log_error "Failed to copy APK"
        exit 1
    fi
}

# Function to upload to repository
upload_to_repo() {
    if [ "$UPLOAD_TO_REPO" != "true" ]; then
        log_info "Skipping git repository upload"
    else
        log_info "Uploading to git repository..."
        cd "$PROJECT_ROOT"
        
        # Add APK files
        git add TXABUILD/*.apk
        
        # Check if there are changes to commit
        if git diff --cached --quiet; then
            log_info "No changes to commit"
        else
            git commit -m "build: TXAMusic-$VERSION_NAME-$BUILD_TYPE"
            
            # Push to remote
            git push origin main
            
            if [ $? -eq 0 ]; then
                log_success "Uploaded to git repository"
            else
                log_error "Failed to upload to git repository"
                exit 1
            fi
        fi
    fi
    
    # Upload to GitHub Releases
    if [ "$UPLOAD_TO_GITHUB" = "true" ]; then
        log_info "Uploading to GitHub Releases..."
        "$SCRIPT_DIR/TXAUploadToGitHub.sh"
    fi
}

# Function to display build summary
build_summary() {
    log_success "=== BUILD COMPLETED ==="
    log_info "Version: $VERSION_NAME ($VERSION_CODE)"
    log_info "Type: $BUILD_TYPE"
    log_info "Output: $OUTPUT_FILE"
    log_info "Size: $(du -h "$OUTPUT_FILE" | cut -f1)"
    
    if [ "$UPLOAD_TO_REPO" = "true" ]; then
        log_info "Repository: Updated"
    fi
    
    log_success "======================"
}

# Pre-flight checks
check_prerequisites() {
    log_info "Running pre-flight checks..."
    
    # Check Java
    if ! command -v java &> /dev/null; then
        log_error "✗ Java not installed or not in PATH"
        log_error "Please install Java 11 or higher"
        exit 1
    fi
    log_success "✓ Java found: $(java -version 2>&1 | head -n 1)"
    
    # Check Gradle wrapper
    if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
        log_error "✗ Gradle wrapper not found: $PROJECT_ROOT/gradlew"
        exit 1
    fi
    log_success "✓ Gradle wrapper found"
    
    # Check Git
    if ! command -v git &> /dev/null; then
        log_error "✗ Git not installed or not in PATH"
        exit 1
    fi
    log_success "✓ Git found: $(git --version)"
    
    log_success "All pre-flight checks passed"
}

# Main execution
main() {
    log_info "Starting TXA Music build process..."
    
    # Check if we're in the right directory
    if [ ! -f "$PROJECT_ROOT/build.gradle.kts" ]; then
        log_error "Not in a valid Android project directory"
        exit 1
    fi
    
    # Execute build steps
    check_prerequisites
    check_disk_space
    kill_processes
    clean_gradle
    generate_keystore
    configure_git
    build_apk
    copy_apk
    upload_to_repo
    build_summary
    
    log_success "Build process completed successfully!"
}

# Trap to handle script interruption
trap 'log_error "Build process interrupted"; exit 1' INT TERM

# Run main function
main "$@"
