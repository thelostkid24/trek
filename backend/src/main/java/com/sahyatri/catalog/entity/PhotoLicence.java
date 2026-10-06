package com.sahyatri.catalog.entity;

/** The terms we use a trek photo on. CC BY and CC BY-SA require the credit to be shown with the photo. */
public enum PhotoLicence {
    /** Taken by our guides or team. */
    OURS,
    /** Someone else's, used with their permission. */
    WITH_PERMISSION,
    CC_BY,
    CC_BY_SA,
    CC0,
    UNSPLASH
}
