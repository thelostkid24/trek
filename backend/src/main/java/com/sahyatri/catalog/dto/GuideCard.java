package com.sahyatri.catalog.dto;

import java.util.UUID;

/** A departure's guide as the trek and departure pages show it: "Sankri · led Kedarkantha 34 times". */
public record GuideCard(UUID id, String fullName, String avatarUrl, String homeCity, long ledThisTrek) {
}
