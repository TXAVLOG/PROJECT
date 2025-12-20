# 🚀 TXA Demo - Cross-Platform Build System
**Windows + Ubuntu VPS Support**

**FILE BY TXA**  
**Contact**: https://fb.com/vlog.txa.2311

## 📋 Overview

Complete automated build system for TXA Demo Android app on Ubuntu VPS. Features keystore management, process cleanup, APK validation, and repository integration.

## 📁 File Structure

```
build/
├── README.md                    # This documentation
├── .env.example                 # Environment template
├── .env                         # Environment variables (gitignored)
│
├── Ubuntu Scripts (.sh)
├── TXASetupEnvironment.sh       # Ubuntu environment setup
├── TXABuild.sh                  # Ubuntu full build script
├── TXAQuickBuild.sh             # Ubuntu quick build
├── TXAUploadToGitHub.sh         # GitHub Releases upload
└── TXABackupKeystore.sh         # Keystore backup
│
└── Windows Scripts (.ps1)
├── TXABuild.ps1                 # Windows full build script
├── TXAQuickBuild.ps1            # Windows quick build
├── TXAUploadToGitHub.ps1        # GitHub Releases upload
└── TXABackupKeystore.ps1        # Keystore backup
```

## 🛠️ Setup Instructions

### Platform Detection
- **Ubuntu/Mac**: Use `.sh` scripts
- **Windows**: Use `.ps1` scripts

### 1. Ubuntu VPS Setup (First Time Only)

```bash
# Clone repository
git clone https://github.com/TXAVLOG/PROJECT.git
cd PROJECT

# Setup VPS environment
chmod +x build/TXASetupEnvironment.sh
./build/TXASetupEnvironment.sh

# Reload environment
source ~/.bashrc
```

### 2. Windows Setup (First Time Only)

```powershell
# Clone repository
git clone https://github.com/TXAVLOG/PROJECT.git
cd PROJECT

# Install required tools
winget install GitHub.cli      # GitHub CLI
winget install GnuPG.Gpg4win   # GPG for encryption
winget install OpenJDK.11      # Java 11

# Set PowerShell execution policy
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser

# Verify installations
java -version
gh --version
gpg --version
```

### 3. Configure Build Environment

```bash
# Copy environment template (Ubuntu)
cp build/.env.example build/.env

# Copy environment template (Windows)
copy build\.env.example build\.env

# Edit with your values
nano build/.env          # Ubuntu
notepad build\.env       # Windows
```

**Required .env values:**
```bash
KEYSTORE_PASSWORD=your_secure_keystore_password
KEY_PASSWORD=your_secure_key_password
GIT_EMAIL="viptretrauc@gmail.com"
GIT_NAME="TXAVLOG"
BUILD_TYPE=release          # debug or release
CLEAN_BUILD=true           # true or false
UPLOAD_TO_REPO=true        # true or false
```

### 3. Make Scripts Executable

```bash
chmod +x build/*.sh
```

## 🚀 Usage

### Quick Build (Recommended)

**Ubuntu:**
```bash
# Debug build (default)
./build/TXAQuickBuild.sh

# Release build
./build/TXAQuickBuild.sh release
```

**Windows:**
```powershell
# Debug build (default)
.\build\TXAQuickBuild.ps1

# Release build
.\build\TXAQuickBuild.ps1 -BuildType release
```

### Full Build with All Features

**Ubuntu:**
```bash
./build/TXABuild.sh
```

**Windows:**
```powershell
.\build\TXABuild.ps1
```

## 📦 Output

- **APK Location**: `TXABUILD/TXADEMO-{version}-{type}.apk`
- **Keystore**: `app/txademo.keystore` (auto-generated)
- **Repository Upload**: Automatic if `UPLOAD_TO_REPO=true`

## 🔧 Features

### ✅ Security
- Keystore passwords in `.env` (gitignored)
- Automatic keystore generation (alias: `txademo`)
- Git configuration from environment variables
- Process cleanup with graceful shutdown

### ✅ Build Management
- Kills old Java/Gradle processes
- Cleans Gradle cache
- Disk space validation (5GB minimum)
- APK validation (size + format)
- Version auto-detection from `version.properties`

### ✅ Error Handling
- Immediate stop on any failure (`set -e`)
- Detailed error messages
- Build validation
- Process cleanup on interruption

### ✅ Repository Integration
- Automatic git configuration
- APK commit and push to repository
- Version-based commit messages

## 📊 Build Process Flow

```
1. Environment Validation
2. Process Cleanup (Java/Gradle)
3. Gradle Cache Cleaning
4. Keystore Generation (if needed)
5. Git Configuration
6. APK Building (debug/release)
7. APK Validation
8. Copy to TXABUILD folder
9. Repository Upload
10. Success Summary
```

## 🐛 Troubleshooting

### Common Issues

**Permission Denied**
```bash
chmod +x build/*.sh
```

**Gradle Daemon Issues**
```bash
./gradlew --stop
rm -rf ~/.gradle/caches
```

**Android SDK Not Found**
```bash
export ANDROID_HOME=$HOME/Android/sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

**Insufficient Disk Space**
```bash
df -h  # Check available space
./gradlew clean  # Clean build cache
```

### Debug Mode

Enable verbose logging:
```bash
export DEBUG=true
./build/TXABuild.sh
```

### Manual Keystore Creation

If auto-generation fails:
```bash
keytool -genkey \
    -v -keystore app/txademo.keystore \
    -alias txademo -keyalg RSA \
    -keysize 2048 -validity 10000 \
    -storepass YOUR_PASSWORD \
    -keypass YOUR_PASSWORD \
    -dname "CN=TXA Demo, OU=TXA, O=TXAVLOG, L=Ho Chi Minh, ST=HCM, C=VN"
```

## 🔒 Security Notes

- ⚠️ **Never commit** `.env` file or keystore passwords
- ⚠️ **Never commit** `*.keystore` files
- ⚠️ Use strong, unique passwords for keystore
- ⚠️ Consider using GitHub Releases instead of git for APK distribution
- ⚠️ Regularly rotate keystore passwords

## 📈 Performance Optimization

### For Faster Builds
```bash
# Set in .env
CLEAN_BUILD=false  # Skip full clean
BUILD_TYPE=debug   # Faster than release
```

### For Clean Builds
```bash
# Full clean before build
rm -rf ~/.gradle/caches
./gradlew clean
```

## 🌐 Repository Management

### APK Files in Git
- **Pros**: Simple versioning
- **Cons**: Repository bloat, history size

### Alternative: GitHub Releases
```bash
# Create release and upload APK
gh release create v1.0.0 TXABUILD/*.apk
```

## 📝 Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `KEYSTORE_PASSWORD` | ✅ | Keystore password |
| `KEY_PASSWORD` | ✅ | Key password |
| `GIT_EMAIL` | ✅ | Git commit email |
| `GIT_NAME` | ✅ | Git commit name |
| `BUILD_TYPE` | ❌ | `debug` or `release` |
| `CLEAN_BUILD` | ❌ | `true` or `false` |
| `UPLOAD_TO_REPO` | ❌ | `true` or `false` |

## 🚨 Error Codes

- **Exit 1**: General failure (missing files, invalid config)
- **Exit 2**: Build failure (Gradle errors)
- **Exit 3**: Validation failure (APK corruption)
- **Exit 130**: Interrupted by user (Ctrl+C)

## 📞 Support

**Contact**: TXAVLOG  
**Facebook**: https://fb.com/vlog.txa.2311  
**GitHub**: https://github.com/TXAVLOG/PROJECT

---

## 🔄 Version History

- **v1.0**: Initial build system
- **v1.1**: Added security fixes, APK validation
- **v1.2**: Improved process management, error handling

**Last Updated**: 2024-12-20  
**Compatible**: Ubuntu 18.04+, Android SDK 28+
