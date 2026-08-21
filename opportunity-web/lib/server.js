'use strict';

const { buildOverpassQuery, parseOverpass, mapGooglePlace, sanitizeKeywords } = require('./opportunity');

const USER_AGENT = 'DraftWA-Opportunities/0.8.0 (+https://draftwa-mobile-five.vercel.app)';
const OVERPASS_ENDPOINTS = [
  'https://overpass-api.de/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter'
];

function withTimeout(ms) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ms);
  return { signal: controller.signal, done: () => clearTimeout(timer) };
}

async function fetchText(url, options = {}, timeoutMs = 15000) {
  const timeout = withTimeout(timeoutMs);
  try {
    const res = await fetch(url, { ...options, signal: timeout.signal });
    const text = await res.text();
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.slice(0, 240)}`);
    return { res, text };
  } finally {
    timeout.done();
  }
}

async function fetchJson(url, options = {}, timeoutMs = 15000) {
  const { res, text } = await fetchText(url, options, timeoutMs);
  try {
    return { res, data: JSON.parse(text) };
  } catch (e) {
    throw new Error(`Réponse JSON invalide (${url})`);
  }
}

async function collectOsm(center, radiusM) {
  const query = buildOverpassQuery(center.lat, center.lng, radiusM);
  let lastError = null;
  for (const endpoint of OVERPASS_ENDPOINTS) {
    try {
      const { data } = await fetchJson(endpoint, {
        method: 'POST',
        headers: {
          'content-type': 'application/x-www-form-urlencoded;charset=UTF-8',
          'accept': 'application/json',
          'user-agent': USER_AGENT
        },
        body: new URLSearchParams({ data: query }).toString()
      }, 30000);
      return { endpoint, entities: parseOverpass(data, center), rawCount: Array.isArray(data.elements) ? data.elements.length : 0 };
    } catch (e) {
      lastError = e;
    }
  }
  throw lastError || new Error('Overpass indisponible');
}

async function collectGoogle(center, radiusM, keywords, apiKey) {
  if (!apiKey) return { enabled: false, entities: [], queries: 0 };
  const qs = sanitizeKeywords(keywords, 6);
  const queries = qs.length ? qs : ['Unternehmen', 'Firma', 'Software company', 'Bank'];
  const entities = [];
  const errors = [];
  for (const textQuery of queries.slice(0, 6)) {
    try {
      const { data } = await fetchJson('https://places.googleapis.com/v1/places:searchText', {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'accept': 'application/json',
          'X-Goog-Api-Key': apiKey,
          'X-Goog-FieldMask': [
            'places.id','places.displayName','places.formattedAddress','places.location',
            'places.types','places.primaryType','places.websiteUri','places.nationalPhoneNumber',
            'places.internationalPhoneNumber','places.googleMapsUri','places.rating','places.userRatingCount'
          ].join(',')
        },
        body: JSON.stringify({
          textQuery,
          pageSize: 20,
          rankPreference: 'RELEVANCE',
          locationBias: {
            circle: {
              center: { latitude: center.lat, longitude: center.lng },
              radius: Math.min(Number(radiusM) || 3000, 50000)
            }
          }
        })
      }, 15000);
      for (const place of data.places || []) {
        const mapped = mapGooglePlace(place, center);
        if (mapped && mapped.distanceFromCenterM <= radiusM * 1.15) entities.push(mapped);
      }
    } catch (e) {
      errors.push(`${textQuery}: ${e.message}`);
    }
  }
  return { enabled: true, entities, queries: queries.length, errors };
}

async function geocode(query) {
  const q = String(query || '').trim();
  if (q.length < 2 || q.length > 160) throw new Error('Recherche de lieu invalide');
  const url = new URL('https://nominatim.openstreetmap.org/search');
  url.searchParams.set('q', q);
  url.searchParams.set('format', 'jsonv2');
  url.searchParams.set('limit', '5');
  url.searchParams.set('addressdetails', '1');
  const { data } = await fetchJson(url.toString(), {
    headers: {
      'accept': 'application/json',
      'accept-language': 'fr,en;q=0.8,de;q=0.7',
      'user-agent': USER_AGENT
    }
  }, 10000);
  return (Array.isArray(data) ? data : []).map(item => ({
    displayName: item.display_name || '',
    lat: Number(item.lat),
    lng: Number(item.lon),
    type: item.type || '',
    category: item.category || '',
    boundingBox: item.boundingbox || null
  })).filter(x => Number.isFinite(x.lat) && Number.isFinite(x.lng));
}

function baseHeaders(extra = {}) {
  return {
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
    'referrer-policy': 'no-referrer',
    ...extra
  };
}

function json(res, status, body, extraHeaders = {}) {
  res.statusCode = status;
  for (const [k,v] of Object.entries(baseHeaders({ 'content-type': 'application/json; charset=utf-8', ...extraHeaders }))) res.setHeader(k, v);
  res.end(JSON.stringify(body));
}

async function readJsonBody(req, maxBytes = 1024 * 1024) {
  if (req.body && typeof req.body === 'object' && !Buffer.isBuffer(req.body)) return req.body;
  let raw = '';
  for await (const chunk of req) {
    raw += chunk.toString('utf8');
    if (Buffer.byteLength(raw, 'utf8') > maxBytes) throw new Error('Requête trop volumineuse');
  }
  if (!raw.trim()) return {};
  return JSON.parse(raw);
}

module.exports = {
  USER_AGENT,
  OVERPASS_ENDPOINTS,
  collectOsm,
  collectGoogle,
  geocode,
  baseHeaders,
  json,
  readJsonBody
};
