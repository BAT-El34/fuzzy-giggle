'use strict';
const { json, options, body } = require('../../lib/server');

async function callOpenAI(input){
  const key=process.env.OPENAI_API_KEY;
  if(!key)throw Object.assign(new Error('OPENAI_API_KEY non configurée'),{status:503});
  const model=process.env.OPENAI_MODEL||'gpt-5-mini';
  const r=await fetch('https://api.openai.com/v1/responses',{
    method:'POST',
    headers:{'authorization':'Bearer '+key,'content-type':'application/json'},
    body:JSON.stringify({model,input,max_output_tokens:700})
  });
  const text=await r.text();let data={};
  try{data=text?JSON.parse(text):{};}catch(_){}
  if(!r.ok)throw Object.assign(new Error((data.error&&data.error.message)||'Erreur OpenAI'),{status:r.status});
  for(const item of (data.output||[])){
    for(const part of (item.content||[])){
      if(part&&part.type==='output_text'&&part.text)return part.text;
    }
  }
  return data.output_text||'Réponse indisponible.';
}

module.exports=async(req,res)=>{
  if(options(req,res))return;
  if(req.method!=='POST')return json(res,405,{ok:false,error:'Méthode non autorisée'},{allow:'POST'},req);
  try{
    const data=await body(req,128*1024);
    const stats=data.stats||{};
    const messages=Array.isArray(data.messages)?data.messages.slice(-12):[];
    const safeMessages=messages.map(m=>({
      role:m&&m.role==='assistant'?'assistant':'user',
      content:String((m&&m.content)||'').slice(0,2000)
    }));
    const input=[
      {role:'system',content:'Tu es le chat opérationnel DraftWA. Réponds en français, de manière concise et pratique. Ne prétends pas avoir envoyé ou modifié des messages. Utilise uniquement les statistiques de campagne fournies comme contexte.'},
      {role:'system',content:'Statistiques de campagne: '+JSON.stringify(stats)},
      ...safeMessages
    ];
    const answer=await callOpenAI(input);
    return json(res,200,{ok:true,answer},{},req);
  }catch(e){return json(res,e.status||500,{ok:false,error:e.message||'Erreur IA'},{},req);}
};
