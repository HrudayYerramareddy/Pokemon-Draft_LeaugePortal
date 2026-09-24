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
      ${data.complete ? `<button id="draft-wheel-replay" class="secondary" ${active ? "disabled" : ""}>Watch Replay</button> <button id="draft-wheel-video" class="secondary" ${active ? "disabled" : ""}>Download Video</button>` : ""} ${manager ? `<button id="draft-wheel-spin" class="primary" ${data.complete || active ? "disabled" : ""}>Spin for Pick ${16-count}</button> <button id="draft-wheel-reset" class="secondary" ${active ? "disabled" : ""}>Reset Wheel</button>` : '<p class="muted">The commissioner controls the drawing.</p>'}
      </div></div>`;
    paintWheel(data.remaining);
    const wheel = document.querySelector("#draft-order-wheel");
    wheel.style.transform = `rotate(${rotation}deg)`;
    document.querySelector("#draft-wheel-spin")?.addEventListener("click", spin);
    document.querySelector("#draft-wheel-reset")?.addEventListener("click", reset);
    document.querySelector("#draft-wheel-replay")?.addEventListener("click", () => replay(false));
    document.querySelector("#draft-wheel-video")?.addEventListener("click", () => replay(true));
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


  // Rebuild the drawing from the server's saved elimination order. No video hosting required.
  // The same canvas is used for on-screen playback and MediaRecorder capture.
  async function replay(download) {
    if (active || !snapshot?.complete || snapshot.history.length !== 16) return;
    if (download && (!window.MediaRecorder || !HTMLCanvasElement.prototype.captureStream)) {
      return toast("Video recording is not supported in this browser. Try Chrome or Edge.", true);
    }
    active = true;
    const official = snapshot;
    const overlay = document.createElement("div");
    overlay.className = "pokemon-detail-overlay";
    overlay.style.zIndex = "3000";
    overlay.innerHTML = '<div class="card" style="width:min(760px,96vw);max-height:95vh;overflow:auto;text-align:center"><h2>Draft Order Replay</h2><p id="wheel-replay-caption" aria-live="polite">Preparing the drawing…</p><canvas id="wheel-replay-canvas" width="900" height="720" style="width:min(100%,650px);height:auto;display:block;margin:auto"></canvas><p id="wheel-replay-progress" class="muted">Please keep this tab open until the replay finishes.</p></div>';
    document.body.appendChild(overlay);
    const canvas = overlay.querySelector("canvas"), ctx = canvas.getContext("2d");
    const caption = overlay.querySelector("#wheel-replay-caption");
    const progress = overlay.querySelector("#wheel-replay-progress");
    const colors = palette;
    const all = [...official.history];
    let angle = 0, recorder, chunks = [], stream;
    function frame(remaining, heading, spinAngle) {
      const W = canvas.width, H = canvas.height, cx = W / 2, cy = 325, radius = 258;
      ctx.fillStyle = "#071b31"; ctx.fillRect(0,0,W,H);
      ctx.fillStyle = "#e7f4ff"; ctx.textAlign = "center"; ctx.font = "bold 33px sans-serif";
      ctx.fillText("DRAFT ORDER ELIMINATION",cx,45);
      ctx.font = "bold 24px sans-serif"; ctx.fillText(heading,cx,84);
      const step = Math.PI * 2 / remaining.length;
      remaining.forEach((team,i) => {
        const start = spinAngle + i*step - Math.PI/2;
        ctx.beginPath();ctx.moveTo(cx,cy);ctx.arc(cx,cy,radius,start,start+step);ctx.closePath();
        ctx.fillStyle=colors[i%colors.length];ctx.fill();ctx.strokeStyle="#d8eaff";ctx.lineWidth=2;ctx.stroke();
        const mid=start+step/2;
        const x=cx+radius*.67*Math.cos(mid),y=cy+radius*.67*Math.sin(mid);
        ctx.save();ctx.translate(x,y);ctx.rotate(mid+Math.PI/2);
        ctx.fillStyle="white";ctx.shadowColor="#000";ctx.shadowBlur=5;
        ctx.font="bold "+(remaining.length>10?12:remaining.length>5?15:20)+"px sans-serif";
        const name=team.name;
        // Keep long names inside the wedge.
        ctx.fillText(name.length>21?name.slice(0,20)+"…":name,0,0,remaining.length>10?94:140);
        ctx.restore();
      });
      ctx.fillStyle="#fff";ctx.beginPath();ctx.moveTo(cx,cy-radius-19);ctx.lineTo(cx-16,cy-radius-49);ctx.lineTo(cx+16,cy-radius-49);ctx.closePath();ctx.fill();
      ctx.font="bold 23px sans-serif";ctx.fillText("First drawn: Pick 16  •  Last remaining: Pick 1",cx,658);
      ctx.font="17px sans-serif";ctx.fillText("Official saved draft order",cx,690);
    }
    try {
      if (download) {
        stream = canvas.captureStream(30);
        const mime = ["video/webm;codecs=vp9","video/webm;codecs=vp8","video/webm"].find(type => MediaRecorder.isTypeSupported(type));
        if (!mime) throw new Error("This browser cannot record a WebM video.");
        recorder = new MediaRecorder(stream,{mimeType:mime,videoBitsPerSecond:3500000});
        recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data); };
        recorder.start(1000);
      }
      let remaining = [...all].map(h=>({teamId:h.teamId,name:h.name}));
      frame(remaining,"Starting with all 16 teams",angle);
      await pause(700);
      for (let i=0;i<15;i++) {
        const winner=all[i], index=remaining.findIndex(t=>t.teamId===winner.teamId);
        if(index<0) throw new Error("Saved draft results are inconsistent.");
        const step=2*Math.PI/remaining.length;
        const target=-(index+.5)*step;
        const normalized=((angle%(2*Math.PI))+2*Math.PI)%(2*Math.PI);
        const delta=((target-normalized)%(2*Math.PI)+2*Math.PI)%(2*Math.PI);
        const start=angle,end=start+Math.PI*2*3+delta;
        caption.textContent="Spinning for pick "+winner.position+"…";
        progress.textContent=(i+1)+" of 15 spins";
        const duration=1550, started=performance.now();
        await new Promise(resolve => {
          function tick(now) {
            const t=Math.min(1,(now-started)/duration), eased=1-Math.pow(1-t,3);
            frame(remaining,"Spinning for Pick "+winner.position,start+(end-start)*eased);
            if(t<1)requestAnimationFrame(tick);else resolve();
          }
          requestAnimationFrame(tick);
        });
        angle=end;
        caption.textContent=winner.name+" receives pick "+winner.position+"!";
        frame(remaining,"Pick "+winner.position+": "+winner.name,angle);
        await pause(550);
        remaining=remaining.filter(t=>t.teamId!==winner.teamId);
      }
      const last=all[15];
      if(remaining.length!==1 || remaining[0].teamId!==last.teamId || last.position!==1)
        throw new Error("Saved final pick is inconsistent.");
      caption.textContent=last.name+" receives pick 1!";
      frame(remaining,"PICK 1: "+last.name,angle);
      progress.textContent="Drawing complete — official order: "+all.map(h=>h.position+". "+h.name).join(" | ");
      await pause(1500);
      if (recorder) {
        const stopped = new Promise(resolve => {recorder.onstop=resolve;});
        recorder.stop();await stopped;
        const blob = new Blob(chunks,{type:recorder.mimeType});
        if (!blob.size) throw new Error("Recording was empty.");
        const url=URL.createObjectURL(blob),link=document.createElement("a");
        link.href=url;link.download="draft-order-replay.webm";document.body.appendChild(link);link.click();link.remove();
        setTimeout(()=>URL.revokeObjectURL(url),60000);
        toast("Draft order replay video generated");
      } else toast("Draft order replay complete");
    } catch(e) {
      if(recorder?.state==="recording")recorder.stop();
      toast("Replay failed: "+e.message,true);
    } finally {
      stream?.getTracks().forEach(track=>track.stop());
      overlay.remove();active=false;
      if(document.querySelector("#draft-wheel-panel"))draw(official);
    }
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
