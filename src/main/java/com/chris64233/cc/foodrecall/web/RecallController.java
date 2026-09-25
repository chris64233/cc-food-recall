package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.RecallService;
import com.chris64233.cc.foodrecall.web.Dtos.RecallRequest;
import com.chris64233.cc.foodrecall.web.Dtos.RecallResponse;
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
    public RecallResponse initiate(@RequestBody RecallRequest request) {
        return recallService.initiate(request);
    }

    @PostMapping("/{recallNumber}/close")
    public RecallResponse close(@PathVariable String recallNumber) {
        return recallService.close(recallNumber);
    }
}
