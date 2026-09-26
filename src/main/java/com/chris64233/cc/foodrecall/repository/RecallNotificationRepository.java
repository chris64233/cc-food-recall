package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecallNotificationRepository extends JpaRepository<RecallNotification, Long> {

    List<RecallNotification> findByRecallOrderByIdAsc(RecallEvent recall);

    List<RecallNotification> findByRecallAndHolder(RecallEvent recall, String holder);
}
