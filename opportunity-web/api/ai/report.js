'use strict';
const { json, options, body } = require('../../lib/server');

async function callOpenAI(input){
  const key = process.env.OPENAI_API_KEY;
  if (!key) throw Object.assign(new Error('OPENAI_API_KEY non configurée'), {status:503});
  const model = process.env.OPENAI_MODEL || 'gpt-5-mini';
  const r = await fetch('https://api.openai.com/v1/responses', {
    method:'POST',
    headers:{'authorization':'Bearer '+key,'content-type':'application/json'},
    body:JSON.stringify({model,input,max_output_tokens:900})
  });
  const text=await r.text();
  let data={};
  try{data=text?JSON.parse(text):{};}catch(_){data={};}
  if(!r.ok) throw Object.assign(new Error((data.error&&data.error.message)||'Erreur OpenAI'),{status:r.status});
  const out = Array.isArray(data.output) ? data.output : [];
  for (const item of out) {
    if (!item || !Array.isArray(item.content)) continue;
    for (const part of item.content) if (part && part.type === 'output_text' && part.text) return part.text;
  }
  return data.output_text || 'Rapport indisponible.';
}

module.exports=async(req,res)=>{
  if(options(req,res))return;
  if(req.method!=='POST')return json(res,405,{ok:false,error:'Méthode non autorisée'},{allow:'POST'},req);
  try{
    const data=await body(req,128*1024);
    const stats=data.stats||{};
    const prompt=[
      'Tu es l’assistant opérationnel de DraftWA Mobile.',
      'Produis un rapport concis en français sur la campagne de prospection.',
      'N’invente aucune donnée. Analyse uniquement les statistiques fournies.',
      'Structure: Résumé, progression, anomalies, recommandations opérationnelles.',
      'Statistiques JSON: '+JSON.stringify(stats)
    ].join('\n');
    const report=await callOpenAI(prompt);
    return json(res,200,{ok:true,report},{},req);
  }catch(e){return json(res,e.status||500,{ok:false,error:e.message||'Erreur IA'},{},req);}
};
