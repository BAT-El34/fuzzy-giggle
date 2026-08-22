'use strict';
const { boundary, json, options } = require('../../lib/server');
module.exports = async function handler(req,res){
  if(options(req,res)) return;
  if(req.method!=='GET') return json(res,405,{ok:false,error:'Méthode non autorisée'},{allow:'GET'},req);
  try{
    const q=req.query&&req.query.q;
    const result=await boundary(q);
    if(!result) return json(res,404,{ok:false,error:'Frontière introuvable'},{},req);
    return json(res,200,{ok:true,result},{},req);
  }catch(e){
    return json(res,400,{ok:false,error:e.message||'Recherche de frontière impossible'},{},req);
  }
};
