(() => {
  const root = document.body;
  const roomId = root.dataset.roomId;
  if (!roomId) return;

  const stateEl = document.getElementById("room-state");
  const countdownEl = document.getElementById("countdown");
  const playersList = document.getElementById("players-list");
  const playersEmpty = document.getElementById("players-empty");
  const startSection = document.getElementById("start-section");
  const runningSection = document.getElementById("running-section");
  const finishedSection = document.getElementById("finished-section");
  const startBtn = document.getElementById("btn-start");
  const currentSection = document.getElementById("current-question-section");
  const currentQuestionEl = document.getElementById("current-question");
  const currentAnswerEl = document.getElementById("current-answer");
  const questionTimerEl = document.getElementById("question-timer");
  const btnShowResults = document.getElementById("btn-show-results");
  const btnForceResults = document.getElementById("btn-force-results");
  const btnNextQuestion = document.getElementById("btn-next-question");
  const rankingSection = document.getElementById("ranking-section");
  const rankingEmpty = document.getElementById("ranking-empty");
  const rankingTable = document.getElementById("ranking-table");
  const rankingBody = document.getElementById("ranking-body");
  let questionEndsAt = null;
  let questionSecondsFallback = null;
  let clockOffset = 0;

  function nowMs() {
    return Date.now() + clockOffset;
  }

  function updateQuestionTimer() {
    if (!questionTimerEl) return;
    if (questionEndsAt != null) {
      const seconds = Math.max(0, Math.ceil((questionEndsAt - nowMs()) / 1000));
      questionTimerEl.textContent = seconds;
      return;
    }
    if (questionSecondsFallback != null) {
      questionTimerEl.textContent = questionSecondsFallback;
    }
  }

  function setStateSections(state) {
    if (startSection) startSection.style.display = state === "WAITING" ? "block" : "none";
    if (runningSection) runningSection.style.display = state === "RUNNING" ? "block" : "none";
    if (finishedSection) finishedSection.style.display = state === "FINISHED" ? "block" : "none";
    if (currentSection) currentSection.style.display = state === "RUNNING" ? "block" : "none";
    if (rankingSection) rankingSection.style.display = state === "WAITING" ? "none" : "block";
  }

  function renderPlayers(players) {
    if (!playersList || !playersEmpty) return;
    playersList.innerHTML = "";
    if (!players || players.length === 0) {
      playersEmpty.style.display = "block";
      playersList.style.display = "none";
      return;
    }
    playersEmpty.style.display = "none";
    playersList.style.display = "block";
    players.forEach((p) => {
      const li = document.createElement("li");
      li.className = "flex items-center gap-2";
      const dot = document.createElement("span");
      dot.className = "inline-block w-2 h-2 rounded-full bg-white border";
      if (p.status === "finished") {
        dot.classList.add("bg-gray-900", "border-gray-900");
      } else if (p.status === "correct") {
        dot.classList.add("bg-green-600", "border-green-600");
      } else if (p.status === "wrong") {
        dot.classList.add("bg-red-600", "border-red-600");
      } else if (p.status === "inactive") {
        dot.classList.add("bg-gray-400", "border-gray-400");
      }
      const name = document.createElement("span");
      name.textContent = p.name;
      if (p.status === "inactive") {
        name.classList.add("line-through", "text-gray-500");
      }
      li.appendChild(dot);
      li.appendChild(name);
      playersList.appendChild(li);
    });
  }

  function format(totalSeconds) {
    const s = Math.max(0, Math.floor(totalSeconds));
    const m = Math.floor(s / 60);
    const r = s % 60;
    return String(m).padStart(2, "0") + ":" + String(r).padStart(2, "0");
  }

  function stateLabel(state) {
    if (state === "WAITING") return "ESPERANDO";
    if (state === "RUNNING") return "EN JUEGO";
    if (state === "FINISHED") return "FINALIZADA";
    return state || "";
  }

  async function poll() {
    try {
      const res = await fetch(`/rooms/${roomId}/status`, { credentials: "same-origin" });
      if (!res.ok) return;
      const data = await res.json();

      if (stateEl) stateEl.textContent = stateLabel(data.state);
      setStateSections(data.state);
      renderPlayers(data.players || []);
      if (startBtn) {
        const hasPlayers = Array.isArray(data.players) && data.players.length > 0;
        if (hasPlayers) {
          startBtn.disabled = false;
          startBtn.removeAttribute("disabled");
        } else {
          startBtn.disabled = true;
          startBtn.setAttribute("disabled", "disabled");
        }
      }

      if (countdownEl && data.secondsLeft != null) {
        countdownEl.textContent = format(data.secondsLeft);
      }

      if (data.currentQuestion && currentQuestionEl && currentAnswerEl) {
        currentQuestionEl.textContent = data.currentQuestion.statement || "-";
        currentAnswerEl.textContent = data.currentQuestion.correctOption || "-";
      } else if (currentQuestionEl && currentAnswerEl) {
        currentQuestionEl.textContent = "-";
        currentAnswerEl.textContent = "-";
      }

      if (data.serverNow != null) {
        clockOffset = data.serverNow - Date.now();
      }

      if (data.phase === "QUESTION") {
        if (data.questionEndsAt != null) {
          questionEndsAt = data.questionEndsAt;
          questionSecondsFallback = null;
        } else if (data.questionSecondsLeft != null) {
          questionEndsAt = null;
          questionSecondsFallback = data.questionSecondsLeft;
        }
      } else {
        questionEndsAt = null;
        questionSecondsFallback = null;
      }

      if (rankingBody && rankingEmpty && rankingTable) {
        const ranking = data.ranking || [];
        rankingBody.innerHTML = "";
        if (ranking.length === 0) {
          rankingEmpty.style.display = "block";
          rankingTable.style.display = "none";
        } else {
          rankingEmpty.style.display = "none";
          rankingTable.style.display = "table";
          ranking.forEach((p) => {
            const tr = document.createElement("tr");
            const tdName = document.createElement("td");
            const tdScore = document.createElement("td");
            tdName.textContent = p.name;
            tdScore.textContent = p.score;
            tdScore.className = "text-right";
            tr.appendChild(tdName);
            tr.appendChild(tdScore);
            rankingBody.appendChild(tr);
          });
        }
      }

      if (btnShowResults && btnForceResults && btnNextQuestion) {
        const phase = data.phase;
        const canShow = !!data.canShowResults;
        const auto = (data.advanceMode || "AUTO") === "AUTO";
        if (auto) {
          btnShowResults.style.display = "none";
          btnForceResults.style.display = "none";
          btnNextQuestion.style.display = "none";
        } else {
        if (phase === "RESULTS") {
          btnShowResults.style.display = "none";
          btnForceResults.style.display = "none";
          btnNextQuestion.style.display = "inline-block";
        } else {
          btnNextQuestion.style.display = "none";
          if (canShow) {
            btnShowResults.style.display = "inline-block";
            btnForceResults.style.display = "none";
          } else {
            btnShowResults.style.display = "none";
            btnForceResults.style.display = "inline-block";
          }
        }
        }
      }
    } catch (e) {
      console.error("Error actualizando lobby", e);
    }
  }

  poll();
  setInterval(poll, 2000);
  setInterval(updateQuestionTimer, 250);
})();
