/* Enhanced Pokemon card UI. Loaded after app.js so it can upgrade existing renderers without changing league APIs. */
const pokemonMetaCache=new Map();
const POKE_CACHE_KEY='draftLeaguePokemonMetaV3';
try{Object.entries(JSON.parse(localStorage.getItem(POKE_CACHE_KEY)||'{}')).forEach(([k,v])=>pokemonMetaCache.set(k,v));}catch{}

function pokeSlug(name){
  const special={
    'Raichu (Alolan)':'raichu-alola','Ninetales (Alolan)':'ninetales-alola','Persian (Alolan)':'persian-alola','Arcanine (Hisuian)':'arcanine-hisui',
    'Slowbro (Galarian)':'slowbro-galar','Tauros (Paldean Combat)':'tauros-paldea-combat-breed','Tauros (Paldean Blaze)':'tauros-paldea-blaze-breed','Tauros (Paldean Aqua)':'tauros-paldea-aqua-breed',
    'Typhlosion (Hisuian)':'typhlosion-hisui','Slowking (Galarian)':'slowking-galar','Samurott (Hisuian)':'samurott-hisui','Zoroark (Hisuian)':'zoroark-hisui',
    'Stunfisk (Galarian)':'stunfisk-galar','Goodra (Hisuian)':'goodra-hisui','Avalugg (Hisuian)':'avalugg-hisui','Decidueye (Hisuian)':'decidueye-hisui',
    'Indeedee (Male)':'indeedee-male','Indeedee (Female)':'indeedee-female','Basculegion (Male)':'basculegion-male','Basculegion (Female)':'basculegion-female',
    'Toxtricity (Amped)':'toxtricity-amped','Toxtricity (Low Key)':'toxtricity-low-key','Squawkabilly (Green)':'squawkabilly-green-plumage','Squawkabilly (Blue)':'squawkabilly-blue-plumage',
    'Squawkabilly (Yellow)':'squawkabilly-yellow-plumage','Squawkabilly (White)':'squawkabilly-white-plumage','Floette (Eternal Flower)':'floette-eternal',
    'Aegislash':'aegislash-shield','Gourgeist':'gourgeist-average','Mimikyu':'mimikyu-disguised','Morpeko':'morpeko-full-belly','Palafin':'palafin-zero',
    'Meowstic':'meowstic-male','Meowstic (Male)':'meowstic-male','Meowstic (Female)':'meowstic-female',
    'Lycanroc':'lycanroc-midday','Lycanroc (Midday)':'lycanroc-midday','Lycanroc (Midnight)':'lycanroc-midnight','Lycanroc (Dusk)':'lycanroc-dusk'
  };
  if(special[name])return special[name];
  return name.toLowerCase().replace(/[.'’]/g,'').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');
}
function prettyPokeText(s){return String(s||'').split('-').map(x=>x?x[0].toUpperCase()+x.slice(1):'').join(' ')}
async function getPokemonMeta(name){
  if(pokemonMetaCache.has(name))return pokemonMetaCache.get(name);
  try{
    let r=await fetch(`https://pokeapi.co/api/v2/pokemon/${pokeSlug(name)}`);
    if(!r.ok&&name.includes('('))r=await fetch(`https://pokeapi.co/api/v2/pokemon/${pokeSlug(name.split(' (')[0])}`);
    if(!r.ok)throw new Error('not found');
    const data=await r.json();
    const meta={image:data.sprites?.other?.['official-artwork']?.front_default||data.sprites?.front_default||'',abilities:(data.abilities||[]).map(a=>prettyPokeText(a.ability.name)),moves:(data.moves||[]).map(m=>prettyPokeText(m.move.name)),stats:(data.stats||[]).map(s=>({name:prettyPokeText(s.stat.name),value:s.base_stat}))};
    pokemonMetaCache.set(name,meta);try{localStorage.setItem(POKE_CACHE_KEY,JSON.stringify(Object.fromEntries(pokemonMetaCache)));}catch{}return meta;
  }catch{return {image:'',abilities:[],moves:[],stats:[]}}
}
function pokemonCard(p,extra=''){const id=p.pokemonId??p.id;return `<button type="button" class="pokemon-card ${extra}" data-pokemon-id="${id}" data-pokemon-name="${esc(p.name)}"><div class="pokemon-art"><div class="poke-loader">•••</div><img alt="${esc(p.name)}" loading="lazy"></div><div class="pokemon-card-body"><strong>${esc(p.name)}</strong>${p.price!==undefined?`<span class="price-badge">$${p.price}</span>`:''}<div class="poke-meta muted"></div></div></button>`}
async function hydratePokemonCards(root=document){const cards=[...root.querySelectorAll('.pokemon-card')];let cursor=0;async function worker(){while(cursor<cards.length){const card=cards[cursor++],m=await getPokemonMeta(card.dataset.pokemonName),img=card.querySelector('img'),loader=card.querySelector('.poke-loader');if(m.image){img.src=m.image;img.classList.add('loaded')}if(loader)loader.remove();const line=card.querySelector('.poke-meta');if(line&&m.abilities.length)line.textContent=m.abilities.slice(0,2).join(' · ')}}await Promise.all(Array.from({length:Math.min(10,cards.length)},worker))}
function advancedFilterBar(prefix){return `<div class="pokemon-filters"><div class="filter-field wide"><label>Pokémon</label><input id="${prefix}-search" placeholder="Search Pokémon..."></div><div class="filter-field"><label>Price</label><select id="${prefix}-price"><option value="name">Name A–Z</option><option value="low">Price Low → High</option><option value="high">Price High → Low</option></select></div><div class="filter-field"><label>Move</label><input id="${prefix}-move" list="${prefix}-move-list" placeholder="Type a move..."><datalist id="${prefix}-move-list"></datalist></div><div class="filter-field"><label>Ability</label><input id="${prefix}-ability" list="${prefix}-ability-list" placeholder="Type an ability..."><datalist id="${prefix}-ability-list"></datalist></div><button type="button" class="secondary clear-poke-filters" id="${prefix}-clear">Clear</button></div>`}
async function wireAdvancedFilters(prefix,pokemon,draw){const names=[...pokemon];const apply=async()=>{const q=$(`#${prefix}-search`).value.trim().toLowerCase(),move=$(`#${prefix}-move`).value.trim().toLowerCase(),ability=$(`#${prefix}-ability`).value.trim().toLowerCase(),sort=$(`#${prefix}-price`).value;let rows=names.filter(p=>p.name.toLowerCase().includes(q));if(move||ability){const tested=[];for(const p of rows){const m=await getPokemonMeta(p.name);if(move&&!m.moves.some(x=>x.toLowerCase().includes(move)))continue;if(ability&&!m.abilities.some(x=>x.toLowerCase().includes(ability)))continue;tested.push(p)}rows=tested}rows.sort((a,b)=>sort==='low'?(a.price-b.price)||a.name.localeCompare(b.name):sort==='high'?(b.price-a.price)||a.name.localeCompare(b.name):a.name.localeCompare(b.name));draw(rows)};['search','move','ability'].forEach(x=>$(`#${prefix}-${x}`).addEventListener('input',apply));$(`#${prefix}-price`).addEventListener('change',apply);$(`#${prefix}-clear`).onclick=()=>{$(`#${prefix}-search`).value='';$(`#${prefix}-move`).value='';$(`#${prefix}-ability`).value='';$(`#${prefix}-price`).value='name';apply()};(async()=>{const moves=new Set(),abilities=new Set();let i=0;async function w(){while(i<names.length){const m=await getPokemonMeta(names[i++].name);m.moves.forEach(x=>moves.add(x));m.abilities.forEach(x=>abilities.add(x))}}await Promise.all(Array.from({length:10},w));$(`#${prefix}-move-list`).innerHTML=[...moves].sort().map(x=>`<option value="${esc(x)}"></option>`).join('');$(`#${prefix}-ability-list`).innerHTML=[...abilities].sort().map(x=>`<option value="${esc(x)}"></option>`).join('')})()}

renderDraft=async function(){return window.__legacyPokemonDraft?window.__legacyPokemonDraft():undefined}
renderRosters=async function(){const data=await api('/api/rosters');$('#page-rosters').innerHTML=`<div class="section-title"><div><h1>Rosters</h1><p class="muted">Browse every team and its Pokémon.</p></div><select id="roster-team"><option value="all">All Teams</option>${data.map(r=>`<option value="${r.team.id}">${esc(r.team.name)}</option>`).join('')}</select></div><div id="roster-content"></div>`;const draw=()=>{const wanted=$('#roster-team').value,rows=wanted==='all'?data:data.filter(r=>String(r.team.id)===wanted);$('#roster-content').innerHTML=`<div class="roster-team-grid">${rows.map(r=>`<section class="card roster-panel"><div class="roster-heading"><div><span class="eyebrow">DIVISION ${esc(r.team.division)}</span><h2>${esc(r.team.name)}</h2><p class="muted">${esc(r.team.coachName)}</p></div><span class="pill">${r.pokemon.length}/10</span></div><div class="pokemon-card-grid roster-pokemon-grid">${r.pokemon.map(p=>pokemonCard(p)).join('')||'<div class="empty-state">No Pokémon drafted yet.</div>'}</div></section>`).join('')}</div>`;hydratePokemonCards($('#roster-content'))};$('#roster-team').onchange=draw;draw()}
