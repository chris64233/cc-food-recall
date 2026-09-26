package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.LotDestination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LotDestinationRepository extends JpaRepository<LotDestination, Long> {

    Optional<LotDestination> findByDestinationNumber(String destinationNumber);

    List<LotDestination> findByLotIn(Collection<Lot> lots);
}
