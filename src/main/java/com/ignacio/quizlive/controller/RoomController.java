package com.ignacio.quizlive.controller;

import com.ignacio.quizlive.model.Block;
import com.ignacio.quizlive.model.Room;
import com.ignacio.quizlive.model.RoomState;
import com.ignacio.quizlive.model.SelectionMode;
import com.ignacio.quizlive.model.User;
import com.ignacio.quizlive.service.BlockService;
import com.ignacio.quizlive.service.CurrentUserService;
import com.ignacio.quizlive.service.GameService;
import com.ignacio.quizlive.service.RoomService;
import com.ignacio.quizlive.service.RoomStatusService;
import com.ignacio.quizlive.service.RoomViewService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService roomService;
    private final BlockService blockService;
    private final CurrentUserService currentUserService;
    private final GameService gameService;
    private final RoomViewService roomViewService;
    private final RoomStatusService roomStatusService;

    public RoomController(RoomService roomService, BlockService blockService, CurrentUserService currentUserService,
                          GameService gameService, RoomViewService roomViewService, RoomStatusService roomStatusService) {
        this.roomService = roomService;
        this.blockService = blockService;
        this.currentUserService = currentUserService;
        this.gameService = gameService;
        this.roomViewService = roomViewService;
        this.roomStatusService = roomStatusService;
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

        RoomViewService.LobbyView view = roomViewService.buildLobbyView(room);
        if (view.isExpired()) {
            roomService.expireRoomNow(me(), id);
            return "redirect:/rooms";
        }

        model.addAttribute("room", room);
        model.addAttribute("secondsLeft", view.getSecondsLeft());
        model.addAttribute("expiresAtMs", view.getExpiresAtMs());
        model.addAttribute("hasSelection", view.isHasSelection());
        model.addAttribute("selection", view.getSelection());
        model.addAttribute("currentQuestion", view.getCurrentQuestion());
        model.addAttribute("ranking", view.getRanking());
        model.addAttribute("players", view.getPlayers());

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
        return roomStatusService.buildStatus(room);
    }
}

