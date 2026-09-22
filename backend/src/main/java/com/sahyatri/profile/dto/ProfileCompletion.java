package com.sahyatri.profile.dto;

import java.util.List;

/** @param missing keys of unfilled items, in the order documented in docs/TRD.md §7.3 */
public record ProfileCompletion(int percent, List<String> missing) {
}
