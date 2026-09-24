/* Keep the draft board and on-the-clock team in sync across browsers. */
(() => {
  let lastSignature = null;
  let refreshing = false;
  const originalRenderDraft = window.renderDraft;

  function signature(d) {
    return JSON.stringify({
      picks: d.picks.map(p => [p.overallPick, p.teamId, p.pokemonId]),
      currentTeam: d.currentTeam?.id ?? null,
      draftOpen: d.settings.draftOpen
    });
  }

  window.renderDraft = async function () {
    await originalRenderDraft();
    const d = await api("/api/draft");
    lastSignature = signature(d);
    const board = document.querySelector("#page-draft .card:last-child");
    if (board) {
      board.classList.add("live-draft-board");
      const heading = board.querySelector("h2");
      if (heading) heading.textContent = "Live Draft Board";
      const table = board.querySelector("table");
      if (table) table.setAttribute("aria-label", "Live draft picks");
    }
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
