package com.ignacio.quizlive.service;

import com.ignacio.quizlive.model.Room;
import com.ignacio.quizlive.model.RoomPhase;
import com.ignacio.quizlive.model.RoomState;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoomStatusService {

    private final GameService gameService;
    private final RoomService roomService;

    public RoomStatusService(GameService gameService, RoomService roomService) {
        this.gameService = gameService;
        this.roomService = roomService;
    }

    public Map<String, Object> buildStatus(Room room) {
        gameService.finishIfNoActivePlayers(room, 15);

        Map<String, Object> out = new HashMap<>();
        out.put("state", room.getState().name());
        out.put("phase", room.getPhase() == null ? RoomPhase.QUESTION.name() : room.getPhase().name());
        out.put("canShowResults", gameService.canShowResults(room));
        out.put("advanceMode", room.getAdvanceMode() == null ? "AUTO" : room.getAdvanceMode().name());
        out.put("secondsLeft", roomService.secondsLeftToExpire(room));

        List<Map<String, String>> playersOut = gameService.buildPlayerStates(room, 15);
        out.put("players", playersOut);

        if (room.getState() != RoomState.WAITING) {
            out.put("ranking", gameService.getRanking(room).stream()
                    .map(p -> Map.of("name", p.getName(), "score", p.getScore()))
                    .toList());
        } else {
            out.put("ranking", List.of());
        }

        if (room.getState() == RoomState.RUNNING && room.getPhase() == RoomPhase.QUESTION) {
            try {
                var rq = gameService.getCurrentRoomQuestion(room);
                out.put("questionSecondsLeft", gameService.secondsLeft(room));
                if (room.getQuestionStartedAt() != null) {
                    long endMs = room.getQuestionStartedAt()
                            .plusSeconds(room.getTimePerQuestion())
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();
                    out.put("questionEndsAt", endMs);
                    out.put("serverNow", System.currentTimeMillis());
                }
                Map<String, String> q = new HashMap<>();
                q.put("statement", rq.getQuestion().getStatement());
                q.put("correctOption", rq.getQuestion().getCorrectOption());
                out.put("currentQuestion", q);
            } catch (Exception ignored) {
                out.put("currentQuestion", null);
            }
        }

        if (room.getState() == RoomState.RUNNING && room.getPhase() == RoomPhase.RESULTS) {
            out.put("resultSecondsLeft", gameService.resultSecondsLeft(room));
        }

        return out;
    }
}
