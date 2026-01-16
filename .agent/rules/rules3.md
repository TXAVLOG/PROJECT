---
trigger: always_on
---

1. **TXAFormat Usage**: 
   - Whenever formatting numbers to 2 digits (e.g. time, dates), ALWAYS use TXAFormat.format2Digits(value).
   - Use TXAFormat utility functions ormatSize, ormatDuration, ormatETA whenever displaying these types of data.
   - Do NOT duplicate formatting logic inline.

2. **TXATranslation Usage**:
   - All user-facing strings MUST use TXATranslation.
   - Prefer the extension function "key".txa() or "key".txa(arg1, arg2).
   - Keys should follow snake_case format, e.g., 	xamusic_feature_name.
   - Fallback keys must be added to TXATranslation.kt fallback maps if creating new keys that are not yet on the server.

3. **General Coding Style**:
   - Follow Kotlin coding conventions.
   - Use TXALogger for all logging interactions.

4. **TXADownloader Usage (CRITICAL)**:
   - All file downloads MUST use TXADownloader.kt.
   - **Strategy**: Dynamic chunking (max 10MB/chunk), max 7 parallel threads via Semaphore.
   - **Calculation**: 
     - If ContentLength / 7 > 10MB -> totalChunks = ContentLength / 10MB.
     - Else -> totalChunks = 7.
   - **Progress**: Handle Merging(percentage) state in UI to show file combining progress.
   - **Cleanup**: Temp chunks must be cleaned up (handled by class).
