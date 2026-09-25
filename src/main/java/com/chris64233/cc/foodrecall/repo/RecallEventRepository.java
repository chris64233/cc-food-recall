package com.chris64233.cc.foodrecall.repo;

import com.chris64233.cc.foodrecall.domain.RecallEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecallEventRepository extends JpaRepository<RecallEvent, Long> {

    Optional<RecallEvent> findByRecallNumber(String recallNumber);
}
