package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.ReportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportItemRepository extends JpaRepository<ReportItem, Long> {

    @Query("select i from ReportItem i where i.report.recall = :recall")
    List<ReportItem> findByRecall(@Param("recall") com.chris64233.cc.foodrecall.domain.RecallEvent recall);
}
