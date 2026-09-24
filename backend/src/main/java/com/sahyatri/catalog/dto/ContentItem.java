package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.TrekContentItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One list line. {@code title} is the question for FAQs and the heading for cards; {@code badge} is cards only. */
public record ContentItem(
        @Size(max = 12) String badge,
        @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String body) {

    public static ContentItem of(TrekContentItem i) {
        return new ContentItem(i.getBadge(), i.getTitle(), i.getBody());
    }
}
