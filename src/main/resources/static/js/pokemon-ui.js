/* Enhanced Pokemon card UI. Loaded after app.js so it can upgrade existing renderers without changing league APIs. */
const pokemonMetaCache=new Map();
const POKE_CACHE_KEY='draftLeaguePokemonMetaV1';
try{Object.entries(JSON.parse(localStorage.getItem(POKE_CACHE_KEY)||'{}')).forEach(([k,v])=>pokemonMetaCache.set(k,v));}catch{}

function pokeSlug(name){
  const special={
    'Raichu (Alolan)':'raichu-alola','Ninetales (Alolan)':'ninetales-alola','Persian (Alolan)':'persian-alola','Arcanine (Hisuian)':'arcanine-hisui',
    'Slowbro (Galarian)':'slowbro-galar','Tauros (Paldean Combat)':'tauros-paldea-combat-breed','Tauros (Paldean Blaze)':'tauros-paldea-blaze-breed','Tauros (Paldean Aqua)':'tauros-paldea-aqua-breed',
    'Typhlosion (Hisuian)':'typhlosion-hisui','Slowking (Galarian)':'slowking-galar','Samurott (Hisuian)':'samurott-hisui','Zoroark (Hisuian)':'zoroark-hisui',
    'Stunfisk (Galarian)':'stunfisk-galar','Goodra (Hisuian)':'goodra-hisui','Avalugg (Hisuian)':'avalugg-hisui','Decidueye (Hisuian)':'decidueye-hisui',
    'Indeedee (Male)':'indeedee-male','Indeedee (Female)':'indeedee-female','Basculegion (Male)':'basculegion-male','Basculegion (Female)':'basculegion-female',
    'Toxtricity (Amped)':'toxtricity-amped','Toxtricity (Low Key)':'toxtricity-low-key','Squawkabilly (Green)':'squawkabilly-green-plumage','Squawkabilly (Blue)':'squawkabilly-blue-plumage',
    'Squawkabilly (Yellow)':'squawkabilly-yellow-plumage','Squawkabilly (White)':'squawkabilly-white-plumage','Floette (Eternal Flower)':'floette-eternal'
  };
  if(special[name])return special[name];
  return name.toLowerCase().replace(/[.'’]/g,'').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');
}
function prettyPokeText(s){return String(s||'').split('-').map(x=>x?x[0].toUpperCase()+x.slice(1):'').join(' ')}
async function getPokemonMeta(name){
  if(pokemonMetaCache.has(name))return pokemonMetaCache.get(name);
  let slug=pokeSlug(name),data;
  try{
    let r=await fetch(`https://pokeapi.co/api/v2/pokemon/${slug}`);
    if(!r.ok&&name.includes('('))r=await fetch(`https://pokeapi.co/api/v2/pokemon/${pokeSlug(name.split(' (')[0])}`);
    if(!r.ok)throw new Error('not found');
    data=await r.json();
    const meta={
      image:data.sprites?.other?.['official-artwork']?.front_default||data.sprites?.front_default||'',
      abilities:(data.abilities||[]).map(a=>prettyPokeText(a.ability.name)),
      moves:(data.moves||[]).map(m=>prettyPokeText(m.move.name))
    };
    pokemonMetaCache.set(name,meta);
    try{localStorage.setItem(POKE_CACHE_KEY,JSON.stringify(Object.fromEntries(pokemonMetaCache)));}catch{}
    return meta;
  }catch{return {image:'',abilities:[],moves:[]}}
}
function pokemonCard(p,extra=''){
  const id=p.pokemonId??p.id;
  return `<button type="button" class="pokemon-card ${extra}" data-pokemon-id="${id}" data-pokemon-name="${esc(p.name)}">
    <div class="pokemon-art"><div class="poke-loader">•••</div><img alt="${esc(p.name)}" loading="lazy"></div>
    <div class="pokemon-card-body"><strong>${esc(p.name)}</strong>${p.price!==undefined?`<span class="price-badge">$${p.price}</span>`:''}<div class="poke-meta muted"></div></div>
  </button>`;
}
async function hydratePokemonCards(root=document){
  const cards=[...root.querySelectorAll('.pokemon-card')];
  let cursor=0;
  async function worker(){while(cursor<cards.length){const card=cards[cursor++],name=card.dataset.pokemonName;const m=await getPokemonMeta(name);const img=card.querySelector('img'),loader=card.querySelector('.poke-loader');if(m.image){img.src=m.image;img.classList.add('loaded')}if(loader)loader.remove();const line=card.querySelector('.poke-meta');if(line&&m.abilities.length)line.textContent=m.abilities.slice(0,2).join(' · ');}}
  await Promise.all(Array.from({length:Math.min(10,cards.length)},worker));
}
function advancedFilterBar(prefix){return `<div class="pokemon-filters">
  <div class="filter-field wide"><label>Pokémon</label><input id="${prefix}-search" placeholder="Search Pokémon..."></div>
  <div class="filter-field"><label>Price</label><select id="${prefix}-price"><option value="name">Name A–Z</option><option value="low">Price Low → High</option><option value="high">Price High → Low</option></select></div>
  <div class="filter-field"><label>Move</label><input id="${prefix}-move" list="${prefix}-move-list" placeholder="Type a move..."><datalist id="${prefix}-move-list"></datalist></div>
  <div class="filter-field"><label>Ability</label><input id="${prefix}-ability" list="${prefix}-ability-list" placeholder="Type an ability..."><datalist id="${prefix}-ability-list"></datalist></div>
  <button type="button" class="secondary clear-poke-filters" id="${prefix}-clear">Clear</button>
</div>`}
async function wireAdvancedFilters(prefix,pokemon,draw){
  const names=[...pokemon];
  let metadataReady=false;
  const apply=async()=>{
    const q=$(`#${prefix}-search`).value.trim().toLowerCase(),move=$(`#${prefix}-move`).value.trim().toLowerCase(),ability=$(`#${prefix}-ability`).value.trim().toLowerCase(),sort=$(`#${prefix}-price`).value;
    let rows=names.filter(p=>p.name.toLowerCase().includes(q));
    if(move||ability){
      const tested=[];for(const p of rows){const m=await getPokemonMeta(p.name);if(move&&!m.moves.some(x=>x.toLowerCase().includes(move)))continue;if(ability&&!m.abilities.some(x=>x.toLowerCase().includes(ability)))continue;tested.push(p)}rows=tested;
    }
    rows.sort((a,b)=>sort==='low'?(a.price-b.price)||a.name.localeCompare(b.name):sort==='high'?(b.price-a.price)||a.name.localeCompare(b.name):a.name.localeCompare(b.name));
    draw(rows);
  };
  ['search','move','ability'].forEach(x=>$(`#${prefix}-${x}`).addEventListener('input',apply));$(`#${prefix}-price`).addEventListener('change',apply);
  $(`#${prefix}-clear`).addEventListener('click',()=>{$(`#${prefix}-search`).value='';$(`#${prefix}-move`).value='';$(`#${prefix}-ability`).value='';$(`#${prefix}-price`).value='name';apply()});
  // Populate searchable move/ability suggestions in the background.
  (async()=>{const moves=new Set(),abilities=new Set();let i=0;async function w(){while(i<names.length){const p=names[i++],m=await getPokemonMeta(p.name);m.moves.forEach(x=>moves.add(x));m.abilities.forEach(x=>abilities.add(x));}}await Promise.all(Array.from({length:10},w));$(`#${prefix}-move-list`).innerHTML=[...moves].sort().map(x=>`<option value="${esc(x)}"></option>`).join('');$(`#${prefix}-ability-list`).innerHTML=[...abilities].sort().map(x=>`<option value="${esc(x)}"></option>`).join('');metadataReady=true;})();
}

renderDraft=async function(){
  const d=await api('/api/draft'),canPick=state.user.role==='MANAGER'||(d.currentTeam&&d.currentTeam.id===state.user.teamId);let chosen=null;
  $('#page-draft').innerHTML=`<div class="section-title"><div><h1>Draft Room</h1><p class="muted">10 rounds · ${d.settings.snakeDraft?'snake':'linear'} draft</p></div>${state.user.role==='MANAGER'?'<button id="undo-pick" class="danger">Undo Last Pick</button>':''}</div>
  <div class="card on-clock"><div><span class="eyebrow">ON THE CLOCK</span><h2>${d.currentTeam?esc(d.currentTeam.name):'Draft complete'}</h2></div><button id="make-pick" class="primary" ${canPick?'':'disabled'}>Draft Selected Pokémon</button></div>
  <div class="card"><h2>Available Pokémon</h2>${advancedFilterBar('draft')}<p id="draft-selection" class="selection-status muted">Select a Pokémon card to draft it.</p><div id="draft-grid" class="pokemon-card-grid"></div></div>
  <div class="card"><h2>Draft Board</h2><div class="table-wrap"><table><thead><tr><th>Pick</th><th>Round</th><th>Team</th><th>Pokémon</th></tr></thead><tbody>${d.picks.map(p=>`<tr><td>${p.overallPick}</td><td>${p.round}</td><td>${esc(p.teamName)}</td><td>${esc(p.pokemonName)}</td></tr>`).join('')||'<tr><td colspan="4">No picks yet</td></tr>'}</tbody></table></div></div>`;
  const draw=rows=>{const grid=$('#draft-grid');grid.innerHTML=rows.map(p=>pokemonCard(p,chosen===p.id?'selected':'')).join('')||'<div class="empty-state">No Pokémon match these filters.</div>';grid.querySelectorAll('.pokemon-card').forEach(c=>c.onclick=()=>{chosen=Number(c.dataset.pokemonId);grid.querySelectorAll('.pokemon-card').forEach(x=>x.classList.toggle('selected',x===c));$('#draft-selection').innerHTML=`Selected: <strong>${esc(c.dataset.pokemonName)}</strong>`});hydratePokemonCards(grid)};
  draw(d.available);wireAdvancedFilters('draft',d.available,draw);
  $('#make-pick').onclick=async()=>{if(!chosen)return toast('Select a Pokémon card first',true);try{await api('/api/draft/pick',{method:'POST',body:{pokemonId:chosen}});toast('Pick recorded');await renderDraft()}catch(e){toast(e.message,true)}};
  if($('#undo-pick'))$('#undo-pick').onclick=async()=>{try{await api('/api/manager/draft/undo',{method:'POST'});toast('Last pick undone');await renderDraft()}catch(e){toast(e.message,true)}};
};

renderRosters=async function(){
  const data=await api('/api/rosters');
  $('#page-rosters').innerHTML=`<div class="section-title"><div><h1>Rosters</h1><p class="muted">Browse every team and its Pokémon.</p></div><select id="roster-team"><option value="all">All Teams</option>${data.map(r=>`<option value="${r.team.id}">${esc(r.team.name)}</option>`).join('')}</select></div><div id="roster-content"></div>`;
  const draw=()=>{const wanted=$('#roster-team').value,rows=wanted==='all'?data:data.filter(r=>String(r.team.id)===wanted);$('#roster-content').innerHTML=`<div class="roster-team-grid">${rows.map(r=>`<section class="card roster-panel"><div class="roster-heading"><div><span class="eyebrow">DIVISION ${esc(r.team.division)}</span><h2>${esc(r.team.name)}</h2><p class="muted">${esc(r.team.coachName)}</p></div><span class="pill">${r.pokemon.length}/10</span></div><div class="pokemon-card-grid roster-pokemon-grid">${r.pokemon.map(p=>pokemonCard(p)).join('')||'<div class="empty-state">No Pokémon drafted yet.</div>'}</div></section>`).join('')}</div>`;hydratePokemonCards($('#roster-content'))};
  $('#roster-team').onchange=draw;draw();
};

const originalRenderBids=renderBids;
renderBids=async function(){
  if(state.user.role==='MANAGER')return originalRenderBids();
  const list=await api('/api/free-agency/bids'),free=await api('/api/pokemon/free-agents'),myRoster=await api(`/api/rosters/${state.user.teamId}`),me=state.teams.find(t=>t.id===state.user.teamId);let wanted=null,drop=null;
  $('#fa-content').innerHTML=`<div class="card"><div class="section-title"><div><h2>Free Agent Pool</h2><p class="muted">Every undrafted Pokémon appears here automatically.</p></div><span class="budget-chip">FAAB $${me?.faBudget??0}</span></div>${advancedFilterBar('fa')}<p id="fa-selection" class="selection-status muted">Select the Pokémon you want.</p><div id="fa-grid" class="pokemon-card-grid"></div></div>
  <div class="card"><h2>Optional Drop</h2><p class="muted">Choose one of your Pokémon only if your roster is full or you want to replace it.</p><div id="drop-grid" class="pokemon-card-grid compact-grid">${myRoster.map(p=>pokemonCard(p)).join('')||'<div class="empty-state">Your roster is empty.</div>'}</div><div class="bid-actions"><label>FAAB Bid <input id="bid-amount" type="number" min="0" max="${me?.faBudget??0}" value="0"></label><button id="submit-bid" class="primary">Submit Blind Bid</button></div></div>${bidTable(list)}`;
  const draw=rows=>{const grid=$('#fa-grid');grid.innerHTML=rows.map(p=>pokemonCard(p,wanted===p.id?'selected':'')).join('')||'<div class="empty-state">No free agents match these filters.</div>';grid.querySelectorAll('.pokemon-card').forEach(c=>c.onclick=()=>{wanted=Number(c.dataset.pokemonId);grid.querySelectorAll('.pokemon-card').forEach(x=>x.classList.toggle('selected',x===c));$('#fa-selection').innerHTML=`Wanted: <strong>${esc(c.dataset.pokemonName)}</strong>`});hydratePokemonCards(grid)};
  draw(free);wireAdvancedFilters('fa',free,draw);hydratePokemonCards($('#drop-grid'));
  $('#drop-grid').querySelectorAll('.pokemon-card').forEach(c=>c.onclick=()=>{const id=Number(c.dataset.pokemonId);if(drop===id){drop=null;c.classList.remove('selected')}else{drop=id;$('#drop-grid').querySelectorAll('.pokemon-card').forEach(x=>x.classList.toggle('selected',x===c))}});
  $('#submit-bid').onclick=async()=>{if(!wanted)return toast('Select a free agent first',true);try{await api('/api/free-agency/bids',{method:'POST',body:{pokemonWantedId:wanted,pokemonDroppedId:drop,bidAmount:Number($('#bid-amount').value)}});toast('Blind bid submitted');await renderBids()}catch(e){toast(e.message,true)}};
};
