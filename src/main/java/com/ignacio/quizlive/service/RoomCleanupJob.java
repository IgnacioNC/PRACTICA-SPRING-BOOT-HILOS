package com.ignacio.quizlive.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RoomCleanupJob {

    private final RoomService roomService;

    public RoomCleanupJob(RoomService roomService) {
        this.roomService = roomService;
    }

    // cada 60 segundos
    @Scheduled(fixedRate = 60_000)
    public void cleanup() {
        int deleted = roomService.cleanupWaitingRoomsOlderThanMinutes(10);
        if (deleted > 0) {
            System.out.println("[Cleanup] Salas WAITING borradas por timeout: " + deleted);
        }
    }
}