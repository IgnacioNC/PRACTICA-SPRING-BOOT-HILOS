package com.ignacio.quizlive.controller;

import com.ignacio.quizlive.model.Block;
import com.ignacio.quizlive.model.Room;
import com.ignacio.quizlive.model.RoomPhase;
import com.ignacio.quizlive.model.RoomState;
import com.ignacio.quizlive.model.SelectionMode;
import com.ignacio.quizlive.model.User;
import com.ignacio.quizlive.service.BlockService;
import com.ignacio.quizlive.service.CurrentUserService;
import com.ignacio.quizlive.service.GameService;
import com.ignacio.quizlive.service.RoomService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService roomService;
    private final BlockService blockService;
    private final CurrentUserService currentUserService;
    private final GameService gameService;

    public RoomController(RoomService roomService, BlockService blockService, CurrentUserService currentUserService,
                          GameService gameService) {
        this.roomService = roomService;
        this.blockService = blockService;
        this.currentUserService = currentUserService;
        this.gameService = gameService;
    }

    private User me() {
        return currentUserService.getCurrentUser();
    }

    @GetMapping
    public String list(Model model) {
        List<Room> rooms = roomService.myRooms(me());
        for (Room room : rooms) {
            if (room.getState() == RoomState.RUNNING) {
                gameService.finishIfNoActivePlayers(room, 15);
            }
        }
        model.addAttribute("rooms", rooms);
        return "rooms/list";
    }

    @GetMapping("/new")
    public String newRoomForm(Model model) {
        List<Block> mine = blockService.getBlocksByUser(me());
        List<Block> usable = mine.stream()
                .filter(blockService::canBeUsedInRoom)
                .collect(Collectors.toList());

        model.addAttribute("blocks", usable);

        model.addAttribute("questionCount", 10);
        model.addAttribute("timePerQuestion", 15);
        model.addAttribute("selectionMode", SelectionMode.MANUAL);
        model.addAttribute("advanceMode", com.ignacio.quizlive.model.AdvanceMode.AUTO);

        return "rooms/new";
    }

    @PostMapping
    public String create(@RequestParam Long blockId,
                         @RequestParam int questionCount,
                         @RequestParam int timePerQuestion,
                         @RequestParam SelectionMode selectionMode,
                         @RequestParam com.ignacio.quizlive.model.AdvanceMode advanceMode,
                         Model model) {
        try {
            Block block = blockService.getMyBlockById(me(), blockId);

            Room room = roomService.createRoom(me(), block, questionCount, timePerQuestion, selectionMode, advanceMode);

            if (selectionMode == SelectionMode.MANUAL) {
                return "redirect:/rooms/" + room.getId() + "/select";
            }

            return "redirect:/rooms/" + room.getId();

        } catch (RuntimeException ex) {
            List<Block> mine = blockService.getBlocksByUser(me());
            List<Block> usable = mine.stream().filter(blockService::canBeUsedInRoom).collect(Collectors.toList());

            model.addAttribute("blocks", usable);
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("blockId", blockId);
            model.addAttribute("questionCount", questionCount);
            model.addAttribute("timePerQuestion", timePerQuestion);
            model.addAttribute("selectionMode", selectionMode);
            model.addAttribute("advanceMode", advanceMode);

            return "rooms/new";
        }
    }

    @GetMapping("/{id}/select")
    public String selectQuestionsForm(@PathVariable Long id, Model model) {
        Room room = roomService.getMyRoomById(me(), id);

        model.addAttribute("room", room);
        model.addAttribute("block", room.getBlock());
        model.addAttribute("questions", room.getBlock().getQuestions());
        model.addAttribute("selectedCount", room.getQuestionCount());

        return "rooms/select";
    }

    @PostMapping("/{id}/select")
    public String saveSelection(@PathVariable Long id,
                                @RequestParam(name = "questionIds", required = false) List<Long> questionIds,
                                Model model) {
        Room room = roomService.getMyRoomById(me(), id);

        try {
            roomService.assignManualQuestions(me(), id, questionIds);
            return "redirect:/rooms/" + id;
        } catch (RuntimeException ex) {
            model.addAttribute("room", room);
            model.addAttribute("block", room.getBlock());
            model.addAttribute("questions", room.getBlock().getQuestions());
            model.addAttribute("selectedCount", room.getQuestionCount());
            model.addAttribute("error", ex.getMessage());
            return "rooms/select";
        }
    }

    @GetMapping("/{id}")
    public String lobby(@PathVariable Long id, Model model) {
        Room room;
        try {
            room = roomService.getMyRoomById(me(), id);
        } catch (RuntimeException ex) {
            return "redirect:/rooms";
        }

        long secondsLeft = 0;
        long expiresAtMs = 0;

        if (room.getState() == RoomState.WAITING) {
            LocalDateTime expiresAt = room.getLastActivityAt().plusMinutes(10);

            secondsLeft = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
            if (secondsLeft < 0) secondsLeft = 0;

            expiresAtMs = expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            if (secondsLeft == 0) {
                roomService.expireRoomNow(me(), id);
                return "redirect:/rooms";
            }
        }

        boolean hasSelection = false;
        try {
            hasSelection = roomService.hasSelection(room);
            model.addAttribute("selection", roomService.getSelection(room));
        } catch (Exception ignored) {
            model.addAttribute("selection", java.util.List.of());
        }

        if (room.getState() == RoomState.RUNNING) {
            try {
                com.ignacio.quizlive.model.RoomQuestion rq = gameService.getCurrentRoomQuestion(room);
                model.addAttribute("currentQuestion", rq.getQuestion());
            } catch (Exception ignored) {
                model.addAttribute("currentQuestion", null);
            }
        }

        if (room.getState() != RoomState.WAITING) {
            model.addAttribute("ranking", gameService.getRanking(room));
        } else {
            model.addAttribute("ranking", java.util.List.of());
        }

        model.addAttribute("room", room);
        model.addAttribute("secondsLeft", secondsLeft);
        model.addAttribute("expiresAtMs", expiresAtMs);
        model.addAttribute("hasSelection", hasSelection);
        java.util.List<java.util.Map<String, String>> playerStates = new java.util.ArrayList<>();
        java.util.List<com.ignacio.quizlive.model.Player> playersRaw = gameService.getPlayers(room);
        com.ignacio.quizlive.model.RoomQuestion rq = null;
        boolean timeUp = false;
        java.time.LocalDateTime inactiveLimit = java.time.LocalDateTime.now().minusSeconds(15);
        try {
            rq = gameService.getCurrentRoomQuestion(room);
            timeUp = gameService.secondsLeft(room) == 0;
        } catch (Exception ignored) {
        }
        for (com.ignacio.quizlive.model.Player p : playersRaw) {
            String status = "blank";
            if (room.getState() == RoomState.FINISHED) {
                status = "finished";
            } else if (p.getLastSeenAt() != null && p.getLastSeenAt().isBefore(inactiveLimit)) {
                status = "inactive";
            } else if (rq != null) {
                com.ignacio.quizlive.model.Answer a = gameService.getAnswer(p, rq);
                if (a != null) {
                    status = a.isCorrect() ? "correct" : "wrong";
                } else if (timeUp) {
                    status = "wrong";
                }
            }
            playerStates.add(java.util.Map.of("name", p.getName(), "status", status));
        }
        model.addAttribute("players", playerStates);

        return "rooms/lobby";
    }

    @PostMapping("/{id}/start")
    public String start(@PathVariable Long id) {
        Room room = roomService.getMyRoomById(me(), id);
        try {
            gameService.startRoom(room);
            return "redirect:/rooms/" + id;
        } catch (RuntimeException ex) {
            String msg = java.net.URLEncoder.encode(ex.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
            return "redirect:/rooms/" + id + "?error=" + msg;
        }
    }

    @PostMapping("/{id}/next")
    public String next(@PathVariable Long id) {
        Room room = roomService.getMyRoomById(me(), id);
        gameService.nextQuestion(room);
        return "redirect:/rooms/" + id;
    }

    @PostMapping("/{id}/force")
    public String force(@PathVariable Long id) {
        Room room = roomService.getMyRoomById(me(), id);
        gameService.forceEndQuestion(room);
        return "redirect:/rooms/" + id;
    }

    @PostMapping("/{id}/stop")
    public String stop(@PathVariable Long id) {
        Room room = roomService.getMyRoomById(me(), id);
        gameService.stopRoom(room);
        return "redirect:/rooms/" + id;
    }

    @GetMapping("/{id}/results")
    public String results(@PathVariable Long id, Model model) {
        Room room = roomService.getMyRoomById(me(), id);
        model.addAttribute("room", room);
        model.addAttribute("ranking", gameService.getRanking(room));
        return "rooms/results";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        roomService.deleteMyRoom(me(), id);
        return "redirect:/rooms";
    }

    @PostMapping("/delete-all")
    public String deleteAll() {
        roomService.deleteAllMyRooms(me());
        return "redirect:/rooms";
    }

    @GetMapping("/api/my")
    @ResponseBody
    public List<Long> myRoomIds() {
        return roomService.myRooms(me()).stream().map(Room::getId).toList();
    }

    @PostMapping("/{id}/expire")
    @ResponseBody
    public void expire(@PathVariable Long id) {
        roomService.expireRoomNow(me(), id);
    }

    @GetMapping("/{id}/status")
    @ResponseBody
    public java.util.Map<String, Object> status(@PathVariable Long id) {
        Room room = roomService.getMyRoomById(me(), id);
        // sin auto-avance, el anfitrión controla el ritmo
        gameService.finishIfNoActivePlayers(room, 15);
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("state", room.getState().name());
        out.put("phase", room.getPhase() == null ? RoomPhase.QUESTION.name() : room.getPhase().name());
        out.put("canShowResults", gameService.canShowResults(room));
        out.put("advanceMode", room.getAdvanceMode() == null ? "AUTO" : room.getAdvanceMode().name());
        out.put("secondsLeft", roomService.secondsLeftToExpire(room));
        java.util.List<java.util.Map<String, String>> playersOut = new java.util.ArrayList<>();
        java.util.List<com.ignacio.quizlive.model.Player> playersRawOut = gameService.getPlayers(room);
        com.ignacio.quizlive.model.RoomQuestion rqOut = null;
        boolean timeUpOut = false;
        java.time.LocalDateTime inactiveLimitOut = java.time.LocalDateTime.now().minusSeconds(15);
        try {
            rqOut = gameService.getCurrentRoomQuestion(room);
            timeUpOut = gameService.secondsLeft(room) == 0;
        } catch (Exception ignored) {
        }
        for (com.ignacio.quizlive.model.Player p : playersRawOut) {
            String status = "blank";
            if (room.getState() == RoomState.FINISHED) {
                status = "finished";
            } else if (p.getLastSeenAt() != null && p.getLastSeenAt().isBefore(inactiveLimitOut)) {
                status = "inactive";
            } else if (rqOut != null) {
                com.ignacio.quizlive.model.Answer a = gameService.getAnswer(p, rqOut);
                if (a != null) {
                    status = a.isCorrect() ? "correct" : "wrong";
                } else if (timeUpOut) {
                    status = "wrong";
                }
            }
            playersOut.add(java.util.Map.of("name", p.getName(), "status", status));
        }
        out.put("players", playersOut);
        if (room.getState() != RoomState.WAITING) {
            out.put("ranking", gameService.getRanking(room).stream()
                    .map(p -> java.util.Map.of("name", p.getName(), "score", p.getScore()))
                    .toList());
        } else {
            out.put("ranking", java.util.List.of());
        }

        if (room.getState() == RoomState.RUNNING && room.getPhase() == RoomPhase.QUESTION) {
            try {
                com.ignacio.quizlive.model.RoomQuestion rq = gameService.getCurrentRoomQuestion(room);
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
                java.util.Map<String, String> q = new java.util.HashMap<>();
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
