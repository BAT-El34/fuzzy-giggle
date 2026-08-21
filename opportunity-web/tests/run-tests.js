'use strict';
const assert = require('assert');
const lib = require('../lib/opportunity');
const exp = require('../api/opportunities/export')._test;

function near(actual, expected, tolerance, label){assert(Math.abs(actual-expected)<=tolerance, `${label}: ${actual} vs ${expected}`)}

near(lib.haversineM(0,0,1,0),111195,800,'haversine');
const input=lib.sanitizeSearchInput({lat:50.313,lng:11.912,radiusM:3000,keywords:['IT','Bank'],minScore:55,useGoogle:true});
assert.equal(input.radiusM,3000); assert.deepEqual(input.keywords,['IT','Bank']); assert.equal(input.useGoogle,true);
assert.throws(()=>lib.sanitizeSearchInput({lat:91,lng:0}),/Latitude/);

const osm=lib.parseOverpass({elements:[{type:'node',id:1,lat:50.314,lon:11.913,tags:{name:'Alpha Software GmbH',office:'it',website:'https://alpha.example',phone:'+49 1'}}]}, {lat:50.313,lng:11.912});
assert.equal(osm.length,1); assert.equal(osm[0].source,'OpenStreetMap'); assert(osm[0].distanceFromCenterM>0);

const ranked=lib.rankEntities([
  ...osm,
  {...osm[0],source:'Google Places',placeId:'p1',sourceId:'google:p1'},
  {source:'OpenStreetMap',sourceId:'osm:2',name:'Random Shop',category:'shop',lat:50.32,lng:11.92}
],{lat:50.313,lng:11.912},['Software'],0);
assert(ranked[0].name.includes('Alpha')); assert(ranked[0].opportunityScore>ranked[1].opportunityScore);
assert(['A+','A','B','C','D'].includes(ranked[0].priority));

const dedup=lib.dedupeEntities([{name:'Same',lat:1,lng:2,source:'A'},{name:'Same',lat:1.00001,lng:2.00001,source:'B',website:'https://x.test'}]);
assert.equal(dedup.length,1); assert(dedup[0].source.includes('A')&&dedup[0].source.includes('B')); assert.equal(dedup[0].website,'https://x.test');

const q=lib.buildOverpassQuery(50.313,11.912,3000); assert(q.includes('(around:3000,50.313000,11.912000)')); assert(q.includes('["office"]'));

const csv=exp.toCsv([{name:'=2+2',category:'IT',lat:1,lng:2}]); assert(csv.includes("'=2+2"));
const geo=JSON.parse(exp.toGeoJson([{name:'X',lat:1,lng:2}])); assert.equal(geo.features[0].geometry.coordinates[0],2);
const xls=exp.toExcelTsv([{name:'X',lat:1,lng:2}]); assert(xls.includes('\t'));

console.log('PASS opportunity-web tests');
