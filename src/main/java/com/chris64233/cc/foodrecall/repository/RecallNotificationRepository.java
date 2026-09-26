package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.DownstreamHolder;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecallNotificationRepository extends JpaRepository<RecallNotification, Long> {

    Optional<RecallNotification> findByNotificationNumber(String notificationNumber);

    List<RecallNotification> findByRecall(RecallEvent recall);
}
