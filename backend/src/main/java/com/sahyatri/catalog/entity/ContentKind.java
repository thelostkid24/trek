package com.sahyatri.catalog.entity;

/** The lists on a trek page. Each is shared (every trek) and/or per trek. */
public enum ContentKind {
    /** What the fee covers. */
    INCLUDED,
    /** What it doesn't. */
    NOT_INCLUDED,
    /** Safety checklist lines. */
    SAFETY,
    /** The dark box, e.g. "Who decides to turn back:" + answer. */
    SAFETY_CALLOUT,
    /** The line under the safety section, e.g. "Take insurance." */
    SAFETY_NOTE,
    /** Question in {@code title}, answer in {@code body}. */
    FAQ,
    /** "Why choose us" cards: badge ("1%"), title and body. */
    WHY_US;

    /** Kinds whose items need a title. */
    public boolean needsTitle() {
        return this == FAQ || this == WHY_US || this == SAFETY_CALLOUT;
    }

    public boolean allowsBadge() {
        return this == WHY_US;
    }
}
