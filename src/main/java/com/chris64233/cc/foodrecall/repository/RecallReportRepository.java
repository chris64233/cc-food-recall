package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecallReportRepository extends JpaRepository<RecallReport, Long> {

    Optional<RecallReport> findByReportNumber(String reportNumber);

    List<RecallReport> findByRecall(RecallEvent recall);

    List<RecallReport> findByRecallAndHolder(RecallEvent recall, String holder);
}
