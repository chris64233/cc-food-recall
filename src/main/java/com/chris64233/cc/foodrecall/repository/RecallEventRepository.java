package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.RecallEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecallEventRepository extends JpaRepository<RecallEvent, Long> {

    Optional<RecallEvent> findByRecallNumber(String recallNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecallEvent r where r.recallNumber = :recallNumber")
    Optional<RecallEvent> findForUpdateByRecallNumber(@Param("recallNumber") String recallNumber);
}
