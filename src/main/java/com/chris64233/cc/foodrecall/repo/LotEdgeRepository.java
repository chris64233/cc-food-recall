package com.chris64233.cc.foodrecall.repo;

import com.chris64233.cc.foodrecall.domain.LotEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LotEdgeRepository extends JpaRepository<LotEdge, Long> {

    @Query("select e.child.lotNumber from LotEdge e where e.parent.lotNumber = :lotNumber")
    List<String> findChildLotNumbers(@Param("lotNumber") String lotNumber);

    @Query("select e.parent.lotNumber from LotEdge e where e.child.lotNumber = :lotNumber")
    List<String> findParentLotNumbers(@Param("lotNumber") String lotNumber);
}
