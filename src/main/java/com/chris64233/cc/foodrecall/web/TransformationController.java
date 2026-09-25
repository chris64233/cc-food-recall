package com.chris64233.cc.foodrecall.web;

import com.chris64233.cc.foodrecall.service.TransformationService;
import com.chris64233.cc.foodrecall.web.Dtos.TransformRequest;
import com.chris64233.cc.foodrecall.web.Dtos.TransformResponse;
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
    public TransformResponse transform(@RequestBody TransformRequest request) {
        return transformationService.transform(request);
    }
}
