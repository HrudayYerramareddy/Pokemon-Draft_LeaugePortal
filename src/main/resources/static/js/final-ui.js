const previousDraftRenderer=renderDraft;
const previousBidRenderer=renderBids;

renderStandings=async function(){
  const s=await api('/api/standings');
  function divisionPanel(name,rows){
    return `<section class="glass-panel team-view"><div class="eyebrow">Division ${name}</div><h2>Division ${name}</h2><div class="standing-row standing-labels"><span>#</span><span>Team</span><span>W</span><span>L</span><span>Diff</span></div>${rows.map((t,i)=>`<div class="standing-row"><span class="standing-rank">${i+1}</span><span class="standing-team">${esc(t.name)}</span><span>${t.wins}</span><span>${t.losses}</span><span>${t.differential>=0?'+':''}${t.differential}</span></div>`).join('')}</section>`;
  }
  $('#page-standings').innerHTML=`${hero('Regular Season','Standings','Wins → Differential → Head-to-Head → Team')}<div class="standings-grid">${divisionPanel('A',s.A)}${divisionPanel('B',s.B)}</div>`;
};

renderDraft=async function(){
  await previousDraftRenderer();
  const d=await api('/api/draft');
  const headings=Array.from($('#page-draft').querySelectorAll('h2'));
  const heading=headings.find(h=>h.textContent.includes('Available'));
  if(heading){
    const badge=document.createElement('span');
    badge.className='pool-count';
    badge.textContent=`${d.available.length} Pokemon left`;
    heading.insertAdjacentElement('afterend',badge);
  }
};

renderBids=async function(){
  await previousBidRenderer();
  const free=await api('/api/pokemon/free-agents');
  const headings=Array.from($('#fa-content').querySelectorAll('h2'));
  const heading=headings.find(h=>h.textContent.includes('Free Agent'));
  if(heading){
    const badge=document.createElement('span');
    badge.className='pool-count';
    badge.textContent=`${free.length} free agents`;
    heading.insertAdjacentElement('afterend',badge);
  }
};
