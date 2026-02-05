(() => {
  const root = document.body;
  const countRaw = root?.dataset?.selectedCount;
  const limit = countRaw ? parseInt(countRaw, 10) : 0;
  const form = document.getElementById("question-select-form");
  const inputs = Array.from(
    document.querySelectorAll('input[type="checkbox"][name="questionIds"]')
  );

  if (!form || inputs.length === 0 || Number.isNaN(limit)) return;

  function checkedCount() {
    return inputs.filter((i) => i.checked).length;
  }

  function enforceLimit(changed) {
    const count = checkedCount();
    if (limit > 0 && count > limit && changed) {
      changed.checked = false;
      alert(`Solo puedes seleccionar ${limit} preguntas.`);
    }
  }

  inputs.forEach((input) => {
    input.addEventListener("change", () => enforceLimit(input));
  });

  form.addEventListener("submit", (e) => {
    const count = checkedCount();
    if (limit <= 0 || count !== limit) {
      e.preventDefault();
      if (limit <= 0) {
        alert("No puedes guardar una selección de 0 preguntas.");
      } else if (count < limit) {
        alert(`Debes seleccionar exactamente ${limit} preguntas.`);
      } else {
        alert(`Solo puedes seleccionar ${limit} preguntas.`);
      }
    }
  });
})();
