'use strict';

const { json } = require('../../lib/server');
const { authorize, geny, flattenRecipeResponse, normalizeRecipe } = require('../../lib/genymotion');

module.exports = async function handler(req, res) {
  if (req.method !== 'GET') return json(res, 405, { ok: false, error: 'Méthode non autorisée' }, { allow: 'GET' });
  const auth = authorize(req);
  if (!auth.ok) return json(res, auth.status, { ok: false, error: auth.error });
  try {
    const payload = await geny('/v1/recipes');
    const recipes = flattenRecipeResponse(payload)
      .map(normalizeRecipe)
      .filter(r => r.uuid)
      .sort((a, b) => {
        const armA = /arm64/i.test(a.arch) ? 1 : 0;
        const armB = /arm64/i.test(b.arch) ? 1 : 0;
        if (armA !== armB) return armB - armA;
        return String(b.android || '').localeCompare(String(a.android || ''), undefined, { numeric: true });
      });
    return json(res, 200, { ok: true, recipes });
  } catch (e) {
    return json(res, 502, { ok: false, error: e.message });
  }
};
