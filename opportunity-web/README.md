# DraftWA Opportunities — server-first web module

This module replaces the heavy browser-side orchestration from `gmaps-mod.zip` with a server-first architecture.

## What runs on the server

- explicit geocoding through Nominatim / OpenStreetMap;
- radius queries through Overpass;
- optional Google Places Text Search when `GOOGLE_PLACES_API_KEY` exists on Vercel;
- geographic distance calculation;
- cross-source deduplication;
- opportunity scoring and priority A+ → D;
- CSV, Excel-compatible `.xls`, JSON and GeoJSON generation;
- NDJSON progress events during searches.

The Android device/browser only renders the map, inputs, progress and result table. The WhatsApp AccessibilityService is completely separate from this module.

## API

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

## Google Places

Google Places is optional. The browser never receives the API key. Add `GOOGLE_PLACES_API_KEY` to the Vercel project environment to enable it. Searches still work with OpenStreetMap when the key is absent.

## Safety / privacy

The backend processes public organization/place information. It does not receive WhatsApp draft contents. The Android WebView exposes no JavaScript bridge and only keeps the DraftWA Vercel origin inside the app; external company/Google/OSM links open in the system browser.
