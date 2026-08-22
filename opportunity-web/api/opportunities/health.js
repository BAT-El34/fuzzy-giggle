'use strict';
const { json, options } = require('../../lib/server');
module.exports = async (req,res) => {
  if (options(req,res)) return;
  if (req.method !== 'GET') return json(res,405,{ok:false,error:'Méthode non autorisée'},{allow:'GET'},req);
  return json(res,200,{ok:true,service:'DraftWA Map Search',version:'1.1.0',compute:'server',maxRadiusM:25000,sources:{openStreetMap:true,googlePlaces:Boolean(process.env.GOOGLE_PLACES_API_KEY)},features:['pwa','history','favorites','share','csv','excel','json','geojson','boundary','osm-meta','dynamic-osm-tags','dynamic-export-columns']},{},req);
};
