(() => {
  const searchInput = document.getElementById("room-search");
  const stateFilter = document.getElementById("room-state-filter");
  const dateFilter = document.getElementById("room-date-filter");
  const base = document.body?.dataset?.base || "/";

  function daysAgo(date, days) {
    const now = new Date();
    const limit = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    limit.setDate(limit.getDate() - days);
    return date >= limit;
  }

  function stateLabel(state) {
    if (state === "WAITING") return "ESPERANDO";
    if (state === "RUNNING") return "EN JUEGO";
    if (state === "FINISHED") return "FINALIZADA";
    return state || "";
  }

  function applyFilters() {
    const query = (searchInput?.value || "").trim().toLowerCase();
    const state = stateFilter?.value || "";
    const dateMode = dateFilter?.value || "";

    document.querySelectorAll("[data-room-id]").forEach((el) => {
      const name = (el.dataset.name || "").toLowerCase();
      const roomState = el.dataset.state || "";
      const createdRaw = el.dataset.created || "";
      const createdText = createdRaw.toLowerCase();
      const created = createdRaw ? new Date(createdRaw) : null;

      let ok = true;
      if (query && !name.includes(query) && !createdText.includes(query)) ok = false;
      if (state && roomState !== state) ok = false;
      if (ok && dateMode) {
        if (!created || Number.isNaN(created.getTime())) {
          ok = false;
        } else if (dateMode === "today") {
          const now = new Date();
          ok =
            created.getFullYear() === now.getFullYear() &&
            created.getMonth() === now.getMonth() &&
            created.getDate() === now.getDate();
        } else {
          ok = daysAgo(created, parseInt(dateMode, 10));
        }
      }

      el.style.display = ok ? "" : "none";
    });
  }

  [searchInput, stateFilter, dateFilter].forEach((el) => {
    if (!el) return;
    el.addEventListener("input", applyFilters);
    el.addEventListener("change", applyFilters);
  });

  async function refreshStates() {
    const rows = Array.from(document.querySelectorAll("[data-room-id]"));
    await Promise.all(
      rows.map(async (row) => {
        const id = parseInt(row.dataset.roomId, 10);
        if (!id) return;
        try {
          const res = await fetch(`${base}rooms/${id}/status`, { credentials: "same-origin" });
          if (!res.ok) return;
          const data = await res.json();
          if (!data?.state) return;
          row.dataset.state = data.state;
          const label = row.querySelector("[data-state-label]");
          if (label) label.textContent = stateLabel(data.state);
        } catch (e) {
          console.error("Error actualizando estado de sala", e);
        }
      })
    );
  }

  setInterval(async () => {
    try {
      const res = await fetch(`${base}rooms/api/my`, { credentials: "same-origin" });
      const ids = await res.json();

      document.querySelectorAll("[data-room-id]").forEach((row) => {
        const id = parseInt(row.dataset.roomId, 10);
        if (!ids.includes(id)) {
          row.remove();
        }
      });

      await refreshStates();

      const cards = document.getElementById("rooms-cards");
      const tableWrap = document.getElementById("rooms-table-wrap");
      const emptyMsg = document.getElementById("empty-msg");

      const remaining = document.querySelectorAll("[data-room-id]").length;

      if (remaining === 0) {
        if (tableWrap) tableWrap.remove();
        if (cards) cards.remove();
        if (emptyMsg) emptyMsg.classList.remove("hidden");
      }

      applyFilters();
    } catch (e) {
      console.error("Error sincronizando salas", e);
    }
  }, 5000);
})();
