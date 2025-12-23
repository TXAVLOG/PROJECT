# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Music - Windows PowerShell Keystore Backup Script

# Error handling
$ErrorActionPreference = "Stop"

# Colors for output
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Output $args
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

function Write-Success { Write-ColorOutput Green $args }
function Write-Info { Write-ColorOutput Cyan $args }
function Write-Warning { Write-ColorOutput Yellow $args }
function Write-Error { Write-ColorOutput Red $args }

# Configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$KeyStoreFile = Join-Path $ProjectRoot "app\txamusic.keystore"
$BackupDir = Join-Path $ProjectRoot "keystore-backups"
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$BackupFile = Join-Path $BackupDir "txamusic_keystore_$Timestamp.tar.gz.gpg"

# Load environment
$EnvFile = Join-Path $ScriptDir ".env"
if (-not (Test-Path $EnvFile)) {
    Write-Error "Environment file not found: $EnvFile"
    exit 1
}

# Read environment variables
$EnvContent = Get-Content $EnvFile | Where-Object { $_ -match "^(?!#).*=" }
foreach ($line in $EnvContent) {
    if ($line -match "^([^=]+)=(.*)$") {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
    }
}

# Check if GPG is installed
try {
    & gpg --version > $null 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "GPG not found"
    }
} catch {
    Write-Error "GPG not installed"
    Write-Info "Install with: choco install gpg4win"
    exit 1
}

# Check if keystore exists
if (-not (Test-Path $KeyStoreFile)) {
    Write-Error "Keystore file not found: $KeyStoreFile"
    Write-Info "Generate keystore first with: .\TXABuild.ps1"
    exit 1
}

# Function to create encrypted backup
function New-KeystoreBackup {
    Write-Info "Creating encrypted keystore backup..."
    
    # Create backup directory
    if (-not (Test-Path $BackupDir)) {
        New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    }
    
    # Create temporary archive
    $TempArchive = Join-Path $env:TEMP "txakeystore_$Timestamp.tar"
    
    # Create archive with keystore and metadata
    $VersionFile = Join-Path $ProjectRoot "version.properties"
    $EnvExampleFile = Join-Path $ScriptDir ".env.example"
    
    & tar -cf $TempArchive `
        -C $ProjectRoot `
        "app\txamusic.keystore" `
        "version.properties" `
        "build\.env.example"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to create archive"
        exit 1
    }
    
    # Add backup metadata
    $BackupInfo = @"
=== TXA Music Keystore Backup ===
Created: $(Get-Date)
Version: $((Get-Content $VersionFile | Where-Object { $_ -match "^versionName=" }) -replace "versionName=", "")
Build Code: $((Get-Content $VersionFile | Where-Object { $_ -match "^versionCode=" }) -replace "versionCode=", "")
Keystore Alias: txamusic
=================================
"@
    
    $BackupInfo | Out-File -FilePath "$env:TEMP\backup_info_$Timestamp.txt" -Encoding UTF8
    
    # Add metadata to archive
    & tar -rf $TempArchive -C $env:TEMP "backup_info_$Timestamp.txt"
    
    # Encrypt with GPG
    Write-Info "Encrypting backup with GPG..."
    $KeyStorePassword = [System.Environment]::GetEnvironmentVariable("KEYSTORE_PASSWORD")
    
    $KeyStorePassword | & gpg --batch --yes --passphrase-fd 0 `
        --symmetric --cipher-algo AES256 `
        --output $BackupFile $TempArchive
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to encrypt backup"
        exit 1
    }
    
    # Clean up temporary files
    Remove-Item $TempArchive -Force -ErrorAction SilentlyContinue
    Remove-Item "$env:TEMP\backup_info_$Timestamp.txt" -Force -ErrorAction SilentlyContinue
    
    if (Test-Path $BackupFile) {
        Write-Success "Encrypted backup created: $BackupFile"
        Write-Info "Backup size: $([math]::Round((Get-Item $BackupFile).Length/1MB, 2)) MB"
    } else {
        Write-Error "Failed to create encrypted backup"
        exit 1
    }
}

# Function to verify backup
function Test-Backup {
    Write-Info "Verifying backup integrity..."
    
    # Test decryption (without extracting)
    $KeyStorePassword = [System.Environment]::GetEnvironmentVariable("KEYSTORE_PASSWORD")
    
    $KeyStorePassword | & gpg --batch --yes --passphrase-fd 0 `
        --decrypt --quiet $BackupFile > $null 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Backup verification passed"
    } else {
        Write-Error "Backup verification failed"
        exit 1
    }
}

# Function to show backup info
function Show-BackupInfo {
    Write-Info "Backup Information:"
    Write-Info "  File: $BackupFile"
    Write-Info "  Size: $([math]::Round((Get-Item $BackupFile).Length/1MB, 2)) MB"
    Write-Info "  Created: $(Get-Date)"
    Write-Info "  Keystore: $KeyStoreFile"
    Write-Warning "IMPORTANT: Store this backup securely!"
    Write-Warning "You need the keystore password AND this backup file to restore."
    Write-Info ""
    Write-Info "To restore:"
    Write-Info "  1. Install GPG: choco install gpg4win"
    Write-Info "  2. Decrypt: gpg --output backup.tar.gz --decrypt $BackupFile"
    Write-Info "  3. Extract: tar -xzf backup.tar.gz"
    Write-Info ""
}

# Function to cleanup old backups (keep last 5)
function Remove-OldBackups {
    Write-Info "Cleaning up old backups (keeping last 5)..."
    
    Set-Location $BackupDir
    $Backups = Get-ChildItem -Path "txamusic_keystore_*.tar.gz.gpg" | Sort-Object LastWriteTime -Descending
    
    if ($Backups.Count -gt 5) {
        $Backups | Select-Object -Skip 5 | ForEach-Object {
            Remove-Item $_.FullName -Force
            Write-Info "Removed: $($_.Name)"
        }
    }
    
    $BackupCount = (Get-ChildItem -Path "*.tar.gz.gpg" | Measure-Object).Count
    Write-Info "Retained $BackupCount backup files"
}

# Main execution
try {
    Write-Info "TXA Music Keystore Backup"
    Write-Info "========================"
    
    New-KeystoreBackup
    Test-Backup
    Remove-OldBackups
    Show-BackupInfo
    
    Write-Success "Keystore backup completed successfully!"
} catch {
    Write-Error "Backup process failed: $($_.Exception.Message)"
    exit 1
}
