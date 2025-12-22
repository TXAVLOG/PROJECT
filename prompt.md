# API Update Request – TXA Music Migration

## 1. Context
- Mobile app has been rebranded from **TXA Demo** to **TXA Music**.
- Package ID changed: `gc.txa.demo` → `ms.txams.vv`.
- Client version reset to **1.0.0_txa** (versionCode `100`).
- All OTA flows (translation, update check, download links) must now serve **TXA Music** data only.

## 2. Objectives for API Team
1. **Rename & isolate environment**
   - Ensure all endpoints under `https://soft.nrotxa.online/txamusic/api/` serve TXA Music resources only.
   - Deprecate or block `/txademo/` responses to prevent stale data.
2. **Update Translation Service**
   - Replace old `txademo_*` keys with the new `txamusic_*` set (see sample payload below).
   - Guarantee `updated_at` timestamps increase whenever content changes.
3. **Update Checker Endpoint**
   - Return TXA Music metadata: version `1.0.0_txa`, package `ms.txams.vv`, download size, changelog, force flags.
   - Remove demo notice fields (`demo_notice_*`).
4. **Download Payloads**
   - Host APK at `/Download/TXAMusic/` and reflect the new file naming convention (`TXAMusic_<version>.apk`).
   - Provide HTTPS links compatible with MediaFire/GitHub/Drive resolvers.
5. **OTA Security & Validation**
   - Maintain SHA256 checksum field for each build.
   - Enforce signature/whitelist logic if any server-side validation references old package ID.
6. **Localization Coverage**
   - Ensure locales endpoint includes: `vi`, `en`
   - Supply fallback strings for missing keys to avoid nulls on client.

## 3. Required Endpoints & Contracts
| Endpoint | Method | Purpose | Notes |
| --- | --- | --- | --- |
| `/txamusic/api/locales` | GET | List available locale codes | Ensure `updated_at` per locale. |
| `/txamusic/api/locale/{locale}` | GET | Fetch translation map | Return only `txamusic_*` keys. |
| `/txamusic/api/update/check` | POST | OTA update metadata | Accepts `packageId`, `versionName`, `locale`. |

### Sample Translation Payload
```json
{
  "locale": "en",
  "updated_at": "2025-12-20T10:00:00Z",
  "strings": {
    "txamusic_music_library_title": "Music Library",
    "txamusic_refresh_library": "Refresh Library",
    "txamusic_settings_music_library": "Music Library"
  }
}
```

### Sample Update Response
```json
{
  "packageId": "ms.txams.vv",
  "versionName": "1.0.0_txa",
  "versionCode": 100,
  "isForce": false,
  "download": {
    "url": "https://soft.nrotxa.online/download/TXAMusic/TXAMusic_1.0.0_txa.apk",
    "size": 45875200,
    "checksum_sha256": "<sha256>"
  },
  "changelog_html": "<ul><li>Music library scanner</li><li>New OTA translations</li></ul>",
  "released_at": "2025-12-22T09:00:00Z"
}
```

## 4. Validation Checklist
- [ ] `/txamusic/` endpoints return HTTP 200 with valid JSON.
- [ ] No response contains `txademo_` keys or `gc.txa.demo` strings.
- [ ] Translation diffs trigger new `updated_at` timestamps.
- [ ] Update response download URL is reachable and matches checksum.
- [ ] APK metadata (package name, version) aligns with mobile build.
- [ ] Legacy `/txademo/` endpoints return HTTP 410/404 or clear deprecation notice.

## 5. Deliverables
1. Updated API deployment pointing to TXA Music data.
2. Postman collection or cURL samples verifying all endpoints.
3. Release note summarizing changes & any credentials/config tweaks.
4. ETA for production rollout + rollback plan if issues arise.

Please confirm once the API reflects the new TXA Music requirements so the mobile build can proceed with end-to-end testing.
