package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.RecallQueryService;
import com.chris64233.cc.foodrecall.service.RecallReportService;
import com.chris64233.cc.foodrecall.service.RecallService;
import com.chris64233.cc.foodrecall.web.Dtos.CloseRequest;
import com.chris64233.cc.foodrecall.web.Dtos.DiscrepanciesResponse;
import com.chris64233.cc.foodrecall.web.Dtos.EffectivenessResponse;
import com.chris64233.cc.foodrecall.web.Dtos.HolderResponsesView;
import com.chris64233.cc.foodrecall.web.Dtos.ImpactsResponse;
import com.chris64233.cc.foodrecall.web.Dtos.NotificationsResponse;
import com.chris64233.cc.foodrecall.web.Dtos.RecallRequest;
import com.chris64233.cc.foodrecall.web.Dtos.RecallResponse;
import com.chris64233.cc.foodrecall.web.Dtos.ReportRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ReportResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recalls")
public class RecallController {

    private final RecallService recallService;
    private final RecallReportService reportService;
    private final RecallQueryService queryService;

    public RecallController(RecallService recallService,
                            RecallReportService reportService,
                            RecallQueryService queryService) {
        this.recallService = recallService;
        this.reportService = reportService;
        this.queryService = queryService;
    }

    @PostMapping
    public RecallResponse initiate(@RequestBody RecallRequest request) {
        return recallService.initiate(request);
    }

    @PostMapping("/{recallNumber}/reports")
    public ReportResponse report(@PathVariable String recallNumber,
                                 @RequestBody ReportRequest request) {
        return reportService.report(recallNumber, request);
    }

    @PostMapping("/{recallNumber}/close")
    public RecallResponse close(@PathVariable String recallNumber,
                                @RequestBody CloseRequest request) {
        return recallService.close(recallNumber, request);
    }

    @GetMapping("/{recallNumber}/impacts")
    public ImpactsResponse impacts(@PathVariable String recallNumber) {
        return queryService.impacts(recallNumber);
    }

    @GetMapping("/{recallNumber}/notifications")
    public NotificationsResponse notifications(@PathVariable String recallNumber) {
        return queryService.notifications(recallNumber);
    }

    @GetMapping("/{recallNumber}/holders")
    public HolderResponsesView holders(@PathVariable String recallNumber) {
        return queryService.holderResponses(recallNumber);
    }

    @GetMapping("/{recallNumber}/discrepancies")
    public DiscrepanciesResponse discrepancies(@PathVariable String recallNumber) {
        return queryService.discrepancies(recallNumber);
    }

    @GetMapping("/{recallNumber}/effectiveness")
    public EffectivenessResponse effectiveness(@PathVariable String recallNumber) {
        return queryService.effectiveness(recallNumber);
    }
}
