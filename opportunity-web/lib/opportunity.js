'use strict';

const EARTH_RADIUS_M = 6371000;
const TECHNICAL_WORDS = [
  'software','informatik','engineering','ingenieur','maschinenbau','industrie','industrial',
  'manufacturer','automation','data','artificial intelligence','ai','logistik','logistics',
  'energy','energie','umwelt','environment','technology','technologie','elektronik','robotik'
];
const BUSINESS_WORDS = [
  'consulting','beratung','bank','finance','steuerberater','accounting','law','rechtsanwalt',
  'versicherung','insurance','office','unternehmen','firma','gmbh','audit','marketing','agentur'
];
const PUBLIC_RESEARCH_WORDS = [
  'hospital','krankenhaus','clinic','klinik','school','schule','university','hochschule','college',
  'public','stadt','government','ngo','association','verein','forschung','research','laboratory','labor'
];
const OPPORTUNITY_WORDS = [
  'karriere','career','jobs','stellenangebote','praktikum','internship','werkstudent','trainee',
  'ausbildung','duales studium','graduate'
];

function clamp(n, min, max) {
  n = Number(n);
  return Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : min;
}

function normalizeText(value) {
  return String(value || '')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function haversineM(lat1, lng1, lat2, lng2) {
  const p1 = Number(lat1) * Math.PI / 180;
  const p2 = Number(lat2) * Math.PI / 180;
  const dp = (Number(lat2) - Number(lat1)) * Math.PI / 180;
  const dl = (Number(lng2) - Number(lng1)) * Math.PI / 180;
  const a = Math.sin(dp / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function firstTag(tags, names) {
  for (const name of names) {
    if (tags && tags[name]) return String(tags[name]).trim();
  }
  return '';
}

function categoryFromTags(tags = {}) {
  const keys = ['office','craft','industrial','amenity','shop','tourism','healthcare','leisure','man_made'];
  for (const key of keys) {
    if (tags[key]) return String(tags[key]).replace(/_/g, ' ');
  }
  return firstTag(tags, ['brand','operator']) || 'organisation';
}

function addressFromTags(tags = {}) {
  const line = [tags['addr:housenumber'], tags['addr:street']].filter(Boolean).join(' ');
  const city = [tags['addr:postcode'], tags['addr:city'] || tags['addr:town'] || tags['addr:village']].filter(Boolean).join(' ');
  return [line, city].filter(Boolean).join(', ');
}

function priorityForScore(score) {
  if (score >= 85) return 'A+';
  if (score >= 70) return 'A';
  if (score >= 55) return 'B';
  if (score >= 40) return 'C';
  return 'D';
}

function scoreEntity(entity, center = {}, keywords = []) {
  const text = normalizeText([
    entity.name, entity.category, entity.address, entity.website, entity.detectedSignals,
    ...(Array.isArray(entity.types) ? entity.types : [])
  ].filter(Boolean).join(' '));

  let score = 18;
  const reasons = [];
  const signals = [];

  const technicalHits = TECHNICAL_WORDS.filter(w => text.includes(normalizeText(w)));
  const businessHits = BUSINESS_WORDS.filter(w => text.includes(normalizeText(w)));
  const publicHits = PUBLIC_RESEARCH_WORDS.filter(w => text.includes(normalizeText(w)));
  const opportunityHits = OPPORTUNITY_WORDS.filter(w => text.includes(normalizeText(w)));
  if (technicalHits.length) { score += 27; reasons.push('secteur technique/industrie'); signals.push(...technicalHits.slice(0, 3)); }
  else if (businessHits.length) { score += 22; reasons.push('services professionnels/finance'); signals.push(...businessHits.slice(0, 3)); }
  else if (publicHits.length) { score += 18; reasons.push('institution/recherche/santé'); signals.push(...publicHits.slice(0, 3)); }

  const keywordHits = (keywords || [])
    .map(normalizeText)
    .filter(Boolean)
    .filter(k => text.includes(k));
  if (keywordHits.length) {
    score += Math.min(18, 8 + keywordHits.length * 3);
    reasons.push('correspond aux mots-clés');
    signals.push(...keywordHits.slice(0, 4));
  }

  if (opportunityHits.length) {
    score += 20;
    reasons.push('signaux carrière détectés');
    signals.push(...opportunityHits.slice(0, 4));
  }
  if (entity.website) { score += 8; reasons.push('site web'); }
  if (entity.phone) { score += 5; reasons.push('téléphone public'); }
  if (entity.email) { score += 7; reasons.push('email public'); }
  if (Number(entity.userRatingCount) >= 20) score += 3;
  if (Number(entity.rating) >= 4) score += 2;

  let distance = Number(entity.distanceFromCenterM);
  if (!Number.isFinite(distance) && Number.isFinite(Number(center.lat)) && Number.isFinite(Number(center.lng))
      && Number.isFinite(Number(entity.lat)) && Number.isFinite(Number(entity.lng))) {
    distance = Math.round(haversineM(center.lat, center.lng, entity.lat, entity.lng));
  }
  if (Number.isFinite(distance)) {
    if (distance <= 1000) { score += 12; reasons.push('≤ 1 km'); }
    else if (distance <= 3000) { score += 9; reasons.push('≤ 3 km'); }
    else if (distance <= 7000) { score += 5; reasons.push('≤ 7 km'); }
  }

  score = Math.round(clamp(score, 0, 100));
  return {
    ...entity,
    distanceFromCenterM: Number.isFinite(distance) ? Math.round(distance) : null,
    opportunityScore: score,
    priority: priorityForScore(score),
    opportunityReason: reasons.join(' • ') || 'profil à qualifier',
    detectedSignals: [...new Set([...(String(entity.detectedSignals || '').split(',').map(x => x.trim()).filter(Boolean)), ...signals])].join(', ')
  };
}

function parseOverpass(payload, center) {
  const out = [];
  for (const el of (payload && payload.elements) || []) {
    const tags = el.tags || {};
    const lat = Number(el.lat ?? (el.center && el.center.lat));
    const lng = Number(el.lon ?? (el.center && el.center.lon));
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue;
    const name = firstTag(tags, ['name','brand','operator']);
    if (!name) continue;
    out.push({
      source: 'OpenStreetMap',
      sourceId: `osm:${el.type}:${el.id}`,
      name,
      category: categoryFromTags(tags),
      address: addressFromTags(tags),
      city: firstTag(tags, ['addr:city','addr:town','addr:village']),
      country: firstTag(tags, ['addr:country']),
      lat,
      lng,
      distanceFromCenterM: Math.round(haversineM(center.lat, center.lng, lat, lng)),
      phone: firstTag(tags, ['contact:phone','phone','contact:mobile']),
      website: firstTag(tags, ['contact:website','website','url']),
      email: firstTag(tags, ['contact:email','email']),
      sourceUrl: `https://www.openstreetmap.org/${el.type}/${el.id}`,
      googleMapsUrl: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${lat},${lng}`)}`,
      types: Object.entries(tags).filter(([k]) => ['office','craft','industrial','amenity','shop','tourism','healthcare'].includes(k)).map(([,v]) => String(v))
    });
  }
  return out;
}

function mapGooglePlace(place, center) {
  const lat = Number(place && place.location && place.location.latitude);
  const lng = Number(place && place.location && place.location.longitude);
  const name = (place && place.displayName && place.displayName.text) || '';
  if (!name || !Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  return {
    source: 'Google Places',
    sourceId: `google:${place.id || ''}`,
    placeId: place.id || '',
    name,
    category: String(place.primaryType || (place.types && place.types[0]) || 'organisation').replace(/_/g, ' '),
    address: place.formattedAddress || '',
    lat,
    lng,
    distanceFromCenterM: Math.round(haversineM(center.lat, center.lng, lat, lng)),
    phone: place.nationalPhoneNumber || place.internationalPhoneNumber || '',
    website: place.websiteUri || '',
    email: '',
    sourceUrl: place.googleMapsUri || '',
    googleMapsUrl: place.googleMapsUri || `https://www.google.com/maps/search/?api=1&query_place_id=${encodeURIComponent(place.id || '')}`,
    rating: Number(place.rating) || null,
    userRatingCount: Number(place.userRatingCount) || 0,
    types: Array.isArray(place.types) ? place.types : []
  };
}

function canonicalKey(entity) {
  const name = normalizeText(entity.name);
  const lat = Number(entity.lat);
  const lng = Number(entity.lng);
  // Prefer a cross-source geographic key so the same organisation returned by
  // OSM and Google Places is merged instead of being shown twice. Place IDs are
  // still preserved as metadata after the merge.
  if (name && Number.isFinite(lat) && Number.isFinite(lng)) {
    return `${name}|${lat.toFixed(4)}|${lng.toFixed(4)}`;
  }
  if (entity.placeId) return `place:${entity.placeId}`;
  return `${name}|${normalizeText(entity.address)}`;
}

function mergeEntity(a, b) {
  const richer = { ...a };
  const fields = ['category','address','city','country','phone','website','email','sourceUrl','googleMapsUrl','placeId'];
  for (const f of fields) if (!richer[f] && b[f]) richer[f] = b[f];
  richer.source = [...new Set([...(String(a.source || '').split(' + ')), ...(String(b.source || '').split(' + '))].filter(Boolean))].join(' + ');
  richer.types = [...new Set([...(a.types || []), ...(b.types || [])])];
  if (Number(b.rating) > Number(richer.rating || 0)) richer.rating = b.rating;
  if (Number(b.userRatingCount) > Number(richer.userRatingCount || 0)) richer.userRatingCount = b.userRatingCount;
  return richer;
}

function dedupeEntities(entities) {
  const map = new Map();
  for (const entity of entities || []) {
    if (!entity || !entity.name) continue;
    const key = canonicalKey(entity);
    if (!key) continue;
    map.set(key, map.has(key) ? mergeEntity(map.get(key), entity) : { ...entity });
  }
  return [...map.values()];
}

function buildOverpassQuery(lat, lng, radiusM) {
  const r = Math.round(clamp(radiusM, 250, 20000));
  const a = Number(lat).toFixed(6);
  const o = Number(lng).toFixed(6);
  const around = `(around:${r},${a},${o})`;
  return `[out:json][timeout:24][maxsize:50000000];\n(\n`
    + `nwr["name"]["office"]${around};\n`
    + `nwr["name"]["craft"]${around};\n`
    + `nwr["name"]["industrial"]${around};\n`
    + `nwr["name"]["amenity"~"bank|clinic|hospital|school|university|college|library|townhall|courthouse|police|fire_station|research_institute"]${around};\n`
    + `nwr["name"]["shop"]${around};\n`
    + `nwr["name"]["tourism"~"hotel|hostel"]${around};\n`
    + `nwr["name"]["healthcare"]${around};\n`
    + `);\nout center tags qt;`;
}

function sanitizeKeywords(value, max = 8) {
  const arr = Array.isArray(value) ? value : String(value || '').split(/[\n,;]+/);
  return [...new Set(arr.map(x => String(x).trim()).filter(x => x.length >= 2 && x.length <= 80))].slice(0, max);
}

function sanitizeSearchInput(input = {}) {
  const lat = Number(input.lat);
  const lng = Number(input.lng);
  if (!Number.isFinite(lat) || lat < -90 || lat > 90) throw new Error('Latitude invalide');
  if (!Number.isFinite(lng) || lng < -180 || lng > 180) throw new Error('Longitude invalide');
  return {
    lat,
    lng,
    radiusM: Math.round(clamp(input.radiusM || 3000, 250, 20000)),
    keywords: sanitizeKeywords(input.keywords),
    minScore: Math.round(clamp(input.minScore || 0, 0, 100)),
    useGoogle: input.useGoogle === true
  };
}

function rankEntities(entities, center, keywords, minScore = 0) {
  return dedupeEntities(entities)
    .map(e => scoreEntity(e, center, keywords))
    .filter(e => e.opportunityScore >= minScore)
    .sort((a, b) => b.opportunityScore - a.opportunityScore || (a.distanceFromCenterM ?? 1e12) - (b.distanceFromCenterM ?? 1e12));
}

module.exports = {
  EARTH_RADIUS_M,
  clamp,
  normalizeText,
  haversineM,
  priorityForScore,
  scoreEntity,
  parseOverpass,
  mapGooglePlace,
  dedupeEntities,
  buildOverpassQuery,
  sanitizeKeywords,
  sanitizeSearchInput,
  rankEntities
};
