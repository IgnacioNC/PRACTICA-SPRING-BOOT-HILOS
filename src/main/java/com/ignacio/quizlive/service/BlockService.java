package com.ignacio.quizlive.service;

import com.ignacio.quizlive.model.Block;
import com.ignacio.quizlive.model.User;
import com.ignacio.quizlive.repository.BlockRepository;
import com.ignacio.quizlive.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BlockService {

    private final BlockRepository blockRepository;
    private final RoomRepository roomRepository;

    public BlockService(BlockRepository blockRepository, RoomRepository roomRepository) {
        this.blockRepository = blockRepository;
        this.roomRepository = roomRepository;
    }

    public Block createBlock(String name, String description, User owner) {
        if (owner == null) {
            throw new RuntimeException("Owner obligatorio");
        }
        if (name == null || name.isBlank()) {
            throw new RuntimeException("El nombre del bloque es obligatorio");
        }

        Block block = new Block();
        block.setName(name.trim());
        block.setDescription(description == null ? null : description.trim());
        block.setOwner(owner);

        return blockRepository.save(block);
    }

    public List<Block> getBlocksByUser(User user) {
        return blockRepository.findByOwner(user);
    }

    // ✅ Propiedad por usuario (clave del enunciado)
    public Block getMyBlockById(User owner, Long blockId) {
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new RuntimeException("Bloque no encontrado"));

        if (!block.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("No tienes permiso para acceder a este bloque");
        }

        return block;
    }

    public Block updateBlock(User owner, Long blockId, String name, String description) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("El nombre del bloque es obligatorio");
        }

        Block block = getMyBlockById(owner, blockId);
        block.setName(name.trim());
        block.setDescription(description == null ? null : description.trim());

        return blockRepository.save(block);
    }

    public void deleteBlock(User owner, Long blockId) {
        Block block = getMyBlockById(owner, blockId);
        long rooms = roomRepository.countByBlock(block);
        if (rooms > 0) {
            throw new RuntimeException("No puedes borrar el bloque mientras existan salas asociadas. Borra esas salas primero.");
        }
        blockRepository.delete(block);
    }

    public boolean canBeUsedInRoom(Block block) {
        return block.getQuestions() != null && block.getQuestions().size() >= 20;
    }
}
