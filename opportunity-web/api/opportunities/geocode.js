'use strict';

const { geocode, json } = require('../../lib/server');

module.exports = async function handler(req, res) {
  if (req.method !== 'GET') return json(res, 405, { ok: false, error: 'Méthode non autorisée' }, { allow: 'GET' });
  try {
    const q = req.query && req.query.q;
    const results = await geocode(q);
    return json(res, 200, { ok: true, results });
  } catch (e) {
    return json(res, 400, { ok: false, error: e.message || 'Géocodage impossible' });
  }
};
