(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const state = { results: [], center: { lat: 50.313, lng: 11.912 }, map: null, markers: [], circle: null, googleAvailable: false };
  const els = {
    place:$('place'), lat:$('lat'), lng:$('lng'), radius:$('radius'), radiusLabel:$('radiusLabel'), keywords:$('keywords'),
    minScore:$('minScore'), maxRows:$('maxRows'), useGoogle:$('useGoogle'), fitBounds:$('fitBounds'), searchBtn:$('searchBtn'),
    geocodeBtn:$('geocodeBtn'), progressBar:$('progressBar'), progressText:$('progressText'), resultsBody:$('resultsBody'), summary:$('summary'),
    stats:$('stats'), resultFilter:$('resultFilter'), priorityFilter:$('priorityFilter'), backendChip:$('backendChip'), osmChip:$('osmChip'), googleChip:$('googleChip')
  };

  function esc(v){return String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
  function km(m){ if(!Number.isFinite(Number(m))) return '—'; const n=Number(m); return n<1000?`${Math.round(n)} m`:`${(n/1000).toFixed(1).replace('.',',')} km`; }
  function setProgress(p, text, isError=false){ els.progressBar.style.width=`${Math.max(0,Math.min(100,Number(p)||0))}%`; els.progressText.textContent=text||''; els.progressText.classList.toggle('error',!!isError); }
  function keywords(){ return els.keywords.value.split(/[\n,;]+/).map(x=>x.trim()).filter(Boolean).slice(0,8); }

  function initMap(){
    if (!window.L) { $('map').innerHTML='<div class="empty">Carte indisponible. Les résultats restent utilisables.</div>'; return; }
    state.map=L.map('map',{zoomControl:true}).setView([state.center.lat,state.center.lng],13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap contributors'}).addTo(state.map);
    drawCenter();
  }
  function drawCenter(){
    if(!state.map||!window.L)return;
    if(state.circle) state.circle.remove();
    state.circle=L.circle([state.center.lat,state.center.lng],{radius:Number(els.radius.value),weight:2,fillOpacity:.04}).addTo(state.map);
  }
  function clearMarkers(){ if(!state.map)return; state.markers.forEach(m=>m.remove()); state.markers=[]; }
  function markerPopup(r){
    const web=r.website?`<a href="${esc(r.website)}" target="_blank" rel="noopener">Site</a>`:'';
    const maps=r.googleMapsUrl?`<a href="${esc(r.googleMapsUrl)}" target="_blank" rel="noopener">Google Maps</a>`:'';
    return `<strong>${esc(r.name)}</strong><br>${esc(r.category||'')}<br><b>${esc(r.priority)}</b> · ${esc(r.opportunityScore)}/100 · ${esc(km(r.distanceFromCenterM))}<br>${[web,maps].filter(Boolean).join(' · ')}`;
  }
  function drawResultsOnMap(rows){
    clearMarkers(); if(!state.map||!window.L)return;
    const pts=[];
    rows.slice(0,800).forEach(r=>{ const lat=Number(r.lat),lng=Number(r.lng); if(!Number.isFinite(lat)||!Number.isFinite(lng))return; const m=L.marker([lat,lng]).addTo(state.map).bindPopup(markerPopup(r)); state.markers.push(m); pts.push([lat,lng]); });
    drawCenter();
    if(els.fitBounds.checked && pts.length){ try{ state.map.fitBounds(L.latLngBounds(pts).pad(.12),{maxZoom:16}); }catch(_){ } }
  }

  function filtered(){
    const q=String(els.resultFilter.value||'').toLowerCase().trim(); const p=els.priorityFilter.value;
    return state.results.filter(r=>(!p||r.priority===p)&&(!q||[r.name,r.category,r.address,r.source,r.detectedSignals].join(' ').toLowerCase().includes(q)));
  }
  function render(){
    const rows=filtered(); const max=Math.max(1,Number(els.maxRows.value)||250); const shown=rows.slice(0,max);
    els.summary.textContent=`${rows.length} affiché(s) sur ${state.results.length} classé(s)`;
    const counts=state.results.reduce((a,r)=>(a[r.priority]=(a[r.priority]||0)+1,a),{});
    els.stats.innerHTML=['A+','A','B','C','D'].filter(k=>counts[k]).map(k=>`<span class="stat"><b>${esc(counts[k])}</b> ${esc(k)}</span>`).join('');
    if(!shown.length){ els.resultsBody.innerHTML='<tr><td colspan="7" class="empty">Aucun résultat pour ce filtre.</td></tr>'; drawResultsOnMap([]); return; }
    els.resultsBody.innerHTML=shown.map(r=>{
      const site=r.website?`<a href="${esc(r.website)}" target="_blank" rel="noopener">Site</a>`:'';
      const gm=r.googleMapsUrl?`<a href="${esc(r.googleMapsUrl)}" target="_blank" rel="noopener">Maps</a>`:'';
      return `<tr><td class="priority">${esc(r.priority)}</td><td><b>${esc(r.name)}</b><br><span class="muted">${esc(r.address||'')}</span></td><td>${esc(r.category||'—')}</td><td>${esc(km(r.distanceFromCenterM))}</td><td class="score">${esc(r.opportunityScore)}</td><td>${esc(r.source||'')}</td><td>${[site,gm].filter(Boolean).join(' · ')||'—'}</td></tr>`;
    }).join('');
    drawResultsOnMap(shown);
  }

  async function health(){
    try{
      const r=await fetch('/api/opportunities/health',{cache:'no-store'}); const j=await r.json(); if(!j.ok)throw new Error();
      els.backendChip.textContent=`Backend ${j.version} ✓`; els.backendChip.className='chip ok'; els.osmChip.className='chip ok';
      state.googleAvailable=!!(j.sources&&j.sources.googlePlaces); els.googleChip.textContent=state.googleAvailable?'Google Places ✓':'Google Places non configuré'; els.googleChip.className=state.googleAvailable?'chip ok':'chip warn'; els.useGoogle.disabled=!state.googleAvailable; els.useGoogle.checked=state.googleAvailable;
    }catch(_){ els.backendChip.textContent='Backend indisponible'; els.backendChip.className='chip warn'; }
  }

  async function geocode(){
    const q=els.place.value.trim(); if(q.length<2)return;
    els.geocodeBtn.disabled=true; setProgress(5,'Géocodage côté serveur…');
    try{
      const r=await fetch(`/api/opportunities/geocode?q=${encodeURIComponent(q)}`,{cache:'no-store'}); const j=await r.json(); if(!j.ok||!j.results.length)throw new Error(j.error||'Lieu introuvable');
      const x=j.results[0]; els.lat.value=x.lat.toFixed(6); els.lng.value=x.lng.toFixed(6); state.center={lat:x.lat,lng:x.lng}; if(state.map){state.map.setView([x.lat,x.lng],13);drawCenter();} setProgress(0,`Centre : ${x.displayName}`);
    }catch(e){setProgress(0,e.message||'Géocodage impossible',true);} finally{els.geocodeBtn.disabled=false;}
  }

  async function consumeNdjson(response){
    if(!response.ok) throw new Error(`HTTP ${response.status}`);
    if(!response.body||!response.body.getReader){ const text=await response.text(); text.split(/\r?\n/).filter(Boolean).forEach(line=>handleEvent(JSON.parse(line))); return; }
    const reader=response.body.getReader(); const decoder=new TextDecoder(); let buf='';
    while(true){ const {value,done}=await reader.read(); if(done)break; buf+=decoder.decode(value,{stream:true}); const lines=buf.split(/\r?\n/); buf=lines.pop()||''; for(const line of lines)if(line.trim())handleEvent(JSON.parse(line)); }
    if(buf.trim())handleEvent(JSON.parse(buf));
  }
  function handleEvent(evt){
    if(evt.type==='progress'||evt.type==='warning') setProgress(evt.progress,evt.message,evt.type==='warning');
    else if(evt.type==='error') throw new Error(evt.message||'Erreur serveur');
    else if(evt.type==='result'){
      state.results=(evt.data&&evt.data.results)||[]; setProgress(100,`${state.results.length} résultats • ${(Number(evt.data.durationMs||0)/1000).toFixed(1)} s`); render(); document.querySelectorAll('.exportBtn').forEach(b=>b.disabled=!state.results.length);
    }
  }

  async function search(){
    const lat=Number(els.lat.value),lng=Number(els.lng.value); if(!Number.isFinite(lat)||!Number.isFinite(lng)){setProgress(0,'Latitude/longitude invalides.',true);return;}
    state.center={lat,lng}; drawCenter(); els.searchBtn.disabled=true; state.results=[]; render(); setProgress(2,'Envoi du job au backend…');
    const payload={lat,lng,radiusM:Number(els.radius.value),keywords:keywords(),minScore:Number(els.minScore.value),useGoogle:els.useGoogle.checked};
    try{
      const r=await fetch('/api/opportunities/search',{method:'POST',headers:{'content-type':'application/json','accept':'application/x-ndjson'},body:JSON.stringify(payload)});
      await consumeNdjson(r);
    }catch(e){ setProgress(100,e.message||'Recherche impossible',true); }
    finally{els.searchBtn.disabled=false;}
  }

  async function exportRows(format){
    if(!state.results.length)return; setProgress(100,`Génération ${format.toUpperCase()} côté serveur…`);
    try{
      const r=await fetch('/api/opportunities/export',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({format,results:state.results})});
      if(!r.ok){const j=await r.json().catch(()=>({}));throw new Error(j.error||`Export HTTP ${r.status}`);} const blob=await r.blob();
      const cd=r.headers.get('content-disposition')||''; const m=/filename="?([^";]+)"?/i.exec(cd); const name=m?m[1]:`draftwa-opportunities.${format}`;
      const url=URL.createObjectURL(blob); const a=document.createElement('a'); a.href=url; a.download=name; a.rel='noopener'; document.body.appendChild(a); a.click(); a.remove(); setTimeout(()=>URL.revokeObjectURL(url),30000); setProgress(100,`${name} généré.`);
    }catch(e){setProgress(100,`${e.message||'Export impossible'} — utilise « Navigateur » dans l’APK si le WebView bloque les téléchargements.`,true);}
  }

  els.radius.addEventListener('input',()=>{els.radiusLabel.textContent=`${(Number(els.radius.value)/1000).toFixed(1).replace('.',',')} km`;drawCenter();});
  els.geocodeBtn.addEventListener('click',geocode); els.searchBtn.addEventListener('click',search); els.resultFilter.addEventListener('input',render); els.priorityFilter.addEventListener('change',render); els.maxRows.addEventListener('change',render);
  document.querySelectorAll('.exportBtn').forEach(b=>b.addEventListener('click',()=>exportRows(b.dataset.format)));
  initMap(); health();
})();
