'use strict';
const { sanitizeSearchInput, rankEntities } = require('../../lib/opportunity');
const { collectOsm, collectGoogle, headers, readJsonBody } = require('../../lib/server');

function sendLine(res, payload) {
  res.write(`${JSON.stringify(payload)}\n`);
}

module.exports = async function handler(req, res) {
  if (req.method !== 'POST') {
    res.statusCode = 405;
    res.setHeader('allow', 'POST');
    return res.end('Method Not Allowed');
  }

  for (const [k, v] of Object.entries(headers({
    'content-type': 'application/x-ndjson; charset=utf-8',
    'x-accel-buffering': 'no'
  }))) res.setHeader(k, v);
  if (typeof res.flushHeaders === 'function') res.flushHeaders();

  const startedAt = Date.now();
  try {
    const raw = await readJsonBody(req, 256 * 1024);
    const input = sanitizeSearchInput(raw);
    const center = { lat: input.lat, lng: input.lng };

    sendLine(res, { type: 'progress', progress: 5, stage: 'accepted', message: 'Recherche acceptée par le serveur' });
    sendLine(res, { type: 'progress', progress: 15, stage: 'osm', message: 'Interrogation OpenStreetMap / Overpass' });

    let osm = { entities: [], rawCount: 0, endpoint: null, error: null };
    try {
      osm = await collectOsm(center, input.radiusM, input.categories);
      sendLine(res, { type: 'progress', progress: 52, stage: 'osm-done', message: `${osm.entities.length} entités OSM reçues` });
    } catch (e) {
      osm.error = e.message || 'Overpass indisponible';
      sendLine(res, { type: 'warning', progress: 52, stage: 'osm-error', message: osm.error });
    }

    let google = { enabled: false, entities: [], queries: 0, errors: [] };
    if (input.useGoogle && process.env.GOOGLE_PLACES_API_KEY) {
      sendLine(res, { type: 'progress', progress: 58, stage: 'google', message: 'Interrogation Google Places' });
      google = await collectGoogle(center, input.radiusM, input.keywords, process.env.GOOGLE_PLACES_API_KEY);
      sendLine(res, { type: 'progress', progress: 77, stage: 'google-done', message: `${google.entities.length} résultats Google Places reçus` });
    } else if (input.useGoogle) {
      sendLine(res, { type: 'warning', progress: 60, stage: 'google-disabled', message: 'Google Places non configuré sur le serveur ; poursuite avec OSM.' });
    }

    sendLine(res, { type: 'progress', progress: 84, stage: 'rank', message: 'Déduplication, distance et scoring côté serveur' });
    const ranked = rankEntities([
      ...(osm.entities || []),
      ...(google.entities || [])
    ], center, input.keywords, input.minScore, input.maxResults);

    const counts = ranked.reduce((acc, x) => {
      acc[x.priority] = (acc[x.priority] || 0) + 1;
      return acc;
    }, {});

    sendLine(res, {
      type: 'result',
      progress: 100,
      stage: 'done',
      message: `${ranked.length} entités classées`,
      data: {
        center,
        radiusM: input.radiusM,
        keywords: input.keywords,
        categories: input.categories,
        minScore: input.minScore,
        results: ranked,
        counts,
        sources: {
          osm: { count: osm.entities.length, rawCount: osm.rawCount || 0, endpoint: osm.endpoint || null, error: osm.error || null },
          google: { enabled: google.enabled, count: google.entities.length, queries: google.queries || 0, errors: google.errors || [] }
        },
        durationMs: Date.now() - startedAt
      }
    });
    res.end();
  } catch (e) {
    sendLine(res, { type: 'error', progress: 100, stage: 'error', message: e.message || 'Recherche impossible' });
    res.end();
  }
};