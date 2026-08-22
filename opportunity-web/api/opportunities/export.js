'use strict';
const { readJsonBody, baseHeaders, json, options } = require('../../lib/server');

const PRIORITY_COLUMNS=[
  ['Nom','name'],['Nom officiel','officialName'],['Catégorie','category'],['Secteur / office','office'],['Commerce / shop','shop'],['Équipement / amenity','amenity'],['Industrie','industrial'],['Santé','healthcare'],
  ['Marque','brand'],['Opérateur','operator'],['Propriétaire','owner'],['Réseau','network'],['Description','description'],
  ['Adresse','address'],['Numéro','houseNumber'],['Rue','street'],['Quartier','neighbourhood'],['Banlieue','suburb'],['Ville','city'],['District','district'],['Code postal','postcode'],['Région / État','state'],['Province','province'],['Pays','country'],
  ['Latitude','lat'],['Longitude','lng'],['Distance du centre (m)','distanceFromCenterM'],
  ['Téléphone','phone'],['Mobile','mobile'],['E-mail','email'],['Site web','website'],['Facebook','facebook'],['Instagram','instagram'],['LinkedIn','linkedin'],['X / Twitter','twitter'],['YouTube','youtube'],
  ['Horaires','openingHours'],['Accessibilité fauteuil','wheelchair'],['Accès internet','internetAccess'],['Paiement espèces','paymentCash'],['Paiement cartes','paymentCards'],['Paiement Visa','paymentVisa'],['Paiement Mastercard','paymentMastercard'],['Paiement mobile','paymentMobile'],
  ['Score opportunité','opportunityScore'],['Priorité','priority'],['Raison opportunité','opportunityReason'],['Signaux détectés','detectedSignals'],
  ['Source','source'],['URL source','sourceUrl'],['Google Maps URL','googleMapsUrl'],['Source ID','sourceId'],['Type OSM','osmType'],['ID OSM','osmId'],['Version OSM','osmVersion'],['Dernière modification OSM','osmTimestamp'],['Changeset OSM','osmChangeset']
];
const RAW_LAST_KEYS=['osmTagsJson','osmMetaJson'];
const OBJECT_KEYS=new Set(['osmTags','osmMeta']);
const priorityKeys=new Set(PRIORITY_COLUMNS.map(([,k])=>k));

function safeCell(value){
  let s='';
  if(value==null)s='';
  else if(Array.isArray(value))s=value.join(' | ');
  else if(typeof value==='object')s=JSON.stringify(value);
  else s=String(value);
  if(/^[=+\-@]/.test(s))s=`'${s}`;
  return s;
}
function csvCell(value){const s=safeCell(value).replace(/"/g,'""');return `"${s}"`}
function populated(v){return !(v===undefined||v===null||v===''||(Array.isArray(v)&&v.length===0))}

function buildColumns(rows){
  const topCounts=new Map(),tagCounts=new Map();
  for(const row of rows){
    for(const [k,v] of Object.entries(row||{})){
      if(priorityKeys.has(k)||RAW_LAST_KEYS.includes(k)||OBJECT_KEYS.has(k)||!populated(v))continue;
      topCounts.set(k,(topCounts.get(k)||0)+1);
    }
    for(const [k,v] of Object.entries(row?.osmTags||{}))if(populated(v))tagCounts.set(k,(tagCounts.get(k)||0)+1);
  }
  const byFrequency=(a,b)=>b[1]-a[1]||a[0].localeCompare(b[0]);
  const top=[...topCounts.entries()].sort(byFrequency).map(([k])=>[`Champ ${k}`,k]);
  const osm=[...tagCounts.entries()].sort(byFrequency).map(([k])=>[`OSM ${k}`,`__osm__${k}`]);
  return [...PRIORITY_COLUMNS,...top,...osm,['Tous les tags OSM (JSON)','osmTagsJson'],['Métadonnées OSM (JSON)','osmMetaJson']];
}
function valueFor(row,key){return key.startsWith('__osm__')?row?.osmTags?.[key.slice(7)]??'':row?.[key]??''}
function toCsv(rows){const cols=buildColumns(rows),lines=[cols.map(([label])=>csvCell(label)).join(',')];for(const row of rows)lines.push(cols.map(([,key])=>csvCell(valueFor(row,key))).join(','));return '\ufeff'+lines.join('\r\n')}
function toExcelTsv(rows){const cols=buildColumns(rows),clean=value=>safeCell(value).replace(/[\t\r\n]+/g,' '),lines=[cols.map(([label])=>clean(label)).join('\t')];for(const row of rows)lines.push(cols.map(([,key])=>clean(valueFor(row,key))).join('\t'));return '\ufeff'+lines.join('\r\n')}
function toGeoJson(rows){const cols=buildColumns(rows);return JSON.stringify({type:'FeatureCollection',features:rows.filter(r=>Number.isFinite(Number(r.lat))&&Number.isFinite(Number(r.lng))).map(r=>({type:'Feature',geometry:{type:'Point',coordinates:[Number(r.lng),Number(r.lat)]},properties:Object.fromEntries(cols.filter(([,key])=>!['lat','lng'].includes(key)).map(([label,key])=>[label,valueFor(r,key)]))}))},null,2)}

module.exports=async function handler(req,res){
  if(options(req,res)) return;
  if(req.method!=='POST')return json(res,405,{ok:false,error:'Méthode non autorisée'},{allow:'POST'},req);
  try{
    const body=await readJsonBody(req,12*1024*1024);const rows=Array.isArray(body.results)?body.results.slice(0,5000):[];
    if(!rows.length)return json(res,400,{ok:false,error:'Aucun résultat à exporter'},{},req);
    const format=String(body.format||'csv').toLowerCase();let data,type,ext;
    if(format==='json'){data=JSON.stringify(rows,null,2);type='application/json; charset=utf-8';ext='json'}
    else if(format==='geojson'){data=toGeoJson(rows);type='application/geo+json; charset=utf-8';ext='geojson'}
    else if(format==='excel'||format==='xls'){data=toExcelTsv(rows);type='application/vnd.ms-excel; charset=utf-8';ext='xls'}
    else{data=toCsv(rows);type='text/csv; charset=utf-8';ext='csv'}
    res.statusCode=200;for(const[k,v]of Object.entries(baseHeaders({'content-type':type,'content-disposition':`attachment; filename="draftwa-opportunities.${ext}"`},req)))res.setHeader(k,v);res.end(data);
  }catch(e){return json(res,400,{ok:false,error:e.message||'Export impossible'},{},req)}
};
module.exports._test={buildColumns,toCsv,toExcelTsv,toGeoJson,safeCell};
