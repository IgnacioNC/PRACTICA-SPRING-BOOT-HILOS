(() => {
  const root = document.body;
  const pin = root.dataset.pin;
  if (!pin) return;

  const stateEl = document.getElementById("room-state");
  const waitingEl = document.getElementById("waiting-msg");
  const runningEl = document.getElementById("running-section");
  const resultEl = document.getElementById("result-section");
  const finishedEl = document.getElementById("finished-section");
  const timeEl = document.getElementById("seconds-left");
  const questionEl = document.getElementById("question-statement");
  const optA = document.getElementById("opt-a");
  const optB = document.getElementById("opt-b");
  const optC = document.getElementById("opt-c");
  const optD = document.getElementById("opt-d");
  const answeredEl = document.getElementById("answered-msg");
  const timeoutEl = document.getElementById("timeout-msg");
  const answerForm = document.getElementById("answer-form");
  const scoreEl = document.getElementById("player-score");
  const positionEl = document.getElementById("player-position");
  const resultBanner = document.getElementById("result-banner");
  const resultText = document.getElementById("result-text");
  const resultStatement = document.getElementById("result-statement");
  const resultSeconds = document.getElementById("result-seconds");
  const resultSecondsRow = document.getElementById("result-seconds-row");
  let questionEndsAt = null;
  let questionSecondsFallback = null;
  let clockOffset = 0;

  function nowMs() {
    return Date.now() + clockOffset;
  }

  function currentSecondsLeft() {
    if (questionEndsAt != null) {
      return Math.max(0, Math.ceil((questionEndsAt - nowMs()) / 1000));
    }
    if (questionSecondsFallback != null) {
      return questionSecondsFallback;
    }
    return null;
  }

  function setStateSections(state, phase) {
    if (waitingEl) waitingEl.style.display = state === "WAITING" ? "block" : "none";
    if (runningEl) runningEl.style.display = state === "RUNNING" && phase === "QUESTION" ? "block" : "none";
    if (resultEl) resultEl.style.display = state === "RUNNING" && phase === "RESULTS" ? "block" : "none";
    if (finishedEl) finishedEl.style.display = state === "FINISHED" ? "block" : "none";
  }

  function renderQuestion(q) {
    if (!q) return;
    if (questionEl) questionEl.textContent = q.statement || "";
    if (optA) optA.textContent = q.optionA || "";
    if (optB) optB.textContent = q.optionB || "";
    if (optC) optC.textContent = q.optionC || "";
    if (optD) optD.textContent = q.optionD || "";
  }

  function stateLabel(state) {
    if (state === "WAITING") return "ESPERANDO";
    if (state === "RUNNING") return "EN JUEGO";
    if (state === "FINISHED") return "FINALIZADA";
    return state || "";
  }

  async function poll() {
    try {
      const base = window.APP_BASE || "";
      const res = await fetch(`${base}/play/${pin}/status`);
      if (res.status === 401 || res.status === 403) {
        window.location.href = "/join";
        return;
      }
      if (!res.ok) return;
      const data = await res.json();

      if (stateEl) stateEl.textContent = stateLabel(data.state);
      setStateSections(data.state, data.phase);

      if (data.error) {
        window.location.href = "/join";
        return;
      }

      const manual = (data.advanceMode || "AUTO") === "MANUAL";
      if (!manual || data.phase === "RESULTS") {
        if (scoreEl && data.score != null) scoreEl.textContent = data.score;
        if (positionEl && data.position != null) positionEl.textContent = data.position;
      }

      if (data.serverNow != null) {
        clockOffset = data.serverNow - Date.now();
      }

      if (data.state === "RUNNING") {
        if (data.phase === "QUESTION") {
          if (data.questionEndsAt != null) {
            questionEndsAt = data.questionEndsAt;
            questionSecondsFallback = null;
          } else if (data.secondsLeft != null) {
            questionEndsAt = null;
            questionSecondsFallback = data.secondsLeft;
          }
          const secondsLeft = currentSecondsLeft();
          if (timeEl && secondsLeft != null) timeEl.textContent = secondsLeft;
          renderQuestion(data.question);
          const answered = !!data.alreadyAnswered;
          const timeUp = secondsLeft != null && secondsLeft <= 0;
          if (answeredEl) answeredEl.style.display = answered ? "block" : "none";
          if (timeoutEl) timeoutEl.style.display = !answered && timeUp ? "block" : "none";
          if (answerForm) answerForm.style.display = answered || timeUp ? "none" : "block";
        }
        if (data.phase === "RESULTS") {
          questionEndsAt = null;
          questionSecondsFallback = null;
          if (resultStatement && data.statement != null) resultStatement.textContent = data.statement;
          if (resultSecondsRow) resultSecondsRow.classList.add("hidden");
          const correct = !!data.correct;
          if (resultBanner) {
            resultBanner.classList.remove("bg-green-600", "bg-red-600");
            resultBanner.classList.add(correct ? "bg-green-600" : "bg-red-600");
          }
          if (resultText) {
            resultText.textContent = correct ? "¡Correcto!" : "Incorrecto o sin respuesta";
          }
        }
      }

      if (data.state === "FINISHED" && finishedEl) {
        // nothing extra; results page is separate
      }
    } catch (e) {
      console.error("Error actualizando juego", e);
    }
  }

  poll();
  setInterval(poll, 500);
  setInterval(() => {
    if (!runningEl || runningEl.style.display === "none") return;
    const secondsLeft = currentSecondsLeft();
    if (timeEl && secondsLeft != null) timeEl.textContent = secondsLeft;
    const answered = answeredEl && answeredEl.style.display === "block";
    const timeUp = secondsLeft != null && secondsLeft <= 0;
    if (timeoutEl) timeoutEl.style.display = !answered && timeUp ? "block" : "none";
    if (answerForm) answerForm.style.display = answered || timeUp ? "none" : "block";
  }, 250);
})();
