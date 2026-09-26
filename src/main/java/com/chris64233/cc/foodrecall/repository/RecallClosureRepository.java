package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.RecallClosure;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecallClosureRepository extends JpaRepository<RecallClosure, Long> {

    Optional<RecallClosure> findByClosureNumber(String closureNumber);

    Optional<RecallClosure> findByRecall(RecallEvent recall);
}
