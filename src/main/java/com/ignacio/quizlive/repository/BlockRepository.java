package com.ignacio.quizlive.repository;

import com.ignacio.quizlive.model.Block;
import com.ignacio.quizlive.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {

    List<Block> findByOwner(User owner);
}