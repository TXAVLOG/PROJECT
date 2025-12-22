#!/bin/bash
# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Music - Keystore Backup Script
# Creates encrypted backup of keystore file

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
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
KEYSTORE_FILE="$PROJECT_ROOT/app/txamusic.keystore"
BACKUP_DIR="$PROJECT_ROOT/keystore-backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="$BACKUP_DIR/txamusic_keystore_$TIMESTAMP.tar.gz.gpg"

# Load environment
ENV_FILE="$SCRIPT_DIR/.env"
if [ ! -f "$ENV_FILE" ]; then
    log_error "Environment file not found: $ENV_FILE"
    exit 1
fi
source "$ENV_FILE"

# Check if GPG is installed
if ! command -v gpg &> /dev/null; then
    log_error "GPG not installed"
    log_info "Install with: sudo apt-get install gnupg"
    exit 1
fi

# Check if keystore exists
if [ ! -f "$KEYSTORE_FILE" ]; then
    log_error "Keystore file not found: $KEYSTORE_FILE"
    log_info "Generate keystore first with: ./TXABuild.sh"
    exit 1
fi

# Function to create encrypted backup
create_backup() {
    log_info "Creating encrypted keystore backup..."
    
    # Create backup directory
    mkdir -p "$BACKUP_DIR"
    
    # Create temporary archive
    local temp_archive="/tmp/txakeystore_$TIMESTAMP.tar"
    
    # Create archive with keystore and metadata
    tar -cf "$temp_archive" \
        -C "$PROJECT_ROOT" \
        app/txamusic.keystore \
        version.properties \
        build/.env.example
    
    # Add backup metadata
    echo "=== TXA Music Keystore Backup ===" > "/tmp/backup_info_$TIMESTAMP.txt"
    echo "Created: $(date)" >> "/tmp/backup_info_$TIMESTAMP.txt"
    echo "Version: $(grep 'versionName' "$PROJECT_ROOT/version.properties" | cut -d'=' -f2 | tr -d ' ')" >> "/tmp/backup_info_$TIMESTAMP.txt"
    echo "Build Code: $(grep 'versionCode' "$PROJECT_ROOT/version.properties" | cut -d'=' -f2 | tr -d ' ')" >> "/tmp/backup_info_$TIMESTAMP.txt"
    echo "Keystore Alias: txamusic" >> "/tmp/backup_info_$TIMESTAMP.txt"
    echo "=================================" >> "/tmp/backup_info_$TIMESTAMP.txt"
    
    # Add metadata to archive
    tar -rf "$temp_archive" -C "/tmp" "backup_info_$TIMESTAMP.txt"
    
    # Encrypt with GPG (using symmetric encryption)
    log_info "Encrypting backup with GPG..."
    echo "$KEYSTORE_PASSWORD" | gpg --batch --yes --passphrase-fd 0 \
        --symmetric --cipher-algo AES256 \
        --output "$BACKUP_FILE" "$temp_archive"
    
    # Clean up temporary files
    rm -f "$temp_archive" "/tmp/backup_info_$TIMESTAMP.txt"
    
    if [ $? -eq 0 ]; then
        log_success "Encrypted backup created: $BACKUP_FILE"
        log_info "Backup size: $(du -h "$BACKUP_FILE" | cut -f1)"
    else
        log_error "Failed to create encrypted backup"
        exit 1
    fi
}

# Function to verify backup
verify_backup() {
    log_info "Verifying backup integrity..."
    
    # Test decryption (without extracting)
    if echo "$KEYSTORE_PASSWORD" | gpg --batch --yes --passphrase-fd 0 \
        --decrypt --quiet "$BACKUP_FILE" > /dev/null 2>&1; then
        log_success "Backup verification passed"
    else
        log_error "Backup verification failed"
        exit 1
    fi
}

# Function to show backup info
show_backup_info() {
    log_info "Backup Information:"
    echo "  File: $BACKUP_FILE"
    echo "  Size: $(du -h "$BACKUP_FILE" | cut -f1)"
    echo "  Created: $(date)"
    echo "  Keystore: $KEYSTORE_FILE"
    echo ""
    log_warning "IMPORTANT: Store this backup securely!"
    log_warning "You need the keystore password AND this backup file to restore."
    echo ""
    log_info "To restore:"
    echo "  1. Install GPG: sudo apt-get install gnupg"
    echo "  2. Decrypt: gpg --output backup.tar.gz --decrypt $BACKUP_FILE"
    echo "  3. Extract: tar -xzf backup.tar.gz"
    echo ""
}

# Function to cleanup old backups (keep last 5)
cleanup_old_backups() {
    log_info "Cleaning up old backups (keeping last 5)..."
    
    cd "$BACKUP_DIR"
    ls -t txamusic_keystore_*.tar.gz.gpg | tail -n +6 | xargs -r rm
    
    local backup_count=$(ls -1 *.tar.gz.gpg 2>/dev/null | wc -l)
    log_info "Retained $backup_count backup files"
}

# Main execution
main() {
    log_info "TXA Music Keystore Backup"
    log_info "========================"
    
    create_backup
    verify_backup
    cleanup_old_backups
    show_backup_info
    
    log_success "Keystore backup completed successfully!"
}

# Trap to handle script interruption
trap 'log_error "Backup process interrupted"; exit 1' INT TERM

# Run main function
main "$@"
