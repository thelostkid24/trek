package com.sahyatri.review.dto;

import com.sahyatri.auth.dto.ValidationPatterns;
import com.sahyatri.review.entity.ReviewTag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A review from the guide's own trekker (§7.21.5): exactly one of email or phone, with the code sent to it.
 * Submitting again from the same email or phone rewrites the review.
 */
public record OwnTrekkerReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Min(1) @Max(5) Integer safetyRating, @Min(1) @Max(5) Integer routeRating,
        @Min(1) @Max(5) Integer communicationRating, @Min(1) @Max(5) Integer careRating,
        @Size(max = 6) List<@NotNull ReviewTag> tags,
        @Size(max = 2000) String body,
        @NotBlank @Size(max = 60) String name,
        @Size(max = 100) String trekName,
        @Email @Size(max = 254) String email,
        @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,
        @NotBlank @Pattern(regexp = ValidationPatterns.OTP_CODE, message = "must be 6 digits") String code) {
}
