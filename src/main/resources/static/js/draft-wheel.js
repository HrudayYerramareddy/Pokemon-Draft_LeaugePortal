/* Shared draft-order elimination wheel. The server chooses and persists each result. */
(() => {
  let active = false, snapshot = null, rotation = 0, lastHistory = -1;
  const palette = ["#2563eb","#7c3aed","#0d9488","#e87924","#db2777","#0891b2","#4f46e5","#65a30d"];
  const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
  const escapeName = value => esc(String(value));

  function paintWheel(remaining, selectedId = null) {
    const wheel = document.querySelector("#draft-order-wheel");
    if (!wheel) return;
    if (!remaining.length) { wheel.style.background = "#174b69"; return; }
    const step = 360 / remaining.length;
    wheel.style.background = "conic-gradient(" + remaining.map((t,i) =>
      `${palette[i % palette.length]} ${i*step}deg ${(i+1)*step}deg`).join(",") + ")";
    const labels = document.querySelector("#draft-wheel-labels");
    labels.innerHTML = remaining.map((t,i) => {
      const angle = (i + .5) * step;
      const rad = (angle - 90) * Math.PI / 180;
      const x = 50 + 33 * Math.cos(rad), y = 50 + 33 * Math.sin(rad);
      return `<span class="wheel-team-label" style="left:${x}%;top:${y}%" title="${escapeName(t.name)}">${escapeName(t.name)}</span>`;
    }).join("");
  }

  function draw(data) {
    snapshot = data;
    const host = document.querySelector("#draft-wheel-panel");
    if (!host) return;
    const manager = state.user.role === "MANAGER";
    const count = data.history.length;
    host.innerHTML = `<div class="section-title"><div><h2>Live Draft Order Wheel</h2><p class="muted">Elimination: first drawn gets pick 16; last remaining gets pick 1. Only the commissioner can spin.</p></div><span class="pool-count">${count}/16 assigned</span></div>
      <div class="draft-wheel-layout"><div class="draft-wheel-stage"><div class="draft-wheel-pointer">▼</div><div id="draft-order-wheel"><div id="draft-wheel-labels"></div></div></div>
      <div class="draft-wheel-results"><h3>Official Draft Order</h3><div id="draft-wheel-status">${data.complete ? "Drawing complete" : "Waiting for the next spin"}</div><ol reversed start="16">${data.history.map(h=>`<li>Pick ${h.position}: ${escapeName(h.name)}</li>`).join("")}</ol>
      ${manager ? `<button id="draft-wheel-spin" class="primary" ${data.complete || active ? "disabled" : ""}>Spin for Pick ${16-count}</button> <button id="draft-wheel-reset" class="secondary" ${active ? "disabled" : ""}>Reset Wheel</button>` : '<p class="muted">The commissioner controls the drawing.</p>'}
      </div></div>`;
    paintWheel(data.remaining);
    const wheel = document.querySelector("#draft-order-wheel");
    wheel.style.transform = `rotate(${rotation}deg)`;
    document.querySelector("#draft-wheel-spin")?.addEventListener("click", spin);
    document.querySelector("#draft-wheel-reset")?.addEventListener("click", reset);
    lastHistory = count;
  }

  async function spin() {
    if (active || !snapshot || snapshot.complete) return;
    active = true;
    const before = snapshot;
    const button = document.querySelector("#draft-wheel-spin");
    if (button) button.disabled = true;
    try {
      const result = await api("/api/draft-wheel/spin", {method:"POST"});
      const winner = result.history[before.history.length];
      const index = before.remaining.findIndex(t => t.teamId === winner.teamId);
      const step = 360 / before.remaining.length;
      const center = (index + .5) * step;
      const normalized = ((rotation % 360) + 360) % 360;
      const delta = ((360 - center - normalized) % 360 + 360) % 360;
      rotation += 360 * 6 + delta;
      const wheel = document.querySelector("#draft-order-wheel");
      wheel.style.transition = "transform 4.5s cubic-bezier(.12,.7,.08,1)";
      wheel.style.transform = `rotate(${rotation}deg)`;
      document.querySelector("#draft-wheel-status").textContent = `Spinning for pick ${winner.position}…`;
      await pause(4700);
      draw(result);
      document.querySelector("#draft-wheel-status").textContent = `${winner.name} receives pick ${winner.position}!`;
      await refreshBase();
    } catch(e) { toast(e.message,true); }
    finally { active = false; const b=document.querySelector("#draft-wheel-spin"); if(b && snapshot && !snapshot.complete)b.disabled=false; }
  }

  async function reset() {
    if (active || !confirm("Reset the entire draft order drawing? This is only allowed before any picks.")) return;
    try { draw(await api("/api/draft-wheel/reset",{method:"POST"})); toast("Draft wheel reset"); }
    catch(e) {toast(e.message,true);}
  }

  const original = window.renderDraft;
  window.renderDraft = async function () {
    await original();
    const page = document.querySelector("#page-draft");
    if (!page) return;
    const panel = document.createElement("section");
    panel.id = "draft-wheel-panel";
    panel.className = "card";
    page.insertBefore(panel,page.firstChild);
    try { draw(await api("/api/draft-wheel")); }
    catch(e) { panel.textContent = "Draft wheel unavailable: " + e.message; }
  };

  setInterval(async () => {
    if (active || state.page !== "draft" || !state.user || !document.querySelector("#draft-wheel-panel")) return;
    try {
      const data = await api("/api/draft-wheel");
      if (data.history.length !== lastHistory) draw(data);
    } catch(e) { console.warn("Wheel refresh failed",e); }
  },3000);
})();
