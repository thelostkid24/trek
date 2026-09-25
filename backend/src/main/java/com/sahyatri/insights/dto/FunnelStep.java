package com.sahyatri.insights.dto;

/** A stage of the new-account funnel: trekkers who signed up in the window and reached this stage since. */
public record FunnelStep(String key, String label, long count) {
}
