#!/bin/bash

echo "=== TXA Build Fix Script ==="
echo "Cleaning all caches and ensuring latest code..."

# 1. Stop all Gradle daemons
echo "Stopping Gradle daemons..."
./gradlew --stop

# 2. Check current commit
echo "Current commit:"
git log --oneline -1

# 3. Pull latest code
echo "Pulling latest code..."
git pull origin 09d36872

# 4. Check if we're on right commit
echo "Verifying commit (should be a3808a6)..."
git log --oneline -1

# 5. Delete all Gradle caches and build files
echo "Deleting Gradle caches..."
rm -rf ~/.gradle/caches
rm -rf ~/.gradle/daemon
rm -rf .gradle
rm -rf build
rm -rf app/build

# 6. Delete wrapper to force re-download
echo "Deleting Gradle wrapper..."
rm -rf gradle/wrapper

# 7. Re-create wrapper with correct version
echo "Re-creating Gradle wrapper 8.9..."
gradle wrapper --gradle-version 8.9

# 8. Verify wrapper version
echo "Verifying wrapper version..."
cat gradle/wrapper/gradle-wrapper.properties

# 9. Check build.gradle doesn't have kapt
echo "Checking for kapt in build.gradle..."
if grep -q "kapt" app/build.gradle; then
    echo "ERROR: kapt still found in build.gradle!"
    grep -n "kapt" app/build.gradle
    exit 1
else
    echo "✓ No kapt found in build.gradle"
fi

# 10. Check for KSP
echo "Checking for KSP in build.gradle..."
if grep -q "ksp" app/build.gradle; then
    echo "✓ KSP found in build.gradle"
else
    echo "ERROR: KSP not found in build.gradle!"
    exit 1
fi

echo "=== Cleanup completed ==="
echo "Now run: ./buildsc/TXAQuickBuild.sh"
