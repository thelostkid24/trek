package com.sahyatri.guide.dto;

import java.util.UUID;

/** A trek the guide has led or is down to lead, so they can write a note about it. */
public record RouteOption(UUID trackId, String name) {
}
