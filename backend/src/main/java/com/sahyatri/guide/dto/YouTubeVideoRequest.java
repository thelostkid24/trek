package com.sahyatri.guide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A YouTube link the guide pasted (watch, youtu.be, Shorts, embed or live), with an optional caption. */
public record YouTubeVideoRequest(@NotBlank @Size(max = 500) String url, @Size(max = 120) String caption) {
}
