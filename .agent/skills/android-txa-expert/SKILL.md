---
name: android-txa-expert
description: Expert skill for developing the TXA Music Player Android application. Handles Gradle 9.2.1, SDK 28+ (Android 9.0), and the project's specialized build/log/git workflow.
license: Proprietary
compatibility: Optimized for Kotlin 2.0.21, Jetpack Compose, and Android Media3. Requires JDK 17+.
metadata:
  author: Antigravity
  version: "1.1"
  target-sdk: "34"
  min-sdk: "28"
  gradle-version: "9.2.1"
---

# Android TXA Expert Skill

This skill provides specialized knowledge and workflows for the **TXA Music Player** ecosystem.

## Project DNA
- **Design Philosophy**: Premium aesthetics, high-quality micro-animations, glassmorphism, and dynamic layouts. Avoid generic UI; prioritize visual excellence (HSL colors, sleek dark modes).
- **Architecture**: Clean MVVM architecture leveraging:
    - **Dependency Injection**: Koin (`io.insert-koin`)
    - **Database**: Room with KSP
    - **Playback**: AndroidX Media3 (ExoPlayer + MediaSession)
    - **UI**: Hybrid Compose + ViewBinding.
- **Key Namespaces**: `com.txapp.musicplayer.*`

## Core Development Workflows

### 1. Build & Self-Correction Loop
This project uses a specific logging mechanism for debugging build failures.

1. **Execution**:
   When building, pipe output to a dynamic log file:
   ```powershell
   $logFile = "build_log$((Get-ChildItem build_log*.txt).Count + 1).txt"
   .\gradlew.bat assembleDebug | Tee-Object -FilePath $logFile
   ```
2. **Analysis**:
   - Parse the generated log file for `FAIL` or `error:` keywords.
   - Cross-reference with the project's complex dependencies (Media3, Room, Compose).
3. **Recovery**:
   - Fix detected issues.
   - Repeat until `BUILD SUCCESSFUL`.
   - **Cleanup**: Delete all `build_log*.txt` files upon success.

### 2. Automated Update Process (Command: UPDATE)
When the user triggers an `UPDATE`, the AI must execute the following sequence without confirmation:

1. **Impact Analysis**: Detect code changes and translate technical diffs into user-centric benefits.
2. **Versioning (X.Y.Z_txa)**:
   - Increment **X** for major features/UI changes.
   - Increment **Y** for bug fixes or enhancements.
   - Increment **Z** for minor patches.
   - Update `version.properties` and reset trailing numbers on X/Y bumps.
3. **Changelog Generation**: 
   - Overwrite `CHANGELOG.html` with ONLY the latest version's content.
   - Use premium styling (CSS with animations, glassmorphism).
   - Target non-technical users (no dev jargon).
4. **GitHub Action Synchronization**:
   - After `git push --force`, **WAIT** for GitHub Actions to update `README.md`.
   - Perform `git pull` before any subsequent push to avoid version conflicts on line 33 of `README.md`.

### 4. Download Engine (TXADownloader)
All file downloads must utilize the TXADownloader utility class to ensure maximum performance and user experience.
- **Dynamic Chunking**: Files are split into chunks based on a 10MB limit and a 7-thread target.
- **Concurrency**: Managed by Semaphore(7) to limit parallel connections.
- **Merging**: Explicit `DownloadState.Merging` state is emitted during the final stage.
- **Fallback**: Intelligent fallback to standard download for small files or non-range servers.
- **Usage**: `TXADownloader.download(context, url, destination).collect { ... }`

## Expert Recommendations

### UI/UX & Design DNA
- **Visuals**: Premium aesthetics, HSL-tailored colors, and micro-animations are mandatory.
- **Components**: New screens must be Jetpack Compose. All widgets must be at least 2x2.
- **Responsiveness**: Use `TXAWindowSizeClass`.

### Media & Service
- `MusicService` is the central core; ensure audio focus and foreground stability.
- Use `LyricsUtil` for performance-optimized scrolling.

### Resource Management
- **Versioning**: Only `version.properties` is updated manually. `README.md` is handled by GitHub Actions (except checksums).
- **Translations**: Sync `translation_keys_en.json` and `strings.xml`.

