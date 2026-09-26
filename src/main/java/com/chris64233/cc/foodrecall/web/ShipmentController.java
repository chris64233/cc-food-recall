package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.ShipmentService;
import com.chris64233.cc.foodrecall.web.Dtos.ShipmentRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ShipmentResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    public ShipmentResponse ship(@RequestBody ShipmentRequest request) {
        return shipmentService.ship(request);
    }
}
