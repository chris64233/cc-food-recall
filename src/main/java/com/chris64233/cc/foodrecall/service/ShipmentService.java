package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.NotificationSource;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallNotification;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import com.chris64233.cc.foodrecall.domain.Shipment;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.LotRepository;
import com.chris64233.cc.foodrecall.repository.RecallEventRepository;
import com.chris64233.cc.foodrecall.repository.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repository.RecallNotificationRepository;
import com.chris64233.cc.foodrecall.repository.ShipmentRepository;
import com.chris64233.cc.foodrecall.web.Dtos.ShipmentRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ShipmentResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 登记批次发货去向。若批次正处于进行中的召回影响清单内，
 * 则为该召回追加新的通知记录（不改写已有通知），并递增统计版本。
 */
@Service
public class ShipmentService {

    private final LotRepository lotRepository;
    private final ShipmentRepository shipmentRepository;
    private final RecallImpactRepository impactRepository;
    private final RecallEventRepository recallRepository;
    private final RecallNotificationRepository notificationRepository;
    private final Transactions transactions;

    @PersistenceContext
    private EntityManager entityManager;

    public ShipmentService(LotRepository lotRepository,
                           ShipmentRepository shipmentRepository,
                           RecallImpactRepository impactRepository,
                           RecallEventRepository recallRepository,
                           RecallNotificationRepository notificationRepository,
                           Transactions transactions) {
        this.lotRepository = lotRepository;
        this.shipmentRepository = shipmentRepository;
        this.impactRepository = impactRepository;
        this.recallRepository = recallRepository;
        this.notificationRepository = notificationRepository;
        this.transactions = transactions;
    }

    public ShipmentResponse ship(ShipmentRequest request) {
        String shipmentKey = LotService.requireText(request == null ? null : request.shipmentKey(),
                "发货编号不能为空");
        String lotNumber = LotService.requireText(request == null ? null : request.lotNumber(),
                "批次号不能为空");
        String holder = LotService.requireText(request == null ? null : request.holder(),
                "持有方不能为空");
        BigDecimal quantity = Quantities.requirePositive(request == null ? null : request.quantity(),
                "发货数量");
        return transactions.idempotent(() -> doShip(shipmentKey, lotNumber, holder, quantity));
    }

    private ShipmentResponse doShip(String shipmentKey, String lotNumber, String holder,
                                    BigDecimal quantity) {
        var existing = shipmentRepository.findByShipmentKey(shipmentKey);
        if (existing.isPresent()) {
            Shipment stored = existing.get();
            if (!stored.getLot().getLotNumber().equals(lotNumber)
                    || !stored.getHolder().equals(holder)
                    || stored.getQuantity().compareTo(quantity) != 0) {
                throw ApiException.conflict("发货编号已存在且内容不一致: " + shipmentKey);
            }
            return toResponse(stored);
        }

        Lot lot = lotRepository.findForUpdateByLotNumber(lotNumber)
                .orElseThrow(() -> ApiException.notFound("批次不存在: " + lotNumber));
        if (lot.getQuantity().compareTo(quantity) < 0) {
            throw ApiException.conflict("批次库存不足: " + lotNumber
                    + " 现有 " + lot.getQuantity() + " 需要 " + quantity);
        }
        lot.setQuantity(lot.getQuantity().subtract(quantity));
        Shipment shipment = shipmentRepository.save(
                new Shipment(shipmentKey, lot, holder, quantity, Instant.now()));

        // 锁顺序与转换传播一致：先批次行锁（上方已持有），再按召回主键顺序加召回行锁
        List<RecallEvent> openRecalls = impactRepository.findByLot(lot).stream()
                .map(RecallImpact::getRecall)
                .filter(recall -> recall.getStatus() == RecallStatus.OPEN)
                .sorted(Comparator.comparing(RecallEvent::getId))
                .toList();
        for (RecallEvent recall : openRecalls) {
            RecallEvent locked = recallRepository.findForUpdateByRecallNumber(recall.getRecallNumber())
                    .orElseThrow(() -> ApiException.notFound("召回事件不存在: " + recall.getRecallNumber()));
            entityManager.refresh(locked);
            if (locked.getStatus() != RecallStatus.OPEN) {
                continue;
            }
            notificationRepository.save(new RecallNotification(locked.nextNotificationNumber(),
                    locked, holder, quantity, NotificationSource.SHIPMENT, Instant.now()));
            locked.bumpStatsVersion();
        }
        return toResponse(shipment);
    }

    private ShipmentResponse toResponse(Shipment shipment) {
        return new ShipmentResponse(shipment.getShipmentKey(), shipment.getLot().getLotNumber(),
                shipment.getHolder(), shipment.getQuantity());
    }
}
