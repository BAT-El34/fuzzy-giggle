'use strict';
const { json, options, body } = require('../../lib/server');

function validEmail(value){
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value||'').trim());
}

module.exports = async (req,res) => {
  if (options(req,res)) return;
  if (req.method !== 'POST') return json(res,405,{ok:false,error:'Méthode non autorisée'},{allow:'POST'},req);

  const apiKey = process.env.RESEND_API_KEY;
  const from = process.env.RESEND_FROM_EMAIL;
  if (!apiKey || !from) return json(res,503,{ok:false,error:'Resend non configuré côté serveur'}, {}, req);

  try {
    const data = await body(req, 64 * 1024);
    const to = String(data.to || '').trim();
    if (!validEmail(to)) return json(res,400,{ok:false,error:'Adresse email invalide'}, {}, req);

    const type = String(data.type || 'INFO').slice(0,40);
    const title = String(data.title || 'Notification DraftWA').slice(0,140);
    const message = String(data.message || '').slice(0,5000);
    const idempotency = String(data.idempotencyKey || '').slice(0,180);

    const response = await fetch('https://api.resend.com/emails', {
      method:'POST',
      headers:{
        'authorization': 'Bearer ' + apiKey,
        'content-type':'application/json',
        ...(idempotency ? {'idempotency-key': idempotency} : {})
      },
      body: JSON.stringify({
        from,
        to:[to],
        subject:title,
        text:'DraftWA\n\nType: ' + type + '\n\n' + message
      })
    });

    const text = await response.text();
    let payload={};
    try { payload = text ? JSON.parse(text) : {}; } catch(_){ payload={raw:text.slice(0,500)}; }
    if (!response.ok) return json(res,response.status,{ok:false,error:'Resend a refusé l’envoi',details:payload}, {}, req);
    return json(res,200,{ok:true,id:payload.id||null}, {}, req);
  } catch (e) {
    return json(res,500,{ok:false,error:e.message||'Erreur notification'}, {}, req);
  }
};
