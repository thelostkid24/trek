package com.sahyatri.blog.dto;

import jakarta.validation.constraints.Size;

public record BlogPhotoRequest(@Size(max = 200) String caption) {
}
