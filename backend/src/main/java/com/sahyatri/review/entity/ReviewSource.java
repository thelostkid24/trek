package com.sahyatri.review.entity;

/**
 * Where a review comes from (§7.21.5). BOOKING: a confirmed booking on a completed departure ("Verified trek").
 * OWN_TREKKER: someone who trekked with the guide off-platform and proved an email or phone ("Group verified"); never
 * counted in any rating, count or bar.
 */
public enum ReviewSource {
    BOOKING, OWN_TREKKER
}
