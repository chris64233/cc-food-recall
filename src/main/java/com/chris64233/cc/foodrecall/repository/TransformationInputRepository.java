package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.TransformationInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TransformationInputRepository extends JpaRepository<TransformationInput, Long> {

    @Query("select distinct i.lot from TransformationOutput o "
            + "join TransformationInput i on i.transformation = o.transformation "
            + "where o.lot in :lots")
    List<Lot> findParentLotsOf(@Param("lots") Collection<Lot> lots);
}
