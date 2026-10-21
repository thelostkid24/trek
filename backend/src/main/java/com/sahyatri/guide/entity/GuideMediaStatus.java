package com.sahyatri.guide.entity;

/** A video is PENDING from the upload URL until the guide confirms the file arrived; photos are READY at once. */
public enum GuideMediaStatus {
    PENDING,
    READY
}
