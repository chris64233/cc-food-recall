package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.DownstreamHolder;
import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.LotDestination;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.DownstreamHolderRepository;
import com.chris64233.cc.foodrecall.repository.LotDestinationRepository;
import com.chris64233.cc.foodrecall.repository.LotRepository;
import com.chris64233.cc.foodrecall.repository.RecallImpactRepository;
import com.chris64233.cc.foodrecall.web.Dtos.DestinationResponse;
import com.chris64233.cc.foodrecall.web.Dtos.RegisterDestinationRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 去向登记：批次从工厂发往下游持有方。受进行中召回影响（已隔离）的批次禁止发货。
 */
@Service
public class DestinationService {

    private final LotRepository lotRepository;
    private final DownstreamHolderRepository holderRepository;
    private final LotDestinationRepository destinationRepository;
    private final RecallImpactRepository impactRepository;
    private final Transactions transactions;

    public DestinationService(LotRepository lotRepository,
                              DownstreamHolderRepository holderRepository,
                              LotDestinationRepository destinationRepository,
                              RecallImpactRepository impactRepository,
                              Transactions transactions) {
        this.lotRepository = lotRepository;
        this.holderRepository = holderRepository;
        this.destinationRepository = destinationRepository;
        this.impactRepository = impactRepository;
        this.transactions = transactions;
    }

    public DestinationResponse register(RegisterDestinationRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        String destinationNumber = LotService.requireText(request.destinationNumber(), "发货单号不能为空");
        String lotNumber = LotService.requireText(request.lotNumber(), "批次号不能为空");
        String holderCode = LotService.requireText(request.holderCode(), "持有方编码不能为空");
        BigDecimal quantity = Quantities.requirePositive(request.quantity(), "发货数量");
        return transactions.idempotent(() -> doRegister(destinationNumber, lotNumber, holderCode, quantity));
    }

    private DestinationResponse doRegister(String destinationNumber, String lotNumber,
                                           String holderCode, BigDecimal quantity) {
        var existing = destinationRepository.findByDestinationNumber(destinationNumber);
        if (existing.isPresent()) {
            LotDestination stored = existing.get();
            if (!stored.getLot().getLotNumber().equals(lotNumber)
                    || !stored.getHolder().getHolderCode().equals(holderCode)
                    || stored.getQuantity().compareTo(quantity) != 0) {
                throw ApiException.conflict("发货单号已存在且内容不一致: " + destinationNumber);
            }
            return toResponse(stored);
        }

        Lot lot = lotRepository.findForUpdateByLotNumber(lotNumber)
                .orElseThrow(() -> ApiException.notFound("批次不存在: " + lotNumber));
        DownstreamHolder holder = holderRepository.findByHolderCode(holderCode)
                .orElseThrow(() -> ApiException.notFound("持有方不存在: " + holderCode));
        if (impactRepository.existsOpenByLot(lot, RecallStatus.OPEN)) {
            throw ApiException.conflict("批次已被进行中的召回隔离，禁止发货: " + lotNumber);
        }
        if (lot.getQuantity().compareTo(quantity) < 0) {
            throw ApiException.conflict("批次库存不足: " + lotNumber
                    + " 现有 " + lot.getQuantity() + " 需要 " + quantity);
        }
        lot.setQuantity(lot.getQuantity().subtract(quantity));
        LotDestination destination =
                new LotDestination(destinationNumber, lot, holder, quantity, Instant.now());
        return toResponse(destinationRepository.save(destination));
    }

    private DestinationResponse toResponse(LotDestination destination) {
        return new DestinationResponse(destination.getDestinationNumber(),
                destination.getLot().getLotNumber(), destination.getHolder().getHolderCode(),
                destination.getQuantity());
    }
}
