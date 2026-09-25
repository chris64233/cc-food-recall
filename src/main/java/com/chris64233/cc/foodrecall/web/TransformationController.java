package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.TransformationService;
import com.chris64233.cc.foodrecall.service.Views.MutationResult;
import com.chris64233.cc.foodrecall.service.Views.TransformationView;
import com.chris64233.cc.foodrecall.web.dto.TransformationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transformations")
public class TransformationController {

    private final TransformationService transformationService;

    public TransformationController(TransformationService transformationService) {
        this.transformationService = transformationService;
    }

    @PostMapping
    public ResponseEntity<TransformationView> transform(@Valid @RequestBody TransformationRequest request) {
        MutationResult<TransformationView> result = transformationService.transform(
                request.transformationId(),
                request.inputs().stream()
                        .map(i -> new TransformationService.Item(i.lotNumber(), i.quantity()))
                        .toList(),
                request.outputs().stream()
                        .map(o -> new TransformationService.Item(o.lotNumber(), o.quantity()))
                        .toList(),
                request.loss());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.view());
    }
}
