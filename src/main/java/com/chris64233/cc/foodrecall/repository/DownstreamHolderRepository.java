package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.DownstreamHolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DownstreamHolderRepository extends JpaRepository<DownstreamHolder, Long> {

    Optional<DownstreamHolder> findByHolderCode(String holderCode);

    List<DownstreamHolder> findByHolderCodeIn(Collection<String> holderCodes);
}
