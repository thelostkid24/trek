package com.sahyatri.question.notify;

import java.util.UUID;

/** Published inside the transaction that added the message; emailed only after it commits. */
public record QuestionNotice(UUID threadId, UUID messageId, Kind kind) {

    public enum Kind {
        /** A new thread: alert the guide, and give a visitor their link. */
        ASKED,
        /** The asker wrote again: alert the guide. */
        ASKER_REPLIED,
        /** The guide wrote: send it to the asker. */
        GUIDE_REPLIED
    }
}
