package com.chris64233.cc.foodrecall.repo;

import com.chris64233.cc.foodrecall.domain.Transformation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransformationRepository extends JpaRepository<Transformation, Long> {

    Optional<Transformation> findByTransformationId(String transformationId);
}
