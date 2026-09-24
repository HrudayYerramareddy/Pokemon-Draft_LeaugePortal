/* A live, snake-order draft board at the top of the Draft Room. */
(() => {
  let lastSignature = null;
  let refreshing = false;
  const originalRenderDraft = window.renderDraft;

  function signature(d) {
    return JSON.stringify({
      picks: d.picks.map((p) => [p.overallPick, p.teamId, p.pokemonId]),
      currentTeam: d.currentTeam?.id ?? null,
      draftOpen: d.settings.draftOpen,
    });
  }

  function buildBoard(d) {
    const teams = [...state.teams].sort(
      (a, b) => a.draftPosition - b.draftPosition,
    );
    const count = teams.length;
    const rounds = 10;
    const picks = new Map(d.picks.map((p) => [Number(p.overallPick), p]));
    const currentPick = d.picks.length + 1;

    const headers = teams
      .map(
        (team) => `<th scope="col" title="${esc(team.name)}">
          <span class="draft-team-position">#${team.draftPosition}</span>
          <span class="draft-team-name">${esc(team.name)}</span>
        </th>`,
      )
      .join("");

    const rows = Array.from({ length: rounds }, (_, index) => {
      const round = index + 1;
      const reversed = d.settings.snakeDraft && round % 2 === 0;
      const cells = teams
        .map((team, column) => {
          const position = reversed ? count - column : column + 1;
          const overall = index * count + position;
          const pick = picks.get(overall);
          const onClock =
            !pick &&
            overall === currentPick &&
            d.settings.draftOpen &&
            d.currentTeam;
          const className = pick
            ? "draft-cell-picked"
            : onClock
              ? "draft-cell-current"
              : "draft-cell-empty";
          const name = pick
            ? esc(pick.pokemonName || "Unknown Pokémon")
            : onClock
              ? "ON THE CLOCK"
              : "—";

          return `<td class="${className}" title="Round ${round}, pick ${overall}: ${esc(team.name)}">
            <span class="draft-pick-number">Pick ${overall}</span>
            <span class="draft-pokemon-name">${name}</span>
          </td>`;
        })
        .join("");

      return `<tr>
        <th scope="row" class="draft-round-heading">R${round}<span>${reversed ? "←" : "→"}</span></th>
        ${cells}
      </tr>`;
    }).join("");

    return `<div class="card live-draft-board">
      <div class="section-title">
        <div>
          <h2>Live Draft Board</h2>
          <p class="muted">Teams are ordered by draft position. Arrows show the direction of each round.</p>
        </div>
        <span class="pool-count">${d.picks.length}/${count * rounds} picks</span>
      </div>
      <div class="draft-board-scroll" role="region" aria-label="Live snake draft board" tabindex="0">
        <table class="draft-snake-table">
          <thead><tr><th scope="col">Round</th>${headers}</tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    </div>`;
  }

  window.renderDraft = async function () {
    await originalRenderDraft();
    const d = await api("/api/draft");
    lastSignature = signature(d);

    const page = document.querySelector("#page-draft");
    if (!page) return;

    // Remove the older chronological table and put the snake board above the picker.
    const oldBoard = [...page.querySelectorAll(".card")].find(
      (card) => card.querySelector("h2")?.textContent.trim() === "Draft Board",
    );
    oldBoard?.remove();

    const title = page.querySelector(".section-title");
    if (title) title.insertAdjacentHTML("afterend", buildBoard(d));
    else page.insertAdjacentHTML("afterbegin", buildBoard(d));
  };

  async function checkDraft() {
    if (refreshing || state.page !== "draft" || !state.user) return;
    refreshing = true;
    try {
      const d = await api("/api/draft");
      const next = signature(d);
      if (lastSignature !== null && next !== lastSignature) {
        if (!document.querySelector(".pokemon-detail-overlay")) {
          await window.renderDraft();
        }
      } else if (lastSignature === null) {
        lastSignature = next;
      }
    } catch (e) {
      console.warn("Draft refresh failed:", e);
    } finally {
      refreshing = false;
    }
  }

  setInterval(checkDraft, 3000);
})();
