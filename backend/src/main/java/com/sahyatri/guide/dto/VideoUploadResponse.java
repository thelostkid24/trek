package com.sahyatri.guide.dto;

import java.util.Map;
import java.util.UUID;

/** PUT the file to {@code upload_url} with {@code headers}, then POST .../complete. */
public record VideoUploadResponse(UUID id, String uploadUrl, Map<String, String> headers) {
}
