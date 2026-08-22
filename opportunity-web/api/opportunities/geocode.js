'use strict';
const { geocode, json, options } = require('../../lib/server');
module.exports = async function handler(req,res){
  if (options(req,res)) return;
  if(req.method!=='GET') return json(res,405,{ok:false,error:'Méthode non autorisée'},{allow:'GET'},req);
  try{
    const q=req.query&&req.query.q;
    const results=await geocode(q);
    return json(res,200,{ok:true,results},{},req);
  }catch(e){
    return json(res,400,{ok:false,error:e.message||'Géocodage impossible'},{},req);
  }
};
