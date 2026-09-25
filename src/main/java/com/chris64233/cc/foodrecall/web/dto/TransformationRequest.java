package com.chris64233.cc.foodrecall.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record TransformationRequest(
        @NotBlank String transformationId,
        @NotEmpty List<@Valid LotAmount> inputs,
        @NotEmpty List<@Valid LotAmount> outputs,
        @PositiveOrZero @Digits(integer = 15, fraction = 3) BigDecimal loss) {
}
