package com.sahyatri.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlogFaq(@NotBlank @Size(max = 200) String question, @NotBlank @Size(max = 2000) String answer) {
}
