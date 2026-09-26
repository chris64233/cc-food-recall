package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.RecallService;
import com.chris64233.cc.foodrecall.web.Dtos.CloseRecallRequest;
import com.chris64233.cc.foodrecall.web.Dtos.DiscrepancyView;
import com.chris64233.cc.foodrecall.web.Dtos.EffectivenessResponse;
import com.chris64233.cc.foodrecall.web.Dtos.HolderResponseView;
import com.chris64233.cc.foodrecall.web.Dtos.ImpactView;
import com.chris64233.cc.foodrecall.web.Dtos.NotificationView;
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

import java.util.List;

@RestController
@RequestMapping("/api/recalls")
public class RecallController {

    private final RecallService recallService;

    public RecallController(RecallService recallService) {
        this.recallService = recallService;
    }

    /** 发起召回：生成不可变初始影响清单并为每个下游持有方创建通知任务。 */
    @PostMapping
    public RecallResponse initiate(@RequestBody RecallRequest request) {
        return recallService.initiate(request);
    }

    /** 关闭召回：校验响应门槛，记录尚未收回数量和批准人。 */
    @PostMapping("/{recallNumber}/close")
    public RecallResponse close(@PathVariable String recallNumber,
                                @RequestBody(required = false) CloseRecallRequest request) {
        return recallService.close(recallNumber, request);
    }

    /** 下游提交响应报告（已隔离/已消费/已转交/数量不符），报告号幂等。 */
    @PostMapping("/{recallNumber}/reports")
    public ReportResponse report(@PathVariable String recallNumber,
                                 @RequestBody ReportRequest request) {
        return recallService.report(recallNumber, request);
    }

    @GetMapping("/{recallNumber}")
    public RecallResponse getRecall(@PathVariable String recallNumber) {
        return recallService.getRecall(recallNumber);
    }

    /** 影响批次查询（含初始清单与后续追加批次）。 */
    @GetMapping("/{recallNumber}/impacts")
    public List<ImpactView> impacts(@PathVariable String recallNumber) {
        return recallService.getImpacts(recallNumber);
    }

    /** 通知任务查询。 */
    @GetMapping("/{recallNumber}/notifications")
    public List<NotificationView> notifications(@PathVariable String recallNumber) {
        return recallService.getNotifications(recallNumber);
    }

    /** 持有方响应查询。 */
    @GetMapping("/{recallNumber}/holder-responses")
    public List<HolderResponseView> holderResponses(@PathVariable String recallNumber) {
        return recallService.getHolderResponses(recallNumber);
    }

    /** 数量差异查询。 */
    @GetMapping("/{recallNumber}/discrepancies")
    public List<DiscrepancyView> discrepancies(@PathVariable String recallNumber) {
        return recallService.getDiscrepancies(recallNumber);
    }

    /** 召回完成率（有效性）查询。 */
    @GetMapping("/{recallNumber}/effectiveness")
    public EffectivenessResponse effectiveness(@PathVariable String recallNumber) {
        return recallService.getEffectiveness(recallNumber);
    }
}
