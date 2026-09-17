/* Commissioner-only testing helpers. */
(() => {
  function addTestingTools() {
    if (!state?.user || state.user.role !== "MANAGER") return;
    const page = document.querySelector("#page-manager");
    if (!page || document.querySelector("#testing-roster-tools")) return;

    const tools = document.createElement("div");
    tools.id = "testing-roster-tools";
    tools.className = "card glass-panel";
    tools.innerHTML = `
      <div class="section-title">
        <div>
          <div class="eyebrow">Testing Tools</div>
          <h2>Test Rosters</h2>
          <p class="muted">Skip the 160-pick draft while testing trades, FAAB, weekly lineups, and schedule reveals.</p>
        </div>
      </div>
      <div class="toolbar">
        <button id="fill-test-rosters" class="primary">Fill Test Rosters</button>
        <button id="clear-test-rosters" class="danger">Clear All Rosters</button>
      </div>
      <p class="small muted">Fill gives every team 10 unique Pokemon and keeps each roster at or below the $100 cap. Clear resets roster assignments, draft picks, and weekly lineup submissions.</p>`;
    page.prepend(tools);

    document
      .querySelector("#fill-test-rosters")
      .addEventListener("click", async () => {
        if (
          !confirm(
            "Fill all 16 teams with 10 test Pokemon each? This only works when rosters/draft picks are empty.",
          )
        )
          return;
        const button = document.querySelector("#fill-test-rosters");
        button.disabled = true;
        try {
          const result = await api("/api/manager/testing/fill-rosters", {
            method: "POST",
          });
          toast(
            `Filled ${result.teams} teams with ${result.pokemonAssigned} Pokemon`,
          );
          await refreshBase();
        } catch (e) {
          toast(e.message, true);
        } finally {
          button.disabled = false;
        }
      });

    document
      .querySelector("#clear-test-rosters")
      .addEventListener("click", async () => {
        if (
          !confirm(
            "Clear ALL rosters, draft picks, and weekly lineup submissions? Use this only for testing/resetting.",
          )
        )
          return;
        const button = document.querySelector("#clear-test-rosters");
        button.disabled = true;
        try {
          const result = await api("/api/manager/testing/clear-rosters", {
            method: "POST",
          });
          toast(`Cleared ${result.pokemonRemoved} roster assignments`);
          await refreshBase();
        } catch (e) {
          toast(e.message, true);
        } finally {
          button.disabled = false;
        }
      });
  }

  // Do not replace renderManager. Other UI files redefine it, which made the
  // previous wrapper unreliable. Inject after the Commissioner page renders.
  const managerNav = document.querySelector("#manager-nav");
  if (managerNav)
    managerNav.addEventListener("click", () => setTimeout(addTestingTools, 0));

  const managerPage = document.querySelector("#page-manager");
  if (managerPage) {
    new MutationObserver(() => {
      if (!managerPage.classList.contains("hidden")) addTestingTools();
    }).observe(managerPage, { childList: true });
  }
})();
