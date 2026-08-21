'use strict';

const { json } = require('../../lib/server');

module.exports = async function handler(req, res) {
  if (req.method !== 'GET') return json(res, 405, { ok: false, error: 'Méthode non autorisée' }, { allow: 'GET' });
  return json(res, 200, {
    ok: true,
    service: 'DraftWA Opportunities',
    version: '0.8.0',
    compute: 'server',
    maxRadiusM: 20000,
    sources: {
      openStreetMap: true,
      googlePlaces: Boolean(process.env.GOOGLE_PLACES_API_KEY)
    },
    exports: ['csv','excel','json','geojson']
  });
};
