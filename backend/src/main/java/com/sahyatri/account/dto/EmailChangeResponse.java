package com.sahyatri.account.dto;

/** @param expiresIn seconds the emailed link stays valid */
public record EmailChangeResponse(long expiresIn) {
}
