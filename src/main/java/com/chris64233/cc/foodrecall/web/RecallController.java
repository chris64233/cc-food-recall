package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.RecallService;
import com.chris64233.cc.foodrecall.service.Views.MutationResult;
import com.chris64233.cc.foodrecall.service.Views.RecallView;
import com.chris64233.cc.foodrecall.web.dto.RecallRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public RecallController(RecallService recallService) {
        this.recallService = recallService;
    }

    @PostMapping
    public ResponseEntity<RecallView> initiate(@Valid @RequestBody RecallRequest request) {
        MutationResult<RecallView> result = recallService.initiate(
                request.recallNumber(), request.lotNumber(), request.reason());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.view());
    }

    @PostMapping("/{recallNumber}/close")
    public RecallView close(@PathVariable String recallNumber) {
        return recallService.close(recallNumber).view();
    }

    @GetMapping("/{recallNumber}")
    public RecallView detail(@PathVariable String recallNumber) {
        return recallService.detail(recallNumber);
    }
}
