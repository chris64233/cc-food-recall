package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.DestinationService;
import com.chris64233.cc.foodrecall.web.Dtos.DestinationResponse;
import com.chris64233.cc.foodrecall.web.Dtos.RegisterDestinationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/destinations")
public class DestinationController {

    private final DestinationService destinationService;

    public DestinationController(DestinationService destinationService) {
        this.destinationService = destinationService;
    }

    /** 登记批次发往下游持有方的去向（发货单号幂等）。 */
    @PostMapping
    public DestinationResponse register(@RequestBody RegisterDestinationRequest request) {
        return destinationService.register(request);
    }
}
