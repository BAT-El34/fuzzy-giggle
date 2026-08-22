'use strict';
const { readJsonBody, baseHeaders, json, options } = require('../../lib/server');
const COLUMNS=[['Name','name'],['Category','category'],['Address','address'],['Phone','phone'],['Website','website'],['Email','email'],['Latitude','lat'],['Longitude','lng'],['Distance From Center M','distanceFromCenterM'],['Opportunity Score','opportunityScore'],['Priority','priority'],['Opportunity Reason','opportunityReason'],['Detected Signals','detectedSignals'],['Source','source'],['Google Maps URL','googleMapsUrl'],['Place ID','placeId'],['Source ID','sourceId']];
function safeCell(value){let s=value==null?'':String(value);if(/^[=+\-@]/.test(s))s=`'${s}`;return s}
function csvCell(value){const s=safeCell(value).replace(/"/g,'""');return `"${s}"`}
function toCsv(rows){const lines=[COLUMNS.map(([label])=>csvCell(label)).join(',')];for(const row of rows)lines.push(COLUMNS.map(([,key])=>csvCell(row[key])).join(','));return '\ufeff'+lines.join('\r\n')}
function toExcelTsv(rows){const clean=value=>safeCell(value).replace(/[\t\r\n]+/g,' ');const lines=[COLUMNS.map(([label])=>clean(label)).join('\t')];for(const row of rows)lines.push(COLUMNS.map(([,key])=>clean(row[key])).join('\t'));return '\ufeff'+lines.join('\r\n')}
function toGeoJson(rows){return JSON.stringify({type:'FeatureCollection',features:rows.filter(r=>Number.isFinite(Number(r.lat))&&Number.isFinite(Number(r.lng))).map(r=>({type:'Feature',geometry:{type:'Point',coordinates:[Number(r.lng),Number(r.lat)]},properties:Object.fromEntries(COLUMNS.filter(([,key])=>!['lat','lng'].includes(key)).map(([label,key])=>[label,r[key]??'']))}))},null,2)}
module.exports=async function handler(req,res){
  if(options(req,res)) return;
  if(req.method!=='POST')return json(res,405,{ok:false,error:'Méthode non autorisée'},{allow:'POST'},req);
  try{
    const body=await readJsonBody(req,4*1024*1024);const rows=Array.isArray(body.results)?body.results.slice(0,5000):[];
    if(!rows.length)return json(res,400,{ok:false,error:'Aucun résultat à exporter'},{},req);
    const format=String(body.format||'csv').toLowerCase();let data,type,ext;
    if(format==='json'){data=JSON.stringify(rows,null,2);type='application/json; charset=utf-8';ext='json'}else if(format==='geojson'){data=toGeoJson(rows);type='application/geo+json; charset=utf-8';ext='geojson'}else if(format==='excel'||format==='xls'){data=toExcelTsv(rows);type='application/vnd.ms-excel; charset=utf-8';ext='xls'}else{data=toCsv(rows);type='text/csv; charset=utf-8';ext='csv'}
    res.statusCode=200;for(const[k,v]of Object.entries(baseHeaders({'content-type':type,'content-disposition':`attachment; filename="draftwa-opportunities.${ext}"`},req)))res.setHeader(k,v);res.end(data);
  }catch(e){return json(res,400,{ok:false,error:e.message||'Export impossible'},{},req)}
};
module.exports._test={toCsv,toExcelTsv,toGeoJson,safeCell};
