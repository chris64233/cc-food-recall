package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.NotificationSource;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallNotification;
import com.chris64233.cc.foodrecall.domain.RecallReport;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import com.chris64233.cc.foodrecall.domain.ReportType;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.RecallEventRepository;
import com.chris64233.cc.foodrecall.repository.RecallNotificationRepository;
import com.chris64233.cc.foodrecall.repository.RecallReportRepository;
import com.chris64233.cc.foodrecall.web.Dtos.ReportRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ReportResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 下游持有方处置报告。报告数量与持有方接收量守恒：
 * 同一持有方在同一召回下所有报告数量之和不得超过其接收总量
 * （接收总量 = 该召回下其全部通知记录数量之和）。
 */
@Service
public class RecallReportService {

    private final RecallEventRepository recallRepository;
    private final RecallNotificationRepository notificationRepository;
    private final RecallReportRepository reportRepository;
    private final Transactions transactions;

    public RecallReportService(RecallEventRepository recallRepository,
                               RecallNotificationRepository notificationRepository,
                               RecallReportRepository reportRepository,
                               Transactions transactions) {
        this.recallRepository = recallRepository;
        this.notificationRepository = notificationRepository;
        this.reportRepository = reportRepository;
        this.transactions = transactions;
    }

    public ReportResponse report(String recallNumber, ReportRequest request) {
        String recall = LotService.requireText(recallNumber, "召回事件号不能为空");
        Validated validated = validate(request);
        return transactions.idempotent(() -> doReport(recall, validated));
    }

    private ReportResponse doReport(String recallNumber, Validated validated) {
        var existing = reportRepository.findByReportNumber(validated.reportNumber());
        if (existing.isPresent()) {
            RecallReport stored = existing.get();
            if (!stored.getRecall().getRecallNumber().equals(recallNumber)
                    || !stored.getHolder().equals(validated.holder())
                    || stored.getType() != validated.type()
                    || stored.getQuantity().compareTo(validated.quantity()) != 0
                    || !java.util.Objects.equals(stored.getTransferredTo(), validated.transferredTo())) {
                throw ApiException.conflict("报告号已存在且内容不一致: " + validated.reportNumber());
            }
            return toResponse(stored, null);
        }

        RecallEvent recall = recallRepository.findForUpdateByRecallNumber(recallNumber)
                .orElseThrow(() -> ApiException.notFound("召回事件不存在: " + recallNumber));
        if (recall.getStatus() != RecallStatus.OPEN) {
            throw ApiException.conflict("召回已关闭，不再接受报告: " + recallNumber);
        }

        List<RecallNotification> notifications =
                notificationRepository.findByRecallAndHolder(recall, validated.holder());
        BigDecimal received = notifications.stream()
                .map(RecallNotification::getQuantity)
                .reduce(Quantities.ZERO, BigDecimal::add);
        if (received.signum() == 0) {
            throw ApiException.conflict("持有方不在召回通知清单中: " + validated.holder());
        }
        BigDecimal reported = reportRepository.findByRecallAndHolder(recall, validated.holder())
                .stream()
                .map(RecallReport::getQuantity)
                .reduce(Quantities.ZERO, BigDecimal::add);
        if (reported.add(validated.quantity()).compareTo(received) > 0) {
            throw ApiException.conflict("报告数量不守恒: 持有方 " + validated.holder()
                    + " 接收 " + received + " 已报告 " + reported
                    + " 本次 " + validated.quantity());
        }

        RecallReport report = reportRepository.save(new RecallReport(validated.reportNumber(),
                recall, validated.holder(), validated.type(), validated.quantity(),
                validated.transferredTo(), Instant.now()));

        // 转交产生新发现的去向：为接收方追加通知记录，不改写原通知
        String appendedNotification = null;
        if (validated.type() == ReportType.TRANSFERRED) {
            RecallNotification appended = notificationRepository.save(new RecallNotification(
                    recall.nextNotificationNumber(), recall, validated.transferredTo(),
                    validated.quantity(), NotificationSource.TRANSFER, Instant.now()));
            appendedNotification = appended.getNotificationNumber();
        }
        recall.bumpStatsVersion();
        return toResponse(report, appendedNotification);
    }

    private Validated validate(ReportRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        String reportNumber = LotService.requireText(request.reportNumber(), "报告号不能为空");
        String holder = LotService.requireText(request.holder(), "持有方不能为空");
        ReportType type;
        try {
            type = ReportType.valueOf(
                    LotService.requireText(request.type(), "报告类型不能为空")
                            .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("报告类型无效: " + request.type()
                    + " 可选值: ISOLATED, CONSUMED, TRANSFERRED, MISMATCH");
        }
        BigDecimal quantity = Quantities.requirePositive(request.quantity(), "报告数量");
        String transferredTo = request.transferredTo() == null || request.transferredTo().isBlank()
                ? null : request.transferredTo().trim();
        if (type == ReportType.TRANSFERRED) {
            if (transferredTo == null) {
                throw ApiException.badRequest("转交报告必须填写接收方");
            }
            if (transferredTo.equals(holder)) {
                throw ApiException.badRequest("转交接收方不能与报告持有方相同");
            }
        } else if (transferredTo != null) {
            throw ApiException.badRequest("仅转交报告需要填写接收方");
        }
        return new Validated(reportNumber, holder, type, quantity, transferredTo);
    }

    private ReportResponse toResponse(RecallReport report, String appendedNotification) {
        return new ReportResponse(report.getReportNumber(), report.getRecall().getRecallNumber(),
                report.getHolder(), report.getType().name(), report.getQuantity(),
                report.getTransferredTo(), appendedNotification);
    }

    private record Validated(String reportNumber, String holder, ReportType type,
                             BigDecimal quantity, String transferredTo) {
    }
}
