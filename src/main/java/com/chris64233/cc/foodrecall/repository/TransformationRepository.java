package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.Transformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransformationRepository extends JpaRepository<Transformation, Long> {

    Optional<Transformation> findByTransformationKey(String transformationKey);

    @Query("select distinct t from Transformation t join t.inputs i where i.lot in :lots")
    List<Transformation> findByInputLotIds(@Param("lots") Collection<Lot> lots);
}
