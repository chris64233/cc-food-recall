package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.DownstreamHolder;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.DownstreamHolderRepository;
import com.chris64233.cc.foodrecall.web.Dtos.HolderResponse;
import com.chris64233.cc.foodrecall.web.Dtos.RegisterHolderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class HolderService {

    private final DownstreamHolderRepository holderRepository;
    private final Transactions transactions;

    public HolderService(DownstreamHolderRepository holderRepository, Transactions transactions) {
        this.holderRepository = holderRepository;
        this.transactions = transactions;
    }

    public HolderResponse register(RegisterHolderRequest request) {
        String code = LotService.requireText(request == null ? null : request.holderCode(),
                "持有方编码不能为空");
        String name = LotService.requireText(request == null ? null : request.name(),
                "持有方名称不能为空");
        return transactions.idempotent(() -> doRegister(code, name));
    }

    private HolderResponse doRegister(String code, String name) {
        return holderRepository.findByHolderCode(code)
                .map(existing -> {
                    if (!existing.getName().equals(name)) {
                        throw ApiException.conflict("持有方编码已存在且名称不一致: " + code);
                    }
                    return toResponse(existing);
                })
                .orElseGet(() -> toResponse(
                        holderRepository.save(new DownstreamHolder(code, name, Instant.now()))));
    }

    @Transactional(readOnly = true)
    public HolderResponse getHolder(String code) {
        DownstreamHolder holder = holderRepository.findByHolderCode(code)
                .orElseThrow(() -> ApiException.notFound("持有方不存在: " + code));
        return toResponse(holder);
    }

    private HolderResponse toResponse(DownstreamHolder holder) {
        return new HolderResponse(holder.getHolderCode(), holder.getName());
    }
}
