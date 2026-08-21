'use strict';

const { json } = require('../../lib/server');
const { isConfigured, hasLabKey } = require('../../lib/genymotion');

module.exports = async function handler(req, res) {
  if (req.method !== 'GET') return json(res,405,{ok:false,error:'Méthode non autorisée'},{allow:'GET'});
  const serverManaged = isConfigured() && hasLabKey();
  return json(res,200,{
    ok:true,
    service:'DraftWA Android Lab',
    provider:'genymotion-saas',
    providerConfigured:isConfigured(),
    labKeyConfigured:hasLabKey(),
    serverManaged,
    bringYourOwnToken:true,
    ready:true,
    emulator:'real-android-cloud-vm'
  });
};
