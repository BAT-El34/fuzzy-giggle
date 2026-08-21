'use strict';

const { json } = require('../../lib/server');
const { authorize, assertUuid, geny, normalizeInstance, body } = require('../../lib/genymotion');

module.exports = async function handler(req, res) {
  if (req.method !== 'POST') return json(res, 405, { ok: false, error: 'Méthode non autorisée' }, { allow: 'POST' });
  const auth = authorize(req);
  if (!auth.ok) return json(res, auth.status, { ok: false, error: auth.error });
  try {
    const input = await body(req);
    const uuid = assertUuid(input.uuid, 'Instance UUID');
    const instanceRaw = await geny(`/v1/instances/${uuid}`);
    const tokenPayload = await geny('/v1/instances/access-token', {
      method: 'POST',
      body: { instance_uuid: uuid }
    });
    const instance = normalizeInstance(instanceRaw);
    const accessToken = tokenPayload && tokenPayload.access_token;
    if (!accessToken) throw new Error('Jeton de session Genymotion absent');
    if (!instance.webrtcUrl) throw new Error('Flux WebRTC indisponible pour cette instance');
    return json(res, 200, {
      ok: true,
      instance,
      accessToken,
      expires: 'provider-managed'
    });
  } catch (e) {
    return json(res, 502, { ok: false, error: e.message });
  }
};
