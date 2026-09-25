package com.chris64233.cc.foodrecall.repository;

import com.chris64233.cc.foodrecall.domain.Lot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    Optional<Lot> findByLotNumber(String lotNumber);

    List<Lot> findByLotNumberIn(Collection<String> lotNumbers);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.lotNumber = :lotNumber")
    Optional<Lot> findForUpdateByLotNumber(@Param("lotNumber") String lotNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.lotNumber in :lotNumbers order by l.lotNumber")
    List<Lot> findForUpdateByLotNumberIn(@Param("lotNumbers") Collection<String> lotNumbers);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.id in :ids order by l.id")
    List<Lot> findForUpdateByIdIn(@Param("ids") Collection<Long> ids);
}
