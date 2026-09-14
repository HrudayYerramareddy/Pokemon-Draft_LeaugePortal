/* Final UI overrides: standings, draft details/BST, and free-agent card grids. */

async function getPokemonFullMeta(name){
  const base=await getPokemonMeta(name);
  if(base.stats&&base.stats.length)return base;
  try{
    let r=await fetch(`https://pokeapi.co/api/v2/pokemon/${pokeSlug(name)}`);
    if(!r.ok&&name.includes('('))r=await fetch(`https://pokeapi.co/api/v2/pokemon/${pokeSlug(name.split(' (')[0])}`);
    if(!r.ok)return {...base,stats:[]};
    const data=await r.json();
    const full={...base,stats:(data.stats||[]).map(s=>({name:prettyPokeText(s.stat.name),value:s.base_stat}))};
    pokemonMetaCache.set(name,full);
    return full;
  }catch{return {...base,stats:[]}}
}
function bstOf(meta){return (meta?.stats||[]).reduce((sum,s)=>sum+(Number(s.value)||0),0)}
function showPokemonDetails(p){
  getPokemonFullMeta(p.name).then(meta=>{
    document.querySelector('.pokemon-detail-overlay')?.remove();
    const overlay=document.createElement('div');overlay.className='pokemon-detail-overlay';
    overlay.innerHTML=`<div class="pokemon-detail-card">
      <button class="detail-close" aria-label="Close">×</button>
      <div class="detail-art">${meta.image?`<img src="${meta.image}" alt="${esc(p.name)}">`:''}</div>
      <div class="detail-info"><div class="eyebrow">Pokémon Details</div><h2>${esc(p.name)}</h2>
        <div class="detail-badges"><span>$${p.price??0}</span><span>BST ${bstOf(meta)||'—'}</span></div>
        <p><strong>Abilities:</strong> ${meta.abilities?.length?meta.abilities.join(' · '):'Unknown'}</p>
        <div class="stat-list">${(meta.stats||[]).map(s=>`<div><span>${esc(s.name)}</span><strong>${s.value}</strong></div>`).join('')||'<p class="muted">Stats unavailable.</p>'}</div>
      </div></div>`;
    document.body.appendChild(overlay);
    overlay.querySelector('.detail-close').onclick=()=>overlay.remove();
    overlay.onclick=e=>{if(e.target===overlay)overlay.remove()};
  });
}
function finalFilterBar(prefix,showBst=true){return `<div class="pokemon-filters">
  <div class="filter-field wide"><label>Pokémon</label><input id="${prefix}-search" placeholder="Search Pokémon..."></div>
  <div class="filter-field"><label>Sort</label><select id="${prefix}-sort"><option value="name">Name A–Z</option><option value="price-low">Price Low → High</option><option value="price-high">Price High → Low</option>${showBst?'<option value="bst-high">BST High → Low</option><option value="bst-low">BST Low → High</option>':''}</select></div>
  <div class="filter-field"><label>Move</label><input id="${prefix}-move" list="${prefix}-move-list" placeholder="Type a move..."><datalist id="${prefix}-move-list"></datalist></div>
  <div class="filter-field"><label>Ability</label><input id="${prefix}-ability" list="${prefix}-ability-list" placeholder="Type an ability..."><datalist id="${prefix}-ability-list"></datalist></div>
  <button type="button" class="secondary" id="${prefix}-clear">Clear</button>
</div>`}
async function wireFinalFilters(prefix,source,draw){
  const apply=async()=>{
    const q=$(`#${prefix}-search`).value.trim().toLowerCase(),move=$(`#${prefix}-move`).value.trim().toLowerCase(),ability=$(`#${prefix}-ability`).value.trim().toLowerCase(),sort=$(`#${prefix}-sort`).value;
    const rows=[];
    for(const p of source){
      if(!p.name.toLowerCase().includes(q))continue;
      const m=(move||ability||sort.startsWith('bst'))?await getPokemonFullMeta(p.name):null;
      if(move&&!m.moves.some(x=>x.toLowerCase().includes(move)))continue;
      if(ability&&!m.abilities.some(x=>x.toLowerCase().includes(ability)))continue;
      rows.push({p,m});
    }
    rows.sort((a,b)=>sort==='price-low'?(a.p.price-b.p.price)||a.p.name.localeCompare(b.p.name):sort==='price-high'?(b.p.price-a.p.price)||a.p.name.localeCompare(b.p.name):sort==='bst-high'?(bstOf(b.m)-bstOf(a.m))||a.p.name.localeCompare(b.p.name):sort==='bst-low'?(bstOf(a.m)-bstOf(b.m))||a.p.name.localeCompare(b.p.name):a.p.name.localeCompare(b.p.name));
    draw(rows.map(x=>x.p));
  };
  ['search','move','ability'].forEach(x=>$(`#${prefix}-${x}`).addEventListener('input',apply));$(`#${prefix}-sort`).addEventListener('change',apply);
  $(`#${prefix}-clear`).onclick=()=>{$(`#${prefix}-search`).value='';$(`#${prefix}-move`).value='';$(`#${prefix}-ability`).value='';$(`#${prefix}-sort`).value='name';apply()};
  (async()=>{const moves=new Set(),abilities=new Set();let i=0;async function worker(){while(i<source.length){const p=source[i++],m=await getPokemonMeta(p.name);m.moves.forEach(x=>moves.add(x));m.abilities.forEach(x=>abilities.add(x))}}await Promise.all(Array.from({length:8},worker));const ml=$(`#${prefix}-move-list`),al=$(`#${prefix}-ability-list`);if(ml)ml.innerHTML=[...moves].sort().map(x=>`<option value="${esc(x)}"></option>`).join('');if(al)al.innerHTML=[...abilities].sort().map(x=>`<option value="${esc(x)}"></option>`).join('')})();
}

renderStandings=async function(){
  const s=await api('/api/standings');const panel=(name,rows)=>`<section class="glass-panel team-view"><div class="eyebrow">Division ${name}</div><h2>Division ${name}</h2><div class="standing-row standing-labels"><span>#</span><span>Team</span><span>W</span><span>L</span><span>Diff</span></div>${rows.map((t,i)=>`<div class="standing-row"><span class="standing-rank">${i+1}</span><span class="standing-team">${esc(t.name)}</span><span>${t.wins}</span><span>${t.losses}</span><span>${t.differential>=0?'+':''}${t.differential}</span></div>`).join('')}</section>`;
  $('#page-standings').innerHTML=`${hero('Regular Season','Standings','Wins → Differential → Head-to-Head → Team')}<div class="standings-grid">${panel('A',s.A)}${panel('B',s.B)}</div>`;
};

renderDraft=async function(){
  const d=await api('/api/draft'),canPick=state.user.role==='MANAGER'||(d.currentTeam&&d.currentTeam.id===state.user.teamId);let chosen=null;
  $('#page-draft').innerHTML=`<div class="section-title"><div><h1>Draft Room</h1><p class="muted">10 rounds · ${d.settings.snakeDraft?'snake':'linear'} draft</p></div>${state.user.role==='MANAGER'?'<button id="undo-pick" class="danger">Undo Last Pick</button>':''}</div>
  <div class="card on-clock"><div><span class="eyebrow">ON THE CLOCK</span><h2>${d.currentTeam?esc(d.currentTeam.name):'Draft complete'}</h2></div><button id="make-pick" class="primary" ${canPick?'':'disabled'}>Draft Selected Pokémon</button></div>
  <div class="card"><div class="section-title"><div><h2>Available Pokémon</h2><p class="muted">Click a Pokémon to select it and open its stats.</p></div><span class="pool-count">${d.available.length} Pokémon left</span></div>${finalFilterBar('draft',true)}<p id="draft-selection" class="selection-status muted">Select a Pokémon card.</p><div id="draft-grid" class="pokemon-card-grid"></div></div>
  <div class="card"><h2>Draft Board</h2><div class="table-wrap"><table><thead><tr><th>Pick</th><th>Round</th><th>Team</th><th>Pokémon</th></tr></thead><tbody>${d.picks.map(p=>`<tr><td>${p.overallPick}</td><td>${p.round}</td><td>${esc(p.teamName)}</td><td>${esc(p.pokemonName)}</td></tr>`).join('')||'<tr><td colspan="4">No picks yet</td></tr>'}</tbody></table></div></div>`;
  const draw=rows=>{const grid=$('#draft-grid');grid.innerHTML=rows.map(p=>pokemonCard(p,chosen===p.id?'selected':'')).join('')||'<div class="empty-state">No Pokémon match these filters.</div>';grid.querySelectorAll('.pokemon-card').forEach(c=>c.onclick=()=>{chosen=Number(c.dataset.pokemonId);const p=d.available.find(x=>x.id===chosen);grid.querySelectorAll('.pokemon-card').forEach(x=>x.classList.toggle('selected',x===c));$('#draft-selection').innerHTML=`Selected: <strong>${esc(p.name)}</strong> · $${p.price}`;showPokemonDetails(p)});hydratePokemonCards(grid)};
  draw(d.available);wireFinalFilters('draft',d.available,draw);
  $('#make-pick').onclick=async()=>{if(!chosen)return toast('Select a Pokémon card first',true);try{await api('/api/draft/pick',{method:'POST',body:{pokemonId:chosen}});toast('Pick recorded');document.querySelector('.pokemon-detail-overlay')?.remove();await renderDraft()}catch(e){toast(e.message,true)}};
  if($('#undo-pick'))$('#undo-pick').onclick=async()=>{try{await api('/api/manager/draft/undo',{method:'POST'});toast('Last pick undone');await renderDraft()}catch(e){toast(e.message,true)}};
};

renderBids=async function(){
  const list=await api('/api/free-agency/bids'),free=await api('/api/pokemon/free-agents');
  if(state.user.role==='MANAGER'){
    $('#fa-content').innerHTML=`<div class="card"><div class="section-title"><div><div class="eyebrow">Commissioner</div><h2>Blind FAAB Processing</h2><p class="muted">All undrafted Pokémon remain in the free-agent pool.</p></div><button id="process-bids" class="primary">Process Pending Bids</button></div></div>
    <div class="card"><div class="section-title"><div><h2>Free Agent Pool</h2><p class="muted">Browse every currently undrafted Pokémon.</p></div><span class="pool-count">${free.length} free agents</span></div>${finalFilterBar('fa',true)}<div id="fa-grid" class="pokemon-card-grid force-fa-grid"></div></div><div class="card"><h2>Pending Bids</h2>${bidTable(list)}</div>`;
    const draw=rows=>{const grid=$('#fa-grid');grid.innerHTML=rows.map(p=>pokemonCard(p)).join('')||'<div class="empty-state">No free agents match.</div>';grid.querySelectorAll('.pokemon-card').forEach(c=>c.onclick=()=>{const p=free.find(x=>String(x.id)===c.dataset.pokemonId);if(p)showPokemonDetails(p)});hydratePokemonCards(grid)};draw(free);wireFinalFilters('fa',free,draw);
    $('#process-bids').onclick=async()=>{try{const r=await api('/api/manager/free-agency/process',{method:'POST'});toast(`Processed bids: ${r.winners} winners`);await renderBids()}catch(e){toast(e.message,true)}};return;
  }
  const roster=await api(`/api/rosters/${state.user.teamId}`),me=state.teams.find(t=>t.id===state.user.teamId);let wanted=null;
  $('#fa-content').innerHTML=`<div class="card"><div class="section-title"><div><h2>Free Agent Pool</h2><p class="muted">Every undrafted Pokémon appears here automatically.</p></div><span class="pool-count">${free.length} free agents</span></div>${finalFilterBar('fa',true)}<p id="fa-selection" class="selection-status muted">Select the Pokémon you want.</p><div id="fa-grid" class="pokemon-card-grid force-fa-grid"></div><div class="selection-footer"><span id="fa-selected" class="muted">No Pokémon selected</span><div class="toolbar" style="margin:0"><select id="bid-drop"><option value="">No drop / open slot</option>${roster.map(p=>`<option value="${p.pokemonId}">${esc(p.name)}</option>`).join('')}</select><input id="bid-amount" class="glass-input" type="number" min="0" max="${me?.faBudget??0}" placeholder="FAAB $"><button id="submit-bid" class="primary" disabled>Submit Bid</button></div></div></div><div class="card"><h2>My Bids</h2>${bidTable(list)}</div>`;
  const draw=rows=>{const grid=$('#fa-grid');grid.innerHTML=rows.map(p=>pokemonCard(p,wanted===p.id?'selected':'')).join('')||'<div class="empty-state">No free agents match.</div>';grid.querySelectorAll('.pokemon-card').forEach(c=>c.onclick=()=>{wanted=Number(c.dataset.pokemonId);const p=free.find(x=>x.id===wanted);grid.querySelectorAll('.pokemon-card').forEach(x=>x.classList.toggle('selected',x===c));$('#fa-selected').innerHTML=`Selected <strong>${esc(p.name)}</strong> · FAAB $${me?.faBudget??0}`;$('#submit-bid').disabled=false;showPokemonDetails(p)});hydratePokemonCards(grid)};draw(free);wireFinalFilters('fa',free,draw);
  $('#submit-bid').onclick=async()=>{if(!wanted)return toast('Select a free agent first',true);try{await api('/api/free-agency/bids',{method:'POST',body:{pokemonWantedId:wanted,pokemonDroppedId:$('#bid-drop').value?Number($('#bid-drop').value):null,bidAmount:Number($('#bid-amount').value||0)}});toast('Blind bid submitted');document.querySelector('.pokemon-detail-overlay')?.remove();await renderBids()}catch(e){toast(e.message,true)}};
};
