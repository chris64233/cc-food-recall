package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.LotService;
import com.chris64233.cc.foodrecall.web.Dtos.GenealogyResponse;
import com.chris64233.cc.foodrecall.web.Dtos.LotResponse;
import com.chris64233.cc.foodrecall.web.Dtos.RegisterLotRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lots")
public class LotController {

    private final LotService lotService;

    public LotController(LotService lotService) {
        this.lotService = lotService;
    }

    @PostMapping
    public LotResponse register(@RequestBody RegisterLotRequest request) {
        return lotService.register(request);
    }

    @GetMapping("/{lotNumber}")
    public LotResponse getLot(@PathVariable String lotNumber) {
        return lotService.getLot(lotNumber);
    }

    @GetMapping("/{lotNumber}/genealogy")
    public GenealogyResponse getGenealogy(@PathVariable String lotNumber) {
        return lotService.getGenealogy(lotNumber);
    }
}
