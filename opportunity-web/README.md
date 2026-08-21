# DraftWA Opportunities + Android Lab

This Vercel module contains two server-first surfaces:

- `/opportunities/` for map/prospecting research;
- `/android-lab/` for a real Genymotion SaaS Android VM used to install and test DraftWA APKs.

## Opportunities backend

The server handles:

- geocoding through Nominatim / OpenStreetMap;
- radius queries through Overpass;
- optional Google Places Text Search when `GOOGLE_PLACES_API_KEY` exists on Vercel;
- geographic distance calculation;
- cross-source deduplication;
- opportunity scoring and priority A+ → D;
- CSV, Excel-compatible `.xls`, JSON and GeoJSON generation;
- NDJSON progress events during searches.

The Android device/browser only renders the map, inputs, progress and result table. The WhatsApp AccessibilityService is separate from this module.

## Android Lab

The Android Lab does not emulate Android inside Vercel. Vercel is the secure control plane and Genymotion SaaS provides the real Android cloud VM.

Flow:

1. `/android-lab/` authenticates with a private lab key.
2. The Vercel backend lists Genymotion recipes and starts a disposable Android VM.
3. The backend requests an instance-scoped Genymotion access token.
4. The Genymotion web player opens the VM through WebRTC.
5. The tester installs any `.apk` using Genymotion Install Apps / File Upload or drag-and-drop.
6. The VM is disposable and is configured to stop after inactivity.

Required Vercel secrets:

- `GENYMOTION_API_TOKEN` — API token generated in the Genymotion SaaS dashboard. Never expose it to client JavaScript.
- `ANDROID_LAB_KEY` — a long random password used to protect VM start/stop/session APIs.

Optional Vercel secret:

- `GOOGLE_PLACES_API_KEY` — enables Google Places for the opportunities module.

Android Lab endpoints:

- `GET /api/android-lab/status`
- `GET /api/android-lab/recipes`
- `GET|POST /api/android-lab/instances`
- `POST /api/android-lab/session`
- `POST /api/android-lab/stop`

All Android Lab endpoints except `status` require the `x-draftwa-lab-key` header. The main Genymotion API token stays server-side. The browser only receives the short-lived access token scoped to the selected VM.

## Opportunities API

- `GET /api/opportunities/health`
- `GET /api/opportunities/geocode?q=Hof`
- `POST /api/opportunities/search` (NDJSON streaming response)
- `POST /api/opportunities/export`

Search body example:

```json
{
  "lat": 50.313,
  "lng": 11.912,
  "radiusM": 3000,
  "keywords": ["Software", "IT", "Engineering", "Praktikum"],
  "minScore": 55,
  "useGoogle": false
}
```

## Safety / privacy

The opportunities backend processes public organization/place information. It does not receive WhatsApp draft contents. Android Lab secrets are server-side, disposable VM control is protected by `ANDROID_LAB_KEY`, and no long-lived Genymotion account credential is returned to the browser.
