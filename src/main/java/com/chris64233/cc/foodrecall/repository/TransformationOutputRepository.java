package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.TransformationOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TransformationOutputRepository extends JpaRepository<TransformationOutput, Long> {

    @Query("select distinct o.lot from TransformationInput i "
            + "join TransformationOutput o on o.transformation = i.transformation "
            + "where i.lot in :lots")
    List<Lot> findChildLotsOf(@Param("lots") Collection<Lot> lots);
}
