package com.sahyatri.account.dto;

/** Confirms deleting the account. {@code password} is required, and checked, only when the account has one. */
public record AccountDeleteRequest(String password) {
}
