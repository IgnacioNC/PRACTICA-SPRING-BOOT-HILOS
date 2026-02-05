package com.ignacio.quizlive.controller;

import com.ignacio.quizlive.model.Answer;
import com.ignacio.quizlive.model.Player;
import com.ignacio.quizlive.model.Room;
import com.ignacio.quizlive.model.RoomPhase;
import com.ignacio.quizlive.model.RoomQuestion;
import com.ignacio.quizlive.model.RoomState;
import com.ignacio.quizlive.service.GameService;
import com.ignacio.quizlive.service.RoomService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Controller
public class PlayerController {

    private final RoomService roomService;
    private final GameService gameService;

    public PlayerController(RoomService roomService, GameService gameService) {
        this.roomService = roomService;
        this.gameService = gameService;
    }

    @GetMapping("/join")
    public String joinForm() {
        return "rooms/join";
    }

    @PostMapping("/join")
    public String join(@RequestParam String pin,
                       @RequestParam String name,
                       HttpSession session,
                       Model model) {
        try {
            Room room = roomService.getByPin(pin);

            if (room.getState() != RoomState.WAITING) {
                throw new RuntimeException("La sala no admite jugadores ahora");
            }

            Player existing = getSessionPlayer(session);
            if (existing != null && !existing.getRoom().getId().equals(room.getId())) {
                if (existing.getRoom().getState() == RoomState.FINISHED) {
                    session.removeAttribute("playerId");
                } else {
                    throw new RuntimeException("Ya estas en otra sala");
                }
            }

            Player player = gameService.joinRoom(room, name, 15);
            session.setAttribute("playerId", player.getId());

            return "redirect:/play/" + room.getPin();
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("pin", pin);
            model.addAttribute("name", name);
            return "rooms/join";
        }
    }

    @GetMapping("/play/{pin}")
    public String play(@PathVariable String pin, HttpSession session, Model model) {
        Room room;
        try {
            room = roomService.getByPin(pin);
        } catch (RuntimeException ex) {
            return "redirect:/join";
        }

        Player player = getSessionPlayer(session);
        if (player == null || !player.getRoom().getId().equals(room.getId())) {
            return "redirect:/join";
        }

        gameService.touchPlayer(player);
        model.addAttribute("room", room);
        model.addAttribute("player", player);

        if (room.getState() == RoomState.WAITING) {
            return "rooms/play";
        }

        if (room.getState() == RoomState.FINISHED) {
            model.addAttribute("ranking", gameService.getRanking(room));
            return "rooms/results";
        }

        RoomQuestion rq = gameService.getCurrentRoomQuestion(room);
        boolean alreadyAnswered = gameService.hasAnswered(player, rq);
        long secondsLeft = gameService.secondsLeft(room);
        long resultSecondsLeft = gameService.resultSecondsLeft(room);
        Answer ans = gameService.getAnswer(player, rq);
        boolean correct = ans != null && ans.isCorrect();

        model.addAttribute("rq", rq);
        model.addAttribute("secondsLeft", secondsLeft);
        model.addAttribute("resultSecondsLeft", resultSecondsLeft);
        model.addAttribute("alreadyAnswered", alreadyAnswered);
        model.addAttribute("score", player.getScore());
        model.addAttribute("position", gameService.getPosition(room, player));
        model.addAttribute("correct", correct);
        model.addAttribute("phase", room.getPhase() == null ? RoomPhase.QUESTION : room.getPhase());

        return "rooms/play";
    }

    @PostMapping("/play/{pin}/leave")
    public String leave(@PathVariable String pin, HttpSession session) {
        Room room;
        try {
            room = roomService.getByPin(pin);
        } catch (RuntimeException ex) {
            session.removeAttribute("playerId");
            return "redirect:/join";
        }

        Player player = getSessionPlayer(session);
        if (player != null && player.getRoom().getId().equals(room.getId())) {
            if (room.getState() == RoomState.WAITING) {
                gameService.removePlayer(player);
            }
        }

        session.removeAttribute("playerId");
        return "redirect:/join";
    }

    @PostMapping("/play/{pin}/answer")
    public String answer(@PathVariable String pin,
                         @RequestParam String option,
                         HttpSession session,
                         Model model) {
        Room room = roomService.getByPin(pin);

        Player player = getSessionPlayer(session);
        if (player == null || !player.getRoom().getId().equals(room.getId())) {
            return "redirect:/join";
        }

        try {
            gameService.submitAnswer(player, room, option);
            return "redirect:/play/" + pin;
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            return "redirect:/play/" + pin;
        }
    }

    @GetMapping("/play/{pin}/status")
    @ResponseBody
    public java.util.Map<String, Object> playStatus(@PathVariable String pin, HttpSession session) {
        Room room;
        try {
            room = roomService.getByPin(pin);
        } catch (RuntimeException ex) {
            return java.util.Map.of("error", "room_closed");
        }

        Player player = getSessionPlayer(session);
        if (player == null || !player.getRoom().getId().equals(room.getId())) {
            return java.util.Map.of("error", "unauthorized");
        }

        gameService.touchPlayer(player);
        gameService.finishIfNoActivePlayers(room, 15);
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("state", room.getState().name());
        out.put("phase", room.getPhase() == null ? RoomPhase.QUESTION.name() : room.getPhase().name());
        boolean manualAdvance = room.getAdvanceMode() == null
                || room.getAdvanceMode() == com.ignacio.quizlive.model.AdvanceMode.MANUAL;
        if (!manualAdvance || (room.getPhase() != null && room.getPhase() == RoomPhase.RESULTS)) {
            out.put("score", player.getScore());
            out.put("position", gameService.getPosition(room, player));
        }
        out.put("advanceMode", room.getAdvanceMode() == null ? "AUTO" : room.getAdvanceMode().name());

        if (room.getState() == RoomState.RUNNING && room.getPhase() == RoomPhase.QUESTION) {
            RoomQuestion rq = gameService.getCurrentRoomQuestion(room);
            out.put("secondsLeft", gameService.secondsLeft(room));
            if (room.getQuestionStartedAt() != null) {
                long endMs = room.getQuestionStartedAt()
                        .plusSeconds(room.getTimePerQuestion())
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
                out.put("questionEndsAt", endMs);
                out.put("serverNow", System.currentTimeMillis());
            }
            out.put("alreadyAnswered", gameService.hasAnswered(player, rq));
            java.util.Map<String, String> q = new java.util.HashMap<>();
            q.put("statement", rq.getQuestion().getStatement());
            q.put("optionA", rq.getQuestion().getOptionA());
            q.put("optionB", rq.getQuestion().getOptionB());
            q.put("optionC", rq.getQuestion().getOptionC());
            q.put("optionD", rq.getQuestion().getOptionD());
            out.put("question", q);
        }
        if (room.getState() == RoomState.RUNNING && room.getPhase() == RoomPhase.RESULTS) {
            RoomQuestion rq = gameService.getCurrentRoomQuestion(room);
            Answer ans = gameService.getAnswer(player, rq);
            boolean answered = ans != null;
            boolean correct = answered && ans.isCorrect();
            out.put("resultSecondsLeft", gameService.resultSecondsLeft(room));
            out.put("answered", answered);
            out.put("correct", correct);
            out.put("statement", rq.getQuestion().getStatement());
        }

        return out;
    }

    private Player getSessionPlayer(HttpSession session) {
        Object id = session.getAttribute("playerId");
        if (id == null) return null;
        try {
            return gameService.getPlayerById((Long) id);
        } catch (RuntimeException ex) {
            session.removeAttribute("playerId");
            return null;
        }
    }
}
