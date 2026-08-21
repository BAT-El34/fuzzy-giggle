'use strict';

const { json } = require('../../lib/server');
const { isConfigured, hasLabKey } = require('../../lib/genymotion');

module.exports = async function handler(req, res) {
  if (req.method !== 'GET') return json(res, 405, { ok: false, error: 'Méthode non autorisée' }, { allow: 'GET' });
  return json(res, 200, {
    ok: true,
    service: 'DraftWA Android Lab',
    provider: 'genymotion-saas',
    providerConfigured: isConfigured(),
    labKeyConfigured: hasLabKey(),
    ready: isConfigured() && hasLabKey(),
    emulator: 'real-android-cloud-vm'
  });
};
