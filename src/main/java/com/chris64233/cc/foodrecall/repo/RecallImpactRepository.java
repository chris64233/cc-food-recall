package com.chris64233.cc.foodrecall.repo;

import com.chris64233.cc.foodrecall.domain.RecallImpact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecallImpactRepository extends JpaRepository<RecallImpact, Long> {

    @Query("select i from RecallImpact i join fetch i.recall join fetch i.lot where i.lot.lotNumber = :lotNumber")
    List<RecallImpact> findByLotNumber(@Param("lotNumber") String lotNumber);

    @Query("select i from RecallImpact i join fetch i.recall join fetch i.lot where i.lot.lotNumber in :lotNumbers")
    List<RecallImpact> findByLotNumberIn(@Param("lotNumbers") Collection<String> lotNumbers);

    @Query("select i from RecallImpact i join fetch i.recall join fetch i.lot where i.recall.recallNumber = :recallNumber")
    List<RecallImpact> findByRecallNumber(@Param("recallNumber") String recallNumber);
}
