package com.ignacio.quizlive.service;

import com.ignacio.quizlive.model.*;
import com.ignacio.quizlive.repository.RoomQuestionRepository;
import com.ignacio.quizlive.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomQuestionRepository roomQuestionRepository;
    private final com.ignacio.quizlive.repository.PlayerRepository playerRepository;
    private final com.ignacio.quizlive.repository.AnswerRepository answerRepository;
    private final SecureRandom random = new SecureRandom();

    public RoomService(RoomRepository roomRepository,
                       RoomQuestionRepository roomQuestionRepository,
                       com.ignacio.quizlive.repository.PlayerRepository playerRepository,
                       com.ignacio.quizlive.repository.AnswerRepository answerRepository) {
        this.roomRepository = roomRepository;
        this.roomQuestionRepository = roomQuestionRepository;
        this.playerRepository = playerRepository;
        this.answerRepository = answerRepository;
    }

    public List<Room> myRooms(User host) {
        return roomRepository.findByHost(host);
    }

    public Room getByPin(String pin) {
        return roomRepository.findByPin(pin)
                .orElseThrow(() -> new RuntimeException("PIN no valido"));
    }

    public Room createRoom(User host, Block block, int questionCount, int timePerQuestion, SelectionMode mode, AdvanceMode advanceMode) {
        if (host == null) throw new RuntimeException("Host obligatorio");
        if (block == null) throw new RuntimeException("Bloque obligatorio");

        int total = (block.getQuestions() == null) ? 0 : block.getQuestions().size();
        if (total < 20) {
            throw new RuntimeException("El bloque debe tener al menos 20 preguntas para crear una sala");
        }

        if (questionCount < 1 || questionCount > total) {
            throw new RuntimeException("Numero de preguntas invalido (1.." + total + ")");
        }

        if (timePerQuestion < 5 || timePerQuestion > 120) {
            throw new RuntimeException("Tiempo por pregunta invalido (5..120 segundos)");
        }

        if (mode == null) mode = SelectionMode.MANUAL;
        if (advanceMode == null) advanceMode = AdvanceMode.AUTO;

        Room room = new Room();
        room.setHost(host);
        room.setBlock(block);
        room.setQuestionCount(questionCount);
        room.setTimePerQuestion(timePerQuestion);
        room.setSelectionMode(mode);
        room.setAdvanceMode(advanceMode);
        room.setState(RoomState.WAITING);
        room.setPin(generateUniquePin());
        room.setCurrentQuestionIndex(0);

        LocalDateTime now = LocalDateTime.now();
        room.setCreatedAt(now);
        room.setLastActivityAt(now);

        Room saved = roomRepository.save(room);

        if (mode == SelectionMode.RANDOM) {
            assignRandomQuestions(saved);
        }

        return saved;
    }

    public Room getMyRoomById(User host, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Sala no encontrada"));

        if (host == null || room.getHost() == null || room.getHost().getId() == null) {
            throw new RuntimeException("Host invalido");
        }

        if (!room.getHost().getId().equals(host.getId())) {
            throw new RuntimeException("No tienes permiso para acceder a esta sala");
        }
        return room;
    }

    private String generateUniquePin() {
        for (int i = 0; i < 20; i++) {
            String pin = String.valueOf(100000 + random.nextInt(900000));
            if (!roomRepository.existsByPin(pin)) return pin;
        }
        throw new RuntimeException("No se pudo generar un PIN unico");
    }

    @Transactional
    public void assignManualQuestions(User host, Long roomId, List<Long> questionIdsInOrder) {
        Room room = getMyRoomById(host, roomId);

        if (questionIdsInOrder == null || questionIdsInOrder.size() != room.getQuestionCount()) {
            throw new RuntimeException("Debes seleccionar exactamente " + room.getQuestionCount() + " preguntas");
        }

        roomQuestionRepository.deleteByRoom(room);

        for (int i = 0; i < questionIdsInOrder.size(); i++) {
            Long qid = questionIdsInOrder.get(i);

            Question q = room.getBlock().getQuestions().stream()
                    .filter(qq -> qq.getId().equals(qid))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Pregunta no pertenece al bloque"));

            RoomQuestion rq = new RoomQuestion();
            rq.setRoom(room);
            rq.setQuestion(q);
            rq.setOrderIndex(i + 1);

            roomQuestionRepository.save(rq);
        }

        room.setLastActivityAt(LocalDateTime.now());
        roomRepository.save(room);
    }

    @Transactional
    public void assignRandomQuestions(Room room) {
        roomQuestionRepository.deleteByRoom(room);

        List<Question> all = new ArrayList<>(room.getBlock().getQuestions());
        Collections.shuffle(all, random);

        for (int i = 0; i < room.getQuestionCount(); i++) {
            RoomQuestion rq = new RoomQuestion();
            rq.setRoom(room);
            rq.setQuestion(all.get(i));
            rq.setOrderIndex(i + 1);
            roomQuestionRepository.save(rq);
        }

        room.setLastActivityAt(LocalDateTime.now());
        roomRepository.save(room);
    }

    public boolean hasSelection(Room room) {
        return roomQuestionRepository.countByRoom(room) == room.getQuestionCount();
    }

    public List<RoomQuestion> getSelection(Room room) {
        return roomQuestionRepository.findByRoomOrderByOrderIndexAsc(room);
    }

    @Transactional
    public void deleteMyRoom(User host, Long roomId) {
        Room room = getMyRoomById(host, roomId);
        answerRepository.deleteByRoomQuestionRoom(room);
        playerRepository.deleteByRoom(room);
        roomQuestionRepository.deleteByRoom(room);
        roomRepository.delete(room);
    }

    @Transactional
    public void deleteAllMyRooms(User host) {
        List<Room> rooms = roomRepository.findByHost(host);
        for (Room room : rooms) {
            answerRepository.deleteByRoomQuestionRoom(room);
            playerRepository.deleteByRoom(room);
            roomQuestionRepository.deleteByRoom(room);
            roomRepository.delete(room);
        }
    }

    @Transactional
    public int cleanupWaitingRoomsOlderThanMinutes(int minutes) {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(minutes);
        List<Room> expired = roomRepository.findByStateAndLastActivityAtBefore(RoomState.WAITING, limit);

        for (Room r : expired) {
            answerRepository.deleteByRoomQuestionRoom(r);
            playerRepository.deleteByRoom(r);
            roomQuestionRepository.deleteByRoom(r);
            roomRepository.delete(r);
        }
        return expired.size();
    }

    @Transactional
    public void expireRoomNow(User host, Long roomId) {
        Room room = getMyRoomById(host, roomId);
        if (room.getState() == RoomState.WAITING) {
            answerRepository.deleteByRoomQuestionRoom(room);
            playerRepository.deleteByRoom(room);
            roomQuestionRepository.deleteByRoom(room);
            roomRepository.delete(room);
        }
    }

    public long secondsLeftToExpire(Room room) {
        if (room.getState() != RoomState.WAITING) return 0;
        LocalDateTime expiresAt = room.getLastActivityAt().plusMinutes(10);
        long secondsLeft = java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        return Math.max(0, secondsLeft);
    }

}
