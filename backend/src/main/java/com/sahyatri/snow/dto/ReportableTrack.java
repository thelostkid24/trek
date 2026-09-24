package com.sahyatri.snow.dto;

import java.util.UUID;

/** A trek the signed-in guide can file reports for. */
public record ReportableTrack(UUID id, String slug, String name) {
}
