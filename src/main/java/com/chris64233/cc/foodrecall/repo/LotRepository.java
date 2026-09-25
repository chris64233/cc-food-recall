package com.chris64233.cc.foodrecall.repo;

import com.chris64233.cc.foodrecall.domain.Lot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    Optional<Lot> findByLotNumber(String lotNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.lotNumber = :lotNumber")
    Optional<Lot> findByLotNumberForUpdate(@Param("lotNumber") String lotNumber);
}
