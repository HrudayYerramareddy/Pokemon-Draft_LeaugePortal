const previousDraftRenderer=renderDraft;
const previousBidRenderer=renderBids;

function bstOf(meta){return (meta?.stats||[]).reduce((sum,s)=>sum+(Number(s.value)||0),0)}
function pokemonDetailsModal(name,meta,price){
  const old=document.querySelector('.pokemon-detail-overlay');if(old)old.remove();
  const stats=meta?.stats||[],bst=bstOf(meta);
  const overlay=document.createElement('div');overlay.className='pokemon-detail-overlay';
  overlay.innerHTML=`<div class="pokemon-detail-card"><button class="detail-close" aria-label="Close">×</button><div class="detail-art">${meta?.image?`<img src="${meta.image}" alt="${esc(name)}">`:''}</div><div class="detail-info"><div class="eyebrow">Pokémon Details</div><h2>${esc(name)}</h2><div class="detail-badges"><span class="price-badge-static">$${price??0}</span><span class="bst-badge">BST ${bst||'—'}</span></div><p><strong>Abilities:</strong> ${meta?.abilities?.length?meta.abilities.join(' · '):'Unknown'}</p><div class="stat-list">${stats.map(s=>`<div><span>${esc(s.name)}</span><strong>${s.value}</strong></div>`).join('')}</div></div></div>`;
  document.body.appendChild(overlay);overlay.querySelector('.detail-close').onclick=()=>overlay.remove();overlay.onclick=e=>{if(e.target===overlay)overlay.remove()};
}

renderStandings=async function(){
  const s=await api('/api/standings');
  const panel=(name,rows)=>`<section class="glass-panel team-view"><div class="eyebrow">Division ${name}</div><h2>Division ${name}</h2><div class="standing-row standing-labels"><span>#</span><span>Team</span><span>W</span><span>L</span><span>Diff</span></div>${rows.map((t,i)=>`<div class="standing-row"><span class="standing-rank">${i+1}</span><span class="standing-team">${esc(t.name)}</span><span>${t.wins}</span><span>${t.losses}</span><span>${t.differential>=0?'+':''}${t.differential}</span></div>`).join('')}</section>`;
  $('#page-standings').innerHTML=`${hero('Regular Season','Standings','Wins → Differential → Head-to-Head → Team')}<div class="standings-grid">${panel('A',s.A)}${panel('B',s.B)}</div>`;
};

renderDraft=async function(){
  await previousDraftRenderer();const d=await api('/api/draft');const page=$('#page-draft');
  const h=[...page.querySelectorAll('h2')].find(x=>x.textContent.includes('Available'));if(h){const b=document.createElement('span');b.className='pool-count';b.textContent=`${d.available.length} Pokémon left`;h.insertAdjacentElement('afterend',b)}
  const price=page.querySelector('#draft-price');if(price&&!price.querySelector('option[value="bst-high"]')){price.insertAdjacentHTML('beforeend','<option value="bst-high">BST High → Low</option><option value="bst-low">BST Low → High</option>')}
  const cards=()=>[...page.querySelectorAll('.pokemon-card')];
  await Promise.all(d.available.map(async p=>{const m=await getPokemonMeta(p.name);m.stats=m.stats||[];pokemonMetaCache.set(p.name,m)}));
  cards().forEach(c=>c.addEventListener('dblclick',async e=>{e.preventDefault();e.stopPropagation();const p=d.available.find(x=>String(x.id)===c.dataset.pokemonId);if(p)pokemonDetailsModal(p.name,await getPokemonMeta(p.name),p.price)}));
  page.querySelectorAll('.pokemon-card').forEach(c=>{c.title='Click to select · double-click for stats'});
  if(price){const old=price.onchange;price.onchange=async()=>{if(price.value!=='bst-high'&&price.value!=='bst-low'){if(old)old();return}const grid=page.querySelector('#draft-grid');const sorted=await Promise.all(d.available.map(async p=>({p,bst:bstOf(await getPokemonMeta(p.name))})));sorted.sort((a,b)=>price.value==='bst-high'?b.bst-a.bst:a.bst-b.bst);grid.innerHTML=sorted.map(x=>pokemonCard(x.p)).join('');await hydratePokemonCards(grid);grid.querySelectorAll('.pokemon-card').forEach(c=>c.ondblclick=async e=>{e.preventDefault();const p=d.available.find(x=>String(x.id)===c.dataset.pokemonId);if(p)pokemonDetailsModal(p.name,await getPokemonMeta(p.name),p.price)})}}
};

renderBids=async function(){
  await previousBidRenderer();const free=await api('/api/pokemon/free-agents');const root=$('#fa-content');
  const heading=[...root.querySelectorAll('h2')].find(h=>h.textContent.includes('Free Agent'));if(heading){const b=document.createElement('span');b.className='pool-count';b.textContent=`${free.length} free agents`;heading.insertAdjacentElement('afterend',b)}
  const grid=root.querySelector('#fa-grid');if(grid){grid.classList.add('pokemon-card-grid','force-fa-grid');await hydratePokemonCards(grid)}
};
