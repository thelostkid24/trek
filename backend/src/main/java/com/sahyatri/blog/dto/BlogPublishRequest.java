package com.sahyatri.blog.dto;

import jakarta.validation.constraints.NotNull;

/** Publishing needs {@code moneyRuleConfirmed}: the editor confirms the post publishes none of our money. */
public record BlogPublishRequest(@NotNull Boolean published, Boolean moneyRuleConfirmed) {
}
