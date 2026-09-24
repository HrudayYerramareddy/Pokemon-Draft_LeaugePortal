/* Complete FAAB add/drop UI. Loaded after the base UI. */
(() => {
  const pokemonNames = new Map();

  async function loadPokemonNames() {
    const all = await api("/api/pokemon");
    pokemonNames.clear();
    all.forEach((p) => pokemonNames.set(Number(p.id), p.name));
  }

  window.loadFaabPokemonNames = loadPokemonNames;

  function pokemonNameForDisplay(id) {
    if (id == null) return "—";
    return pokemonNames.get(Number(id)) || "Unknown Pokémon";
  }

  window.bidTable = function (list) {
    const manager = state.user.role === "MANAGER";
    return `<div class="card"><h2>${manager ? "All FAAB Bids" : "My FAAB Bids"}</h2>
      <div class="table-wrap"><table><thead><tr><th>Team</th><th>Free Agent</th><th>Drop If Won</th><th>Bid</th><th>Status</th></tr></thead>
      <tbody>${list.map((b) => `<tr><td>${esc(teamName(b.teamId))}</td><td>${esc(pokemonNameForDisplay(b.wantedPokemonId))}</td><td>${esc(pokemonNameForDisplay(b.dropPokemonId))}</td><td>$${b.amount}</td><td>${esc(b.status)}</td></tr>`).join("") || '<tr><td colspan="5">No bids yet</td></tr>'}</tbody></table></div></div>`;
  };

  window.renderBids = async function () {
    await refreshBase();
    await loadPokemonNames();
    const list = await api("/api/free-agency/bids");
    const free = await api("/api/pokemon/free-agents");

    if (state.user.role === "MANAGER") {
      const pending = list.filter((b) => b.status === "PENDING").length;
      $("#fa-content").innerHTML =
        `<div class="card"><div class="section-title"><div><h2>Blind FAAB Processing</h2><p class="muted">${pending} pending bid${pending === 1 ? "" : "s"}. Processing awards each free agent to the highest valid bid; earlier bid wins a tie.</p></div><button id="process-bids" class="primary" ${pending ? "" : "disabled"}>Process Pending Bids</button></div></div>${bidTable(list)}`;
      const button = $("#process-bids");
      if (button)
        button.addEventListener("click", async () => {
          if (
            !confirm(
              `Process ${pending} pending FAAB bid${pending === 1 ? "" : "s"}? Winners will be added, selected drops will become free agents, and FAAB will be deducted.`,
            )
          )
            return;
          button.disabled = true;
          try {
            const r = await api("/api/manager/free-agency/process", {
              method: "POST",
            });
            toast(
              `FAAB processed: ${r.winners} winning bid${r.winners === 1 ? "" : "s"}`,
            );
            await renderBids();
          } catch (e) {
            toast(e.message, true);
            button.disabled = false;
          }
        });
      return;
    }

    const myRoster = await api(`/api/rosters/${state.user.teamId}`);
    const me = state.teams.find((t) => t.id === state.user.teamId);
    const complete = myRoster.length === 10;
    const budget = me?.faBudget ?? 0;
    $("#fa-content").innerHTML =
      `<div class="card"><h2>Submit Blind FAAB Bid</h2>
      <p>FAAB remaining: <strong>$${budget}</strong> · Roster: <strong>${myRoster.length}/10</strong></p>
      <p class="muted">Every team must keep exactly 10 Pokémon. Choose the free agent you want, your bid amount, and the Pokémon that will be dropped only if you win.</p>
      <div class="toolbar">
        <label>Free agent<select id="bid-wanted"><option value="">Choose free agent</option>${free.map((p) => `<option value="${p.id}">${esc(p.name)} — $${p.price}</option>`).join("")}</select></label>
        <label>Drop if bid wins<select id="bid-drop"><option value="">Choose Pokemon to drop</option>${myRoster.map((p) => `<option value="${p.pokemonId}">${esc(p.name)} — $${p.price}</option>`).join("")}</select></label>
        <label>FAAB bid<input id="bid-amount" type="number" min="1" max="${budget}" value="${budget > 0 ? 1 : 0}" placeholder="1"></label>
        <button id="submit-bid" class="primary" ${budget <= 0 || free.length === 0 || !complete ? "disabled" : ""}>Submit Blind Bid</button>
      </div>
      ${!complete ? '<p class="small muted">Free agency is unavailable until this team has exactly 10 Pokémon.</p>' : ""}<p id="bid-preview" class="small muted"></p></div>${bidTable(list)}`;

    const preview = () => {
      const wanted = free.find((p) => p.id === Number($("#bid-wanted").value));
      const drop = myRoster.find(
        (p) => p.pokemonId === Number($("#bid-drop").value),
      );
      if (!wanted) {
        $("#bid-preview").textContent =
          "Choose a free agent to preview the move.";
        return;
      }
      const current = myRoster.reduce((sum, p) => sum + (p.price || 0), 0);
      const after = current - (drop?.price || 0) + (wanted.price || 0);
      $("#bid-preview").textContent =
        `Roster value if won: $${after}/$100${drop ? ` · ${drop.name} is dropped only if this bid wins.` : " · choose a Pokémon to drop."}`;
    };
    $("#bid-wanted").addEventListener("change", preview);
    $("#bid-drop").addEventListener("change", preview);
    preview();
    $("#submit-bid").addEventListener("click", async () => {
      const wanted = Number($("#bid-wanted").value),
        dropValue = $("#bid-drop").value,
        drop = dropValue ? Number(dropValue) : null,
        amount = Number($("#bid-amount").value);
      if (!complete)
        return toast(
          "Your roster must have exactly 10 Pokemon before using free agency",
          true,
        );
      if (!wanted) return toast("Choose a free agent", true);
      if (!drop) return toast("Choose who to drop if you win", true);
      if (!Number.isInteger(amount) || amount < 1 || amount > budget)
        return toast(`Bid must be between $1 and $${budget}`, true);
      try {
        await api("/api/free-agency/bids", {
          method: "POST",
          body: { wantedPokemonId: wanted, dropPokemonId: drop, amount },
        });
        toast("Blind bid submitted");
        await renderBids();
      } catch (e) {
        toast(e.message, true);
      }
    });
  };
})();
