package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecallImpactRepository extends JpaRepository<RecallImpact, Long> {

    List<RecallImpact> findByRecall(RecallEvent recall);

    List<RecallImpact> findByLot(Lot lot);

    List<RecallImpact> findByLotIn(Collection<Lot> lots);

    Optional<RecallImpact> findByRecallAndLot(RecallEvent recall, Lot lot);

    long countByRecall(RecallEvent recall);

    @Query("select i from RecallImpact i where i.lot = :lot and i.recall.status = :status")
    List<RecallImpact> findOpenByLot(@Param("lot") Lot lot,
                                     @Param("status") RecallStatus status);

    @Query("select count(i) > 0 from RecallImpact i where i.lot = :lot and i.recall.status = :status")
    boolean existsOpenByLot(@Param("lot") Lot lot,
                            @Param("status") RecallStatus status);
}
