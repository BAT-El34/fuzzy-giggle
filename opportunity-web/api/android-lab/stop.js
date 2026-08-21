'use strict';

const { json } = require('../../lib/server');
const { authorize, assertUuid, geny, body } = require('../../lib/genymotion');

module.exports = async function handler(req, res) {
  if (req.method !== 'POST') return json(res,405,{ok:false,error:'Méthode non autorisée'},{allow:'POST'});
  const auth = authorize(req);
  if (!auth.ok) return json(res,auth.status,{ok:false,error:auth.error});
  try {
    const input = await body(req);
    const uuid = assertUuid(input.uuid,'Instance UUID');
    await geny(`/v1/instances/${uuid}/stop-disposable`,{method:'POST',body:{}},req);
    return json(res,200,{ok:true,authMode:auth.mode,uuid,state:'STOPPING'});
  } catch(e) { return json(res,502,{ok:false,error:e.message}); }
};
