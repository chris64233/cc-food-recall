package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.DownstreamHolder;
import com.chris64233.cc.foodrecall.domain.DownstreamReport;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DownstreamReportRepository extends JpaRepository<DownstreamReport, Long> {

    Optional<DownstreamReport> findByReportNumber(String reportNumber);

    List<DownstreamReport> findByRecall(RecallEvent recall);
}
