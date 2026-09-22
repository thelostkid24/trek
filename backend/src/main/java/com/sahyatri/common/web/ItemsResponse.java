package com.sahyatri.common.web;

import java.util.List;

/** List responses are wrapped so they can grow paging fields later without breaking clients. */
public record ItemsResponse<T>(List<T> items) {
}
