const state={user:null,teams:[],settings:null,page:'home'};
const $=s=>document.querySelector(s);const $$=s=>[...document.querySelectorAll(s)];

async function api(url,options={}){
  const opts={headers:{'Content-Type':'application/json'},...options};
  if(options.body&&typeof options.body!=='string')opts.body=JSON.stringify(options.body);
  const res=await fetch(url,opts);
  if(res.status===204)return null;
  let data=null;try{data=await res.json();}catch{data=await res.text();}
  if(!res.ok){const msg=(data&&data.detail)||(data&&data.message)||data||`Request failed (${res.status})`;throw new Error(msg);}
  return data;
}
function toast(msg,error=false){const d=document.createElement('div');d.className='toast'+(error?' error':'');d.textContent=msg;$('#toast').appendChild(d);setTimeout(()=>d.remove(),3500)}
function esc(v){return String(v??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));}
function teamName(id){return state.teams.find(t=>t.id===id)?.name||`Team ${id}`}

async function boot(){
  try{state.user=await api('/api/auth/me');await enterApp();}catch{$('#login-screen').classList.remove('hidden');}
}
async function enterApp(){
  $('#login-screen').classList.add('hidden');$('#app').classList.remove('hidden');
  const dash=await api('/api/dashboard');state.user=dash.user;state.teams=dash.teams;state.settings=dash.settings;
  $('#whoami').innerHTML=`<strong>${esc(state.user.username)}</strong><br>${esc(state.user.role)}${state.user.teamId?` · ${esc(teamName(state.user.teamId))}`:''}`;
  $('#manager-nav').classList.toggle('hidden',state.user.role!=='MANAGER');
  await showPage('home');
}

$('#login-button').addEventListener('click',async()=>{try{state.user=await api('/api/auth/login',{method:'POST',body:{username:$('#login-username').value,password:$('#login-password').value}});await enterApp();}catch(e){toast(e.message,true)}});
$('#login-password').addEventListener('keydown',e=>{if(e.key==='Enter')$('#login-button').click()});
$('#logout-button').addEventListener('click',async()=>{await api('/api/auth/logout',{method:'POST'});location.reload()});
$$('[data-page]').forEach(b=>b.addEventListener('click',()=>showPage(b.dataset.page)));

async function showPage(page){
  state.page=page;$$('.page').forEach(p=>p.classList.add('hidden'));$(`#page-${page}`).classList.remove('hidden');
  $$('[data-page]').forEach(b=>b.classList.toggle('active',b.dataset.page===page));
  try{
    if(page==='home')await renderHome();
    if(page==='draft')await renderDraft();
    if(page==='rosters')await renderRosters();
    if(page==='standings')await renderStandings();
    if(page==='schedule')await renderSchedule();
    if(page==='lineup')await renderLineup();
    if(page==='freeagency')await renderFreeAgency('bids');
    if(page==='manager')await renderManager();
  }catch(e){$(`#page-${page}`).innerHTML=`<div class="card"><h2>Page error</h2><p>${esc(e.message)}</p></div>`;toast(e.message,true)}
}

async function refreshBase(){state.teams=await api('/api/teams');state.settings=await api('/api/settings')}

async function renderHome(){
  const d=await api('/api/dashboard');state.teams=d.teams;state.settings=d.settings;
  const myTeam=state.user.teamId?state.teams.find(t=>t.id===state.user.teamId):null;
  $('#page-home').innerHTML=`
    <h1>${esc(d.settings.leagueName)}</h1><p class="muted">Functional prototype · Week ${d.settings.currentWeek} of ${d.settings.regularSeasonWeeks}</p>
    <div class="grid">
      <div class="card"><h3>League</h3><div class="stat">16</div><p>coaches · 2 divisions of 8</p></div>
      <div class="card"><h3>Draft</h3><div class="stat">${d.settings.draftOpen?'OPEN':'CLOSED'}</div><p>${d.currentDraftTeam?`On the clock: ${esc(d.currentDraftTeam.name)}`:'Draft complete'}</p></div>
      <div class="card"><h3>Playoff scenarios</h3><div class="stat">${d.settings.playoffSeedModeEnabled?'ON':'OFF'}</div><p>${d.settings.playoffSeedModeEnabled?'Two weeks or fewer remain.':'Disabled until only two weeks remain.'}</p></div>
      ${myTeam?`<div class="card"><h3>${esc(myTeam.name)}</h3><div class="stat">${myTeam.wins}-${myTeam.losses}</div><p>Differential ${myTeam.differential>=0?'+':''}${myTeam.differential} · FAAB $${myTeam.faBudget}</p></div>`:''}
    </div>
    <div class="card"><h2>Prototype notes</h2><p>This build prioritizes working league flows over visual polish. Commissioner controls are available from the Commissioner tab. Draft values, divisions, draft order, standings overrides, results, schedule seed, current week and deadlines are editable.</p></div>`;
}

async function renderDraft(){
  const d=await api('/api/draft');
  const canPick=state.user.role==='MANAGER'||(d.currentTeam&&d.currentTeam.id===state.user.teamId);
  $('#page-draft').innerHTML=`<div class="section-title"><div><h1>Draft Room</h1><p class="muted">10 rounds · ${d.settings.snakeDraft?'snake':'linear'} draft</p></div>${state.user.role==='MANAGER'?'<button id="undo-pick" class="danger">Undo Last Pick</button>':''}</div>
    <div class="card"><h2>${d.currentTeam?`${esc(d.currentTeam.name)} is on the clock`:'Draft complete'}</h2>${d.currentTeam?`<p>Draft position ${d.currentTeam.draftPosition}</p>`:''}</div>
    <div class="card"><div class="toolbar"><input id="draft-search" placeholder="Search Pokemon"><select id="draft-pokemon"><option value="">Choose Pokemon</option>${d.available.map(p=>`<option value="${p.id}">${esc(p.name)} — $${p.price}</option>`).join('')}</select><button id="make-pick" class="primary" ${canPick?'':'disabled'}>Draft Pokemon</button></div><p class="small muted">Prices are commissioner-editable prototype draft values. Mega Evolutions are not separate draft slots.</p></div>
    <div class="card"><h2>Draft Board</h2><div class="table-wrap"><table><thead><tr><th>Pick</th><th>Round</th><th>Team</th><th>Pokemon</th></tr></thead><tbody>${d.picks.map(p=>`<tr><td>${p.overallPick}</td><td>${p.round}</td><td>${esc(p.teamName)}</td><td>${esc(p.pokemonName)}</td></tr>`).join('')||'<tr><td colspan="4">No picks yet</td></tr>'}</tbody></table></div></div>`;
  $('#draft-search').addEventListener('input',e=>{const q=e.target.value.toLowerCase();[...$('#draft-pokemon').options].forEach((o,i)=>{if(i)o.hidden=!o.text.toLowerCase().includes(q)})});
  $('#make-pick').addEventListener('click',async()=>{const id=Number($('#draft-pokemon').value);if(!id)return toast('Choose a Pokemon',true);try{await api('/api/draft/pick',{method:'POST',body:{pokemonId:id}});toast('Pick recorded');await renderDraft();}catch(e){toast(e.message,true)}});
  if($('#undo-pick'))$('#undo-pick').addEventListener('click',async()=>{try{await api('/api/manager/draft/undo',{method:'POST'});toast('Last pick undone');await renderDraft();}catch(e){toast(e.message,true)}});
}

async function renderRosters(){
  const data=await api('/api/rosters');
  $('#page-rosters').innerHTML=`<h1>Rosters</h1><p class="muted">Each team can roster up to 10 Pokemon.</p><div class="grid">${data.map(r=>`<div class="card team-card"><h3>${esc(r.team.name)}</h3><p>${esc(r.team.coachName)} · Division ${r.team.division}</p><span class="pill">${r.pokemon.length}/10</span><ul>${r.pokemon.map(p=>`<li>${esc(p.name)} <span class="muted">($${p.price})</span></li>`).join('')||'<li class="muted">No Pokemon drafted yet</li>'}</ul></div>`).join('')}</div>`;
}

async function renderStandings(){
  const s=await api('/api/standings');
  const table=(division,rows)=>`<div class="card"><h2>Division ${division}</h2><div class="table-wrap"><table><thead><tr><th>#</th><th>Team</th><th>W</th><th>L</th><th>Diff</th></tr></thead><tbody>${rows.map((t,i)=>`<tr><td>${i+1}</td><td>${esc(t.name)}</td><td>${t.wins}</td><td>${t.losses}</td><td>${t.differential>=0?'+':''}${t.differential}</td></tr>`).join('')}</tbody></table></div></div>`;
  $('#page-standings').innerHTML=`<h1>Standings</h1><p class="muted">Default order: Wins → Differential → Head-to-Head → Team. Commissioner manual rank overrides are available when needed.</p>${s.seedModeEnabled?'<div class="card good"><strong>Possible-seed mode enabled.</strong> Two or fewer regular-season weeks remain.</div>':'<div class="card warning"><strong>Possible-seed calculations are disabled.</strong> They turn on only when two weeks remain so the site does not waste work on huge scenario sets.</div>'}<div class="grid">${table('A',s.A)}${table('B',s.B)}</div>`;
}

async function renderSchedule(){
  const data=await api('/api/schedule');const by={};data.forEach(m=>(by[m.week]??=[]).push(m));
  $('#page-schedule').innerHTML=`<div class="section-title"><div><h1>Schedule / Results</h1><p class="muted">Weeks 1–7 are divisional round robin. Weeks 8–10 are balanced cross-division matchups.</p></div></div>${Object.keys(by).map(w=>`<div class="card week"><h2>Week ${w}</h2>${by[w].map(m=>`<div class="matchup"><span>${esc(m.homeTeam)}</span><span>${m.played?m.homeScore:'—'}</span><span class="away">${esc(m.awayTeam)}</span><span>${m.played?m.awayScore:'—'}</span>${state.user.role==='MANAGER'?`<div class="toolbar" style="grid-column:1/-1"><input class="score-home inline-input" data-id="${m.id}" type="number" min="0" value="${m.homeScore??''}" placeholder="Home"><input class="score-away inline-input" data-id="${m.id}" type="number" min="0" value="${m.awayScore??''}" placeholder="Away"><button class="primary save-result" data-id="${m.id}">Save Result</button>${m.played?`<button class="secondary clear-result" data-id="${m.id}">Clear</button>`:''}</div>`:''}</div>`).join('')}</div>`).join('')}`;
  $$('.save-result').forEach(b=>b.addEventListener('click',async()=>{const id=b.dataset.id;const h=document.querySelector(`.score-home[data-id="${id}"]`).value,a=document.querySelector(`.score-away[data-id="${id}"]`).value;if(h===''||a==='')return toast('Enter both scores',true);try{await api(`/api/manager/results/${id}`,{method:'POST',body:{homeScore:Number(h),awayScore:Number(a)}});toast('Result saved');await renderSchedule();}catch(e){toast(e.message,true)}}));
  $$('.clear-result').forEach(b=>b.addEventListener('click',async()=>{try{await api(`/api/manager/results/${b.dataset.id}`,{method:'DELETE'});toast('Result cleared');await renderSchedule();}catch(e){toast(e.message,true)}}));
}

async function renderLineup(){
  if(state.user.role==='MANAGER'){$('#page-lineup').innerHTML='<h1>Weekly Lineup</h1><div class="card"><p>Commissioner accounts do not submit a weekly team. You can view revealed submissions after the deadline through the API for now.</p></div>';return;}
  const d=await api('/api/lineup');const selected=new Set((d.submission?.pokemonIdsCsv||'').split(',').filter(Boolean).map(Number));
  $('#page-lineup').innerHTML=`<h1>Weekly Lineup</h1><div class="card"><h2>Week ${d.week}</h2><p>Choose exactly 6 of your rostered Pokemon.</p><p class="muted">Deadline: ${d.deadline?new Date(d.deadline).toLocaleString():'No deadline set yet'}</p><div class="pokemon-grid">${d.roster.map(p=>`<div class="pokemon-option"><label><input class="lineup-check" type="checkbox" value="${p.pokemonId}" ${selected.has(p.pokemonId)?'checked':''}> ${esc(p.name)}</label></div>`).join('')||'<p>No roster yet.</p>'}</div><br><button id="submit-lineup" class="primary">Submit Team of 6</button></div>`;
  $('#submit-lineup').addEventListener('click',async()=>{const ids=$$('.lineup-check:checked').map(x=>Number(x.value));if(ids.length!==6)return toast('Select exactly 6 Pokemon',true);try{await api('/api/lineup',{method:'POST',body:{pokemonIds:ids}});toast('Weekly lineup submitted');await renderLineup();}catch(e){toast(e.message,true)}});
}

async function renderFreeAgency(tab='bids'){
  await refreshBase();
  $('#page-freeagency').innerHTML=`<h1>Free Agency</h1><div class="subtabs"><button data-fa="bids" class="${tab==='bids'?'active':''}">Add / Drop</button><button data-fa="trades" class="${tab==='trades'?'active':''}">Trades</button><button data-fa="transactions" class="${tab==='transactions'?'active':''}">Transactions</button></div><div id="fa-content"></div>`;
  $$('[data-fa]').forEach(b=>b.addEventListener('click',()=>renderFreeAgency(b.dataset.fa)));
  if(tab==='bids')await renderBids();if(tab==='trades')await renderTrades();if(tab==='transactions')await renderTransactions();
}
async function renderBids(){
  const list=await api('/api/free-agency/bids');const free=await api('/api/pokemon/free-agents');
  if(state.user.role==='MANAGER'){
    $('#fa-content').innerHTML=`<div class="card"><div class="section-title"><div><h2>Blind FAAB Processing</h2><p class="muted">Manager can see all bids. Coaches only see their own.</p></div><button id="process-bids" class="primary">Process Pending Bids</button></div></div>${bidTable(list)}`;
    $('#process-bids').addEventListener('click',async()=>{try{const r=await api('/api/manager/free-agency/process',{method:'POST'});toast(`Processed bids: ${r.winners} winners`);await renderBids();}catch(e){toast(e.message,true)}});return;
  }
  const myRoster=await api(`/api/rosters/${state.user.teamId}`);const me=state.teams.find(t=>t.id===state.user.teamId);
  $('#fa-content').innerHTML=`<div class="card"><h2>Submit Blind Bid</h2><p>Budget remaining: <strong>$${me?.faBudget??0}</strong></p><div class="toolbar"><select id="bid-wanted"><option value="">Pokemon wanted</option>${free.map(p=>`<option value="${p.id}">${esc(p.name)}</option>`).join('')}</select><select id="bid-drop"><option value="">No drop / open roster slot</option>${myRoster.map(p=>`<option value="${p.pokemonId}">${esc(p.name)}</option>`).join('')}</select><input id="bid-amount" type="number" min="0" max="${me?.faBudget??100}" value="0" placeholder="Bid"><button id="submit-bid" class="primary">Submit Bid</button></div><p class="small muted">Other coaches cannot see this bid.</p></div>${bidTable(list)}`;
  $('#submit-bid').addEventListener('click',async()=>{const wanted=Number($('#bid-wanted').value),drop=$('#bid-drop').value?Number($('#bid-drop').value):null,amount=Number($('#bid-amount').value);if(!wanted)return toast('Choose a free agent',true);try{await api('/api/free-agency/bids',{method:'POST',body:{wantedPokemonId:wanted,dropPokemonId:drop,amount}});toast('Blind bid submitted');await renderBids();}catch(e){toast(e.message,true)}});
}
function bidTable(list){return `<div class="card"><h2>${state.user.role==='MANAGER'?'All Bids':'My Bids'}</h2><div class="table-wrap"><table><thead><tr><th>Team</th><th>Wanted ID</th><th>Drop ID</th><th>Amount</th><th>Status</th></tr></thead><tbody>${list.map(b=>`<tr><td>${esc(teamName(b.teamId))}</td><td>${b.wantedPokemonId}</td><td>${b.dropPokemonId??'—'}</td><td>$${b.amount}</td><td>${b.status}</td></tr>`).join('')||'<tr><td colspan="5">No bids yet</td></tr>'}</tbody></table></div></div>`}

async function renderTrades(){
  const list=await api('/api/trades');
  if(state.user.role==='MANAGER'){$('#fa-content').innerHTML=`<div class="card"><h2>All Trades</h2>${tradeTable(list)}</div>`;bindTradeButtons();return;}
  const mine=await api(`/api/rosters/${state.user.teamId}`);const all=await api('/api/rosters');
  $('#fa-content').innerHTML=`<div class="card"><h2>Propose Trade</h2><div class="toolbar"><select id="trade-recipient"><option value="">Team</option>${state.teams.filter(t=>t.id!==state.user.teamId).map(t=>`<option value="${t.id}">${esc(t.name)}</option>`).join('')}</select><select id="trade-offered"><option value="">Your Pokemon</option>${mine.map(p=>`<option value="${p.pokemonId}">${esc(p.name)}</option>`).join('')}</select><select id="trade-requested"><option value="">Their Pokemon</option></select><button id="offer-trade" class="primary">Offer Trade</button></div></div><div class="card"><h2>My Trades</h2>${tradeTable(list)}</div>`;
  $('#trade-recipient').addEventListener('change',()=>{const r=all.find(x=>x.team.id===Number($('#trade-recipient').value));$('#trade-requested').innerHTML='<option value="">Their Pokemon</option>'+(r?r.pokemon.map(p=>`<option value="${p.pokemonId}">${esc(p.name)}</option>`).join(''):'')});
  $('#offer-trade').addEventListener('click',async()=>{const recipient=Number($('#trade-recipient').value),offered=Number($('#trade-offered').value),requested=Number($('#trade-requested').value);if(!recipient||!offered||!requested)return toast('Complete all trade fields',true);try{await api('/api/trades',{method:'POST',body:{recipientTeamId:recipient,offeredPokemonId:offered,requestedPokemonId:requested}});toast('Trade offered');await renderTrades();}catch(e){toast(e.message,true)}});bindTradeButtons();
}
function tradeTable(list){return `<div class="table-wrap"><table><thead><tr><th>From</th><th>To</th><th>Offered ID</th><th>Requested ID</th><th>Status</th><th>Action</th></tr></thead><tbody>${list.map(t=>`<tr><td>${esc(teamName(t.proposerTeamId))}</td><td>${esc(teamName(t.recipientTeamId))}</td><td>${t.offeredPokemonId}</td><td>${t.requestedPokemonId}</td><td>${t.status}</td><td>${t.status==='PENDING'&&(state.user.role==='MANAGER'||t.recipientTeamId===state.user.teamId)?`<button class="primary accept-trade" data-id="${t.id}">Accept</button> <button class="secondary reject-trade" data-id="${t.id}">Reject</button>`:'—'}</td></tr>`).join('')||'<tr><td colspan="6">No trades yet</td></tr>'}</tbody></table></div>`}
function bindTradeButtons(){$$('.accept-trade').forEach(b=>b.addEventListener('click',()=>respondTrade(b.dataset.id,true)));$$('.reject-trade').forEach(b=>b.addEventListener('click',()=>respondTrade(b.dataset.id,false)))}
async function respondTrade(id,accept){try{await api(`/api/trades/${id}/respond`,{method:'POST',body:{accept}});toast(accept?'Trade accepted':'Trade rejected');await renderTrades();}catch(e){toast(e.message,true)}}
async function renderTransactions(){const list=await api('/api/transactions');$('#fa-content').innerHTML=`<div class="card"><h2>Transaction History</h2><div class="table-wrap"><table><thead><tr><th>Time</th><th>Type</th><th>Description</th></tr></thead><tbody>${list.map(t=>`<tr><td>${new Date(t.createdAt).toLocaleString()}</td><td>${esc(t.type)}</td><td>${esc(t.description)}</td></tr>`).join('')||'<tr><td colspan="3">No transactions yet</td></tr>'}</tbody></table></div></div>`}

async function renderManager(){
  if(state.user.role!=='MANAGER'){location.hash='';return showPage('home')}
  await refreshBase();const mons=await api('/api/pokemon');
  $('#page-manager').innerHTML=`<h1>Commissioner</h1><div class="manager-grid">
  <div class="card"><h2>League Settings</h2><div class="toolbar"><label>Name <input id="setting-name" value="${esc(state.settings.leagueName)}"></label><label>Week <input id="setting-week" type="number" min="1" max="10" value="${state.settings.currentWeek}"></label><label>Lineup deadline <input id="setting-deadline" type="datetime-local" value="${state.settings.lineupDeadline?String(state.settings.lineupDeadline).slice(0,16):''}"></label><label><input id="setting-draft" type="checkbox" ${state.settings.draftOpen?'checked':''}> Draft open</label><label><input id="setting-snake" type="checkbox" ${state.settings.snakeDraft?'checked':''}> Snake draft</label><button id="save-settings" class="primary">Save Settings</button></div></div>
  <div class="card"><div class="section-title"><div><h2>Teams / Divisions / Draft Order</h2><p class="muted">Manual rank 0 = automatic standings. Use 1–8 only when you want to force placement.</p></div></div><div class="table-wrap"><table><thead><tr><th>Team</th><th>Coach</th><th>Division</th><th>Draft Pos</th><th>Manual Rank</th><th>FAAB</th><th></th></tr></thead><tbody>${state.teams.map(t=>`<tr><td><input id="team-name-${t.id}" value="${esc(t.name)}"></td><td><input id="coach-name-${t.id}" value="${esc(t.coachName)}"></td><td><select id="division-${t.id}"><option ${t.division==='A'?'selected':''}>A</option><option ${t.division==='B'?'selected':''}>B</option></select></td><td><input id="draft-pos-${t.id}" class="inline-input" type="number" min="1" max="16" value="${t.draftPosition}"></td><td><input id="rank-${t.id}" class="inline-input" type="number" min="0" max="8" value="${t.manualRank}"></td><td><input id="budget-${t.id}" class="inline-input" type="number" min="0" value="${t.faBudget}"></td><td><button class="secondary save-team" data-id="${t.id}">Save</button></td></tr>`).join('')}</tbody></table></div></div>
  <div class="card"><h2>Schedule Generator</h2><p>Exactly 7 same-division opponents + 3 balanced cross-division opponents. Changing the random seed changes the cross-division matchups.</p><div class="toolbar"><input id="schedule-seed" type="number" value="${state.settings.scheduleSeed}"><button id="regenerate-schedule" class="danger">Regenerate Entire Schedule</button></div></div>
  <div class="card"><div class="section-title"><div><h2>Pokemon Draft Prices</h2><p class="muted">Prototype values are $0–$20 and can all be changed.</p></div><input id="price-search" placeholder="Search Pokemon"></div><div class="table-wrap price-table"><table><thead><tr><th>Pokemon</th><th>Price</th><th>Drafted</th><th></th></tr></thead><tbody id="price-body">${mons.map(p=>priceRow(p)).join('')}</tbody></table></div></div>
  </div>`;
  $('#save-settings').addEventListener('click',async()=>{try{state.settings=await api('/api/manager/settings',{method:'PUT',body:{leagueName:$('#setting-name').value,currentWeek:Number($('#setting-week').value),draftOpen:$('#setting-draft').checked,snakeDraft:$('#setting-snake').checked,lineupDeadline:$('#setting-deadline').value}});toast('Settings saved');}catch(e){toast(e.message,true)}});
  $$('.save-team').forEach(b=>b.addEventListener('click',async()=>{const id=b.dataset.id;try{await api(`/api/manager/teams/${id}`,{method:'PUT',body:{name:$(`#team-name-${id}`).value,coachName:$(`#coach-name-${id}`).value,division:$(`#division-${id}`).value,draftPosition:Number($(`#draft-pos-${id}`).value),manualRank:Number($(`#rank-${id}`).value),faBudget:Number($(`#budget-${id}`).value)}});toast('Team saved');await refreshBase();}catch(e){toast(e.message,true)}}));
  $('#regenerate-schedule').addEventListener('click',async()=>{if(!confirm('This deletes and rebuilds all scheduled matchups/results. Continue?'))return;try{await api('/api/manager/schedule/regenerate',{method:'POST',body:{seed:Number($('#schedule-seed').value)}});toast('Schedule regenerated');}catch(e){toast(e.message,true)}});
  $('#price-search').addEventListener('input',e=>{const q=e.target.value.toLowerCase();$$('#price-body tr').forEach(r=>r.classList.toggle('hidden',!r.dataset.name.includes(q)))});bindPriceButtons();
}
function priceRow(p){return `<tr data-name="${esc(p.name.toLowerCase())}"><td>${esc(p.name)}</td><td><input id="price-${p.id}" type="number" min="0" max="20" value="${p.price}"></td><td>${p.drafted?'Yes':'No'}</td><td><button class="secondary save-price" data-id="${p.id}">Save</button></td></tr>`}
function bindPriceButtons(){$$('.save-price').forEach(b=>b.addEventListener('click',async()=>{try{await api(`/api/manager/pokemon/${b.dataset.id}/price`,{method:'PUT',body:{price:Number($(`#price-${b.dataset.id}`).value)}});toast('Price saved');}catch(e){toast(e.message,true)}}))}

boot();
