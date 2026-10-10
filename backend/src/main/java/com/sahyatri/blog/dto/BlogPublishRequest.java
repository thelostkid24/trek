package com.sahyatri.blog.dto;

import jakarta.validation.constraints.NotNull;

public record BlogPublishRequest(@NotNull Boolean published) {
}
