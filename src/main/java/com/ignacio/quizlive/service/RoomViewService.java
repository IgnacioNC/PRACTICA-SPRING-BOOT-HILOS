package com.ignacio.quizlive.service;

import com.ignacio.quizlive.model.Room;
import com.ignacio.quizlive.model.RoomQuestion;
import com.ignacio.quizlive.model.RoomState;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class RoomViewService {

    public static final class LobbyView {
        private final boolean expired;
        private final long secondsLeft;
        private final long expiresAtMs;
        private final boolean hasSelection;
        private final List<RoomQuestion> selection;
        private final Object currentQuestion;
        private final List<?> ranking;
        private final List<java.util.Map<String, String>> players;

        public LobbyView(boolean expired, long secondsLeft, long expiresAtMs,
                         boolean hasSelection, List<RoomQuestion> selection,
                         Object currentQuestion, List<?> ranking,
                         List<java.util.Map<String, String>> players) {
            this.expired = expired;
            this.secondsLeft = secondsLeft;
            this.expiresAtMs = expiresAtMs;
            this.hasSelection = hasSelection;
            this.selection = selection;
            this.currentQuestion = currentQuestion;
            this.ranking = ranking;
            this.players = players;
        }

        public boolean isExpired() { return expired; }
        public long getSecondsLeft() { return secondsLeft; }
        public long getExpiresAtMs() { return expiresAtMs; }
        public boolean isHasSelection() { return hasSelection; }
        public List<RoomQuestion> getSelection() { return selection; }
        public Object getCurrentQuestion() { return currentQuestion; }
        public List<?> getRanking() { return ranking; }
        public List<java.util.Map<String, String>> getPlayers() { return players; }
    }

    private final RoomService roomService;
    private final GameService gameService;

    public RoomViewService(RoomService roomService, GameService gameService) {
        this.roomService = roomService;
        this.gameService = gameService;
    }

    public LobbyView buildLobbyView(Room room) {
        long secondsLeft = 0;
        long expiresAtMs = 0;
        boolean expired = false;

        if (room.getState() == RoomState.WAITING) {
            LocalDateTime expiresAt = room.getLastActivityAt().plusMinutes(10);
            secondsLeft = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
            if (secondsLeft < 0) secondsLeft = 0;
            expiresAtMs = expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            expired = secondsLeft == 0;
        }

        boolean hasSelection = false;
        List<RoomQuestion> selection = List.of();
        try {
            hasSelection = roomService.hasSelection(room);
            selection = roomService.getSelection(room);
        } catch (Exception ignored) {
        }

        Object currentQuestion = null;
        if (room.getState() == RoomState.RUNNING) {
            try {
                var rq = gameService.getCurrentRoomQuestion(room);
                currentQuestion = rq.getQuestion();
            } catch (Exception ignored) {
                currentQuestion = null;
            }
        }

        List<?> ranking;
        if (room.getState() != RoomState.WAITING) {
            ranking = gameService.getRanking(room);
        } else {
            ranking = List.of();
        }

        List<java.util.Map<String, String>> players = gameService.buildPlayerStates(room, 15);

        return new LobbyView(expired, secondsLeft, expiresAtMs, hasSelection, selection, currentQuestion, ranking, players);
    }
}
