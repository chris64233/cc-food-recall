package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RecallImpactRepository extends JpaRepository<RecallImpact, Long> {

    List<RecallImpact> findByRecall(RecallEvent recall);

    List<RecallImpact> findByLot(Lot lot);

    List<RecallImpact> findByLotIn(Collection<Lot> lots);

    List<RecallImpact> findByLotAndRecall_Status(Lot lot, RecallStatus status);

    boolean existsByLotAndRecall_Status(Lot lot, RecallStatus status);

    long countByRecall(RecallEvent recall);
}
