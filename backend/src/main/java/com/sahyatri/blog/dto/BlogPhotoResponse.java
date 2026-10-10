package com.sahyatri.blog.dto;

import java.util.UUID;

public record BlogPhotoResponse(UUID id, String url, String caption) {
}
