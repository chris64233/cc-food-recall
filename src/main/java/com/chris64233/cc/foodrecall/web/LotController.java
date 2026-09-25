package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.LotService;
import com.chris64233.cc.foodrecall.service.Views.LineageView;
import com.chris64233.cc.foodrecall.service.Views.LotStockView;
import com.chris64233.cc.foodrecall.service.Views.LotView;
import com.chris64233.cc.foodrecall.service.Views.MutationResult;
import com.chris64233.cc.foodrecall.service.Views.RecallReasonView;
import com.chris64233.cc.foodrecall.web.dto.RegisterLotRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lots")
public class LotController {

    private final LotService lotService;

    public LotController(LotService lotService) {
        this.lotService = lotService;
    }

    @PostMapping
    public ResponseEntity<LotView> register(@Valid @RequestBody RegisterLotRequest request) {
        MutationResult<LotView> result = lotService.register(request.lotNumber(), request.quantity());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.view());
    }

    @GetMapping("/{lotNumber}")
    public LotStockView stock(@PathVariable String lotNumber) {
        return lotService.stock(lotNumber);
    }

    @GetMapping("/{lotNumber}/recalls")
    public List<RecallReasonView> recalls(@PathVariable String lotNumber) {
        return lotService.recallReasons(lotNumber);
    }

    @GetMapping("/{lotNumber}/lineage/upstream")
    public LineageView upstream(@PathVariable String lotNumber) {
        return lotService.lineage(lotNumber, "upstream");
    }

    @GetMapping("/{lotNumber}/lineage/downstream")
    public LineageView downstream(@PathVariable String lotNumber) {
        return lotService.lineage(lotNumber, "downstream");
    }
}
