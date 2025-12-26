#!/bin/bash
# FILE BY TXA
# Contact: https://fb.com/vlog.txa.2311
# TXA Music - VPS Environment Setup Script

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

log_info "Setting up TXA Music build environment..."

# Check if running on Ubuntu
if ! grep -q "Ubuntu" /etc/os-release; then
    log_warning "This script is optimized for Ubuntu"
fi

# Install required packages
log_info "Installing required packages..."
sudo apt-get update
sudo apt-get install -y \
    openjdk-11-jdk \
    unzip \
    wget \
    git \
    curl \
    build-essential

# Set JAVA_HOME
echo 'export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$PATH:$JAVA_HOME/bin' >> ~/.bashrc
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$PATH:$JAVA_HOME/bin

# Download and install Android SDK
log_info "Installing Android SDK..."
cd ~
wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O cmdline-tools.zip
unzip -q cmdline-tools.zip
mkdir -p ~/Android/sdk/cmdline-tools/latest
mv cmdline-tools/* ~/Android/sdk/cmdline-tools/latest/
rm -rf cmdline-tools cmdline-tools.zip

# Set Android environment variables
echo 'export ANDROID_HOME=$HOME/Android/sdk' >> ~/.bashrc
echo 'export ANDROID_SDK_ROOT=$ANDROID_HOME' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc
export ANDROID_HOME=$HOME/Android/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Accept licenses
log_info "Accepting Android SDK licenses..."
yes | sdkmanager --licenses

# Install required Android SDK components
log_info "Installing Android SDK components..."
sdkmanager "platform-tools" "platforms;android-28" "build-tools;28.0.3"

# Make scripts executable
SCRIPT_DIR="$(pwd)/PROJECT-ANDROID/buildsc"
if [ -d "$SCRIPT_DIR" ]; then
    chmod +x "$SCRIPT_DIR"/*.sh
    log_success "Made buildsc scripts executable"
fi

log_success "Environment setup completed!"
log_info "Please run: source ~/.bashrc to reload environment variables"
