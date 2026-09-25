package com.chris64233.cc.foodrecall.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RecallRequest(
        @NotBlank String recallNumber,
        @NotBlank String lotNumber,
        @NotBlank String reason) {
}
