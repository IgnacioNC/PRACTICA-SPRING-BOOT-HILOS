package com.ignacio.quizlive.service;

import com.ignacio.quizlive.model.*;
import com.ignacio.quizlive.repository.AnswerRepository;
import com.ignacio.quizlive.repository.PlayerRepository;
import com.ignacio.quizlive.repository.RoomQuestionRepository;
import com.ignacio.quizlive.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class GameService {

    private static final int RESULT_SECONDS = 3;
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    private final PlayerRepository playerRepository;
    private final AnswerRepository answerRepository;
    private final RoomQuestionRepository roomQuestionRepository;
    private final RoomRepository roomRepository;

    private final ConcurrentHashMap<String, RoomRuntime> runtimes = new ConcurrentHashMap<>();
    private final ExecutorService answerPool = Executors.newFixedThreadPool(8, new NamedThreadFactory("answer-pool"));

    public GameService(PlayerRepository playerRepository,
                       AnswerRepository answerRepository,
                       RoomQuestionRepository roomQuestionRepository,
                       RoomRepository roomRepository) {
        this.playerRepository = playerRepository;
        this.answerRepository = answerRepository;
        this.roomQuestionRepository = roomQuestionRepository;
        this.roomRepository = roomRepository;
    }

    public Player getPlayerById(Long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Jugador no encontrado"));
    }

    public List<Player> getPlayers(Room room) {
        return playerRepository.findByRoomOrderByJoinedAtAsc(room);
    }

    public List<Player> getRanking(Room room) {
        return playerRepository.findByRoomOrderByScoreDescNameAsc(room);
    }

    public Player joinRoom(Room room, String name, int reuseAfterSeconds) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Nombre obligatorio");
        }
        String trimmed = name.trim();
        java.util.Optional<Player> existingOpt = playerRepository.findByRoomAndName(room, trimmed);
        if (existingOpt.isPresent()) {
            Player existing = existingOpt.get();
            LocalDateTime limit = LocalDateTime.now().minusSeconds(reuseAfterSeconds);
            if (existing.getLastSeenAt() != null && existing.getLastSeenAt().isAfter(limit)) {
                throw new RuntimeException("Nombre duplicado en la sala");
            }
            existing.setLastSeenAt(LocalDateTime.now());
            return playerRepository.save(existing);
        }

        Player p = new Player();
        p.setRoom(room);
        p.setName(trimmed);
        p.setScore(0);

        return playerRepository.save(p);
    }

    @Transactional
    public void touchPlayer(Player player) {
        player.setLastSeenAt(LocalDateTime.now());
        playerRepository.save(player);
    }

    @Transactional
    public void startRoom(Room room) {
        if (room.getState() != RoomState.WAITING) {
            throw new RuntimeException("La sala ya ha empezado");
        }
        long totalPlayers = playerRepository.countByRoom(room);
        if (totalPlayers == 0) {
            throw new RuntimeException("No puedes iniciar la sala sin jugadores");
        }
        long count = roomQuestionRepository.countByRoom(room);
        if (count != room.getQuestionCount()) {
            throw new RuntimeException("Falta seleccion de preguntas");
        }
        room.setState(RoomState.RUNNING);
        room.setStartedAt(LocalDateTime.now());
        room.setCurrentQuestionIndex(1);
        room.setQuestionStartedAt(LocalDateTime.now());
        room.setPhase(RoomPhase.QUESTION);
        room.setPhaseStartedAt(LocalDateTime.now());
        roomRepository.save(room);
        if (isAuto(room)) {
            scheduleQuestionTimer(room);
        } else {
            runtime(room).questionOpen.set(true);
        }
    }

    @Transactional
    public void nextQuestion(Room room) {
        if (room.getState() != RoomState.RUNNING) return;

        // Toggle: QUESTION -> RESULTS (show feedback), RESULTS -> next question
        if (room.getPhase() == RoomPhase.QUESTION) {
            long totalPlayers = playerRepository.countByRoom(room);
            long totalAnswers = answerRepository.countByRoomQuestion(getCurrentRoomQuestion(room));
            long left = secondsLeft(room);
            if (totalPlayers > 0 && totalAnswers < totalPlayers && left > 0) {
                return; // esperar a que respondan o a que termine el tiempo
            }
            endQuestion(room);
            if (isAuto(room)) {
                scheduleResultTimer(room);
            }
            return;
        }

        int idx = room.getCurrentQuestionIndex() == null ? 0 : room.getCurrentQuestionIndex();
        if (idx >= room.getQuestionCount()) {
            room.setState(RoomState.FINISHED);
            room.setFinishedAt(LocalDateTime.now());
            roomRepository.save(room);
            cleanupRuntime(room);
            return;
        }

        room.setCurrentQuestionIndex(idx + 1);
        room.setQuestionStartedAt(LocalDateTime.now());
        room.setPhase(RoomPhase.QUESTION);
        room.setPhaseStartedAt(LocalDateTime.now());
        roomRepository.save(room);
        if (isAuto(room)) {
            scheduleQuestionTimer(room);
        } else {
            runtime(room).questionOpen.set(true);
        }
    }

    @Transactional
    public void forceEndQuestion(Room room) {
        if (room.getState() != RoomState.RUNNING) return;
        if (room.getPhase() != RoomPhase.QUESTION) return;
        endQuestion(room);
        if (isAuto(room)) {
            scheduleResultTimer(room);
        }
    }

    @Transactional
    public void stopRoom(Room room) {
        if (room.getState() == RoomState.FINISHED) return;
        room.setState(RoomState.FINISHED);
        room.setFinishedAt(LocalDateTime.now());
        roomRepository.save(room);
        cleanupRuntime(room);
    }

    public RoomQuestion getCurrentRoomQuestion(Room room) {
        int idx = room.getCurrentQuestionIndex() == null ? 0 : room.getCurrentQuestionIndex();
        return roomQuestionRepository.findByRoomAndOrderIndex(room, idx)
                .orElseThrow(() -> new RuntimeException("Pregunta actual no encontrada"));
    }

    public boolean hasAnswered(Player player, RoomQuestion rq) {
        return answerRepository.existsByPlayerAndRoomQuestion(player, rq);
    }

    public long secondsLeft(Room room) {
        if (room.getPhase() != RoomPhase.QUESTION) return 0;
        if (room.getQuestionStartedAt() == null) return 0;
        try {
            RoomQuestion rq = getCurrentRoomQuestion(room);
            if (allAnswered(room, rq)) return 0;
        } catch (Exception ignored) {
        }
        LocalDateTime end = room.getQuestionStartedAt().plusSeconds(room.getTimePerQuestion());
        long secondsLeft = Duration.between(LocalDateTime.now(), end).getSeconds();
        return Math.max(0, secondsLeft);
    }

    @Transactional
    public void advanceIfTimeExpired(Room room) {
        // Desactivado: el profesor decide cuándo avanzar
    }

    @Transactional
    public void finishIfNoActivePlayers(Room room, int inactiveSeconds) {
        if (room.getState() != RoomState.RUNNING) return;
        LocalDateTime limit = LocalDateTime.now().minusSeconds(inactiveSeconds);
        long active = playerRepository.countByRoomAndLastSeenAtAfter(room, limit);
        if (active == 0) {
            stopRoom(room);
        }
    }

    @Transactional
    public void endQuestion(Room room) {
        if (room.getState() != RoomState.RUNNING) return;
        runtime(room).questionOpen.set(false);
        if (!isAuto(room)) {
            try {
                RoomQuestion rq = getCurrentRoomQuestion(room);
                List<Answer> answers = answerRepository.findByRoomQuestion(rq);
                for (Answer a : answers) {
                    if (a.isCorrect()) {
                        Player p = a.getPlayer();
                        p.setScore(p.getScore() + 1);
                        playerRepository.save(p);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        room.setPhase(RoomPhase.RESULTS);
        room.setPhaseStartedAt(LocalDateTime.now());
        roomRepository.save(room);
    }

    public long resultSecondsLeft(Room room) {
        if (room.getPhase() != RoomPhase.RESULTS) return 0;
        if (room.getPhaseStartedAt() == null) return 0;
        long left = RESULT_SECONDS - Duration.between(LocalDateTime.now(), room.getPhaseStartedAt()).getSeconds();
        return Math.max(0, left);
    }

    public Answer getAnswer(Player player, RoomQuestion rq) {
        return answerRepository.findByPlayerAndRoomQuestion(player, rq).orElse(null);
    }

    public java.util.Map<String, Object> buildPlayStatus(Room room, Player player) {
        touchPlayer(player);
        finishIfNoActivePlayers(room, 15);

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("state", room.getState().name());
        out.put("phase", room.getPhase() == null ? RoomPhase.QUESTION.name() : room.getPhase().name());
        boolean manualAdvance = room.getAdvanceMode() == null
                || room.getAdvanceMode() == AdvanceMode.MANUAL;
        if (!manualAdvance || (room.getPhase() != null && room.getPhase() == RoomPhase.RESULTS)) {
            out.put("score", player.getScore());
            out.put("position", getPosition(room, player));
        }
        out.put("advanceMode", room.getAdvanceMode() == null ? "AUTO" : room.getAdvanceMode().name());

        if (room.getState() == RoomState.RUNNING && room.getPhase() == RoomPhase.QUESTION) {
            RoomQuestion rq = getCurrentRoomQuestion(room);
            out.put("secondsLeft", secondsLeft(room));
            if (room.getQuestionStartedAt() != null) {
                long endMs = room.getQuestionStartedAt()
                        .plusSeconds(room.getTimePerQuestion())
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
                out.put("questionEndsAt", endMs);
                out.put("serverNow", System.currentTimeMillis());
            }
            out.put("alreadyAnswered", hasAnswered(player, rq));
            java.util.Map<String, String> q = new java.util.HashMap<>();
            q.put("statement", rq.getQuestion().getStatement());
            q.put("optionA", rq.getQuestion().getOptionA());
            q.put("optionB", rq.getQuestion().getOptionB());
            q.put("optionC", rq.getQuestion().getOptionC());
            q.put("optionD", rq.getQuestion().getOptionD());
            out.put("question", q);
        }
        if (room.getState() == RoomState.RUNNING && room.getPhase() == RoomPhase.RESULTS) {
            RoomQuestion rq = getCurrentRoomQuestion(room);
            Answer ans = getAnswer(player, rq);
            boolean answered = ans != null;
            boolean correct = answered && ans.isCorrect();
            out.put("resultSecondsLeft", resultSecondsLeft(room));
            out.put("answered", answered);
            out.put("correct", correct);
            out.put("statement", rq.getQuestion().getStatement());
        }

        return out;
    }

    public List<java.util.Map<String, String>> buildPlayerStates(Room room, int inactiveSeconds) {
        List<Player> playersRaw = getPlayers(room);
        RoomQuestion rq = null;
        boolean timeUp = false;
        java.time.LocalDateTime inactiveLimit = java.time.LocalDateTime.now().minusSeconds(inactiveSeconds);
        try {
            rq = getCurrentRoomQuestion(room);
            timeUp = secondsLeft(room) == 0;
        } catch (Exception ignored) {
        }
        java.util.List<java.util.Map<String, String>> playerStates = new java.util.ArrayList<>();
        for (Player p : playersRaw) {
            String status = "blank";
            if (room.getState() == RoomState.FINISHED) {
                status = "finished";
            } else if (p.getLastSeenAt() != null && p.getLastSeenAt().isBefore(inactiveLimit)) {
                status = "inactive";
            } else if (rq != null) {
                Answer a = getAnswer(p, rq);
                if (a != null) {
                    status = a.isCorrect() ? "correct" : "wrong";
                } else if (timeUp) {
                    status = "wrong";
                }
            }
            playerStates.add(java.util.Map.of("name", p.getName(), "status", status));
        }
        return playerStates;
    }

    public int getPosition(Room room, Player player) {
        List<Player> ranking = getRanking(room);
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getId().equals(player.getId())) {
                return i + 1;
            }
        }
        return ranking.size();
    }

    @Transactional
    public void removePlayer(Player player) {
        playerRepository.delete(player);
    }

    @Transactional
    public void submitAnswer(Player player, Room room, String option) {
        RoomRuntime rt = runtime(room);
        try {
            answerPool.submit(() -> {
                String thread = Thread.currentThread().getName();
                logger.info("[Room {}] [{}] Respuesta recibida", room.getPin(), thread);
                synchronized (rt.lock) {
                    if (room.getState() != RoomState.RUNNING) {
                        throw new RuntimeException("La sala no esta en juego");
                    }
                    if (isAuto(room) && !rt.questionOpen.get()) {
                        throw new RuntimeException("Tiempo agotado");
                    }

                    RoomQuestion rq = getCurrentRoomQuestion(room);
                    if (hasAnswered(player, rq)) {
                        throw new RuntimeException("Ya has respondido");
                    }
                    long secondsLeft = secondsLeft(room);
                    if (secondsLeft <= 0) {
                        throw new RuntimeException("Tiempo agotado");
                    }

                    String opt = option == null ? "" : option.trim().toUpperCase();
                    if (!(opt.equals("A") || opt.equals("B") || opt.equals("C") || opt.equals("D"))) {
                        throw new RuntimeException("Opcion invalida");
                    }

                    String key = player.getId() + ":" + rq.getId();
                    if (rt.answered.putIfAbsent(key, opt) != null) {
                        throw new RuntimeException("Ya has respondido");
                    }

                    Answer a = new Answer();
                    a.setPlayer(player);
                    a.setRoomQuestion(rq);
                    a.setSelectedOption(opt);

                    boolean correct = rq.getQuestion().getCorrectOption().equalsIgnoreCase(opt);
                    a.setCorrect(correct);

                    answerRepository.save(a);

                    if (correct && isAuto(room)) {
                        player.setScore(player.getScore() + 1);
                        playerRepository.save(player);
                        rt.scores.merge(player.getId(), 1, Integer::sum);
                    }

                    if (isAuto(room)) {
                        boolean all = allAnswered(room, rq);
                        if (all) {
                            rt.cancelTimers();
                            endQuestion(room);
                            scheduleResultTimer(room);
                        }
                    }

                    logger.info("[Room {}] [{}] Respuesta procesada", room.getPin(), thread);
                }
            }).get();
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Error procesando respuesta");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrumpido");
        }
    }

    public boolean allAnswered(Room room, RoomQuestion rq) {
        long totalPlayers = playerRepository.countByRoom(room);
        long totalAnswers = answerRepository.countByRoomQuestion(rq);
        return totalPlayers > 0 && totalAnswers >= totalPlayers;
    }

    public boolean canShowResults(Room room) {
        if (room.getPhase() != RoomPhase.QUESTION) return false;
        try {
            RoomQuestion rq = getCurrentRoomQuestion(room);
            return allAnswered(room, rq) || secondsLeft(room) == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private RoomRuntime runtime(Room room) {
        return runtimes.computeIfAbsent(room.getPin(), RoomRuntime::new);
    }

    private boolean isAuto(Room room) {
        return room.getAdvanceMode() == null || room.getAdvanceMode() == AdvanceMode.AUTO;
    }

    private void scheduleQuestionTimer(Room room) {
        RoomRuntime rt = runtime(room);
        rt.cancelTimers();
        rt.questionOpen.set(true);
        long delay = room.getTimePerQuestion();
        rt.questionTask = rt.scheduler.schedule(() -> {
            logger.info("[Room {}] [{}] Temporizador finalizado", room.getPin(), Thread.currentThread().getName());
            rt.questionOpen.set(false);
            try {
                forceEndQuestion(room);
            } catch (Exception ex) {
                logger.warn("[Room {}] [{}] Error al finalizar pregunta: {}", room.getPin(), Thread.currentThread().getName(), ex.getMessage());
            }
        }, delay, TimeUnit.SECONDS);
        logger.info("[Room {}] [{}] Temporizador iniciado ({}s)", room.getPin(), Thread.currentThread().getName(), delay);
    }

    private void scheduleResultTimer(Room room) {
        RoomRuntime rt = runtime(room);
        rt.resultTask = rt.scheduler.schedule(() -> {
            logger.info("[Room {}] [{}] Fin resultados, siguiente pregunta", room.getPin(), Thread.currentThread().getName());
            try {
                nextQuestion(room);
            } catch (Exception ex) {
                logger.warn("[Room {}] [{}] Error al avanzar: {}", room.getPin(), Thread.currentThread().getName(), ex.getMessage());
            }
        }, RESULT_SECONDS, TimeUnit.SECONDS);
    }

    private void cleanupRuntime(Room room) {
        RoomRuntime rt = runtimes.remove(room.getPin());
        if (rt != null) {
            rt.shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        for (RoomRuntime rt : runtimes.values()) {
            rt.shutdown();
        }
        runtimes.clear();
        answerPool.shutdownNow();
    }

    private static final class RoomRuntime {
        final String pin;
        final ScheduledExecutorService scheduler;
        final AtomicBoolean questionOpen = new AtomicBoolean(false);
        final ConcurrentHashMap<String, String> answered = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Long, Integer> scores = new ConcurrentHashMap<>();
        final Object lock = new Object();
        ScheduledFuture<?> questionTask;
        ScheduledFuture<?> resultTask;

        RoomRuntime(String pin) {
            this.pin = pin;
            this.scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("room-" + pin + "-timer"));
        }

        void cancelTimers() {
            if (questionTask != null) questionTask.cancel(false);
            if (resultTask != null) resultTask.cancel(false);
        }

        void shutdown() {
            cancelTimers();
            scheduler.shutdownNow();
            answered.clear();
            scores.clear();
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private int idx = 1;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(prefix + "-" + idx++);
            t.setDaemon(true);
            return t;
        }
    }
}
