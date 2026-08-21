'use strict';

const { json } = require('../../lib/server');
const { authorize, assertUuid, geny, normalizeInstance, body } = require('../../lib/genymotion');

module.exports = async function handler(req, res) {
  const auth = authorize(req);
  if (!auth.ok) return json(res, auth.status, { ok: false, error: auth.error });

  if (req.method === 'GET') {
    try {
      const payload = await geny('/v1/instances');
      const list = Array.isArray(payload) ? payload : (payload && Array.isArray(payload.instances) ? payload.instances : []);
      return json(res, 200, { ok: true, instances: list.map(normalizeInstance) });
    } catch (e) {
      return json(res, 502, { ok: false, error: e.message });
    }
  }

  if (req.method === 'POST') {
    try {
      const input = await body(req);
      const recipeUuid = assertUuid(input.recipeUuid, 'Recipe UUID');
      const rawName = String(input.name || `DraftWA-Lab-${Date.now()}`).replace(/[^a-zA-Z0-9_. -]/g, '').slice(0, 64);
      const globalSeconds = Math.min(Math.max(Number(input.maxRunSeconds) || 7200, 900), 21600);
      const inactivitySeconds = Math.min(Math.max(Number(input.inactivitySeconds) || 1800, 300), globalSeconds);
      const payload = await geny(`/v1/recipes/${recipeUuid}/start-disposable`, {
        method: 'POST',
        body: {
          instance_name: rawName || `DraftWA-Lab-${Date.now()}`,
          rename_on_conflict: true,
          stop_when_inactive: true,
          automatic_release: { type: 'none' },
          timeouts: { global: globalSeconds, inactivity: inactivitySeconds }
        },
        timeoutMs: 30000
      });
      return json(res, 201, { ok: true, instance: normalizeInstance(payload) });
    } catch (e) {
      return json(res, 400, { ok: false, error: e.message });
    }
  }

  return json(res, 405, { ok: false, error: 'Méthode non autorisée' }, { allow: 'GET, POST' });
};
