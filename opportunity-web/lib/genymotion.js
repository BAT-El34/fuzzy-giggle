'use strict';

const crypto = require('crypto');
const { readJsonBody } = require('./server');

const BASE = 'https://api.geny.io/cloud';
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isConfigured() { return Boolean(process.env.GENYMOTION_API_TOKEN); }
function hasLabKey() { return Boolean(process.env.ANDROID_LAB_KEY); }
function hasSessionToken(req) { return Boolean(req && req.headers && req.headers['x-genymotion-api-token']); }

function safeEqual(a, b) {
  const aa = Buffer.from(String(a || ''));
  const bb = Buffer.from(String(b || ''));
  if (aa.length !== bb.length || aa.length === 0) return false;
  return crypto.timingSafeEqual(aa, bb);
}

function authorize(req) {
  if (hasSessionToken(req)) return { ok: true, mode: 'session-token' };
  const provider = process.env.GENYMOTION_API_TOKEN;
  if (!provider) return { ok: false, status: 503, error: 'Aucun token Genymotion configuré. Colle un token de session dans Android Lab.' };
  const expected = process.env.ANDROID_LAB_KEY;
  if (!expected) return { ok: false, status: 503, error: 'ANDROID_LAB_KEY non configurée sur Vercel' };
  if (!safeEqual(req && req.headers && req.headers['x-draftwa-lab-key'], expected)) return { ok: false, status: 401, error: 'Clé Android Lab invalide' };
  return { ok: true, mode: 'server-token' };
}

function resolveToken(req) {
  const direct = req && req.headers && req.headers['x-genymotion-api-token'];
  if (direct) {
    const token = String(direct).trim();
    if (token.length < 20 || token.length > 512) throw new Error('Token Genymotion de session invalide');
    return token;
  }
  const token = process.env.GENYMOTION_API_TOKEN;
  if (!token) throw new Error('GENYMOTION_API_TOKEN non configuré');
  return token;
}

function assertUuid(value, label = 'UUID') {
  const v = String(value || '').trim();
  if (!UUID_RE.test(v)) throw new Error(`${label} invalide`);
  return v;
}

async function geny(path, options = {}, req = null) {
  const apiToken = resolveToken(req);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeoutMs || 20000);
  try {
    const res = await fetch(`${BASE}${path}`, {
      method: options.method || 'GET',
      headers: {
        'accept': 'application/json',
        'content-type': 'application/json; charset=utf-8',
        'x-api-token': apiToken
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal
    });
    const text = await res.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); }
      catch { data = { raw: text.slice(0, 1000) }; }
    }
    if (!res.ok) {
      const detail = data && (data.detail || data.message || data.error);
      throw new Error(`Genymotion HTTP ${res.status}${detail ? `: ${detail}` : ''}`);
    }
    return data;
  } finally { clearTimeout(timer); }
}

function flattenRecipeResponse(payload) {
  if (Array.isArray(payload)) return payload;
  if (!payload || typeof payload !== 'object') return [];
  return Object.values(payload).filter(Array.isArray).flat().filter(x => x && typeof x === 'object' && x.uuid);
}

function normalizeRecipe(r) {
  const os = r.os_image || r.osImage || {};
  const osv = os.os_version || os.osVersion || {};
  return { uuid:r.uuid, name:r.name||'Android', source:r.source||'', status:r.status||'', arch:os.arch||'', android:osv.os_version||osv.name||'', sdk:Number(osv.sdk_version||0)||null, official:Boolean(r.is_official) };
}

function normalizeInstance(i) {
  const os = i.os_image || i.osImage || {};
  const osv = os.os_version || os.osVersion || {};
  return { uuid:i.uuid, name:i.name||'DraftWA Lab', state:i.state||'', recipeUuid:i.recipe_uuid||(i.recipe&&i.recipe.uuid)||'', arch:os.arch||'', android:osv.os_version||'', sdk:Number(osv.sdk_version||0)||null, webrtcUrl:i.webrtc_url||i.webrtcAddress||'', fileUploadUrl:i.file_upload_url||'', createdAt:i.created_at||null, updatedAt:i.updated_at||null };
}

async function body(req) { return readJsonBody(req, 64 * 1024); }

module.exports = { BASE, UUID_RE, isConfigured, hasLabKey, hasSessionToken, authorize, resolveToken, assertUuid, geny, flattenRecipeResponse, normalizeRecipe, normalizeInstance, body };
