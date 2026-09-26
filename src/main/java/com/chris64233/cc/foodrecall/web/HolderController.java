package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.HolderService;
import com.chris64233.cc.foodrecall.web.Dtos.HolderResponse;
import com.chris64233.cc.foodrecall.web.Dtos.RegisterHolderRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/holders")
public class HolderController {

    private final HolderService holderService;

    public HolderController(HolderService holderService) {
        this.holderService = holderService;
    }

    @PostMapping
    public HolderResponse register(@RequestBody RegisterHolderRequest request) {
        return holderService.register(request);
    }

    @GetMapping("/{holderCode}")
    public HolderResponse getHolder(@PathVariable String holderCode) {
        return holderService.getHolder(holderCode);
    }
}
