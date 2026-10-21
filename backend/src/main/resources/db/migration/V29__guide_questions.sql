-- Questions to a guide (docs/TRD.md §6.23, §7.21.6): a thread per question, asked by a signed-in trekker or by a
-- visitor who proved their email with a code. Replies happen on the site; contact details are never exchanged.

CREATE TABLE question_threads (
    id              UUID PRIMARY KEY,
    guide_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asker_id        UUID REFERENCES users (id) ON DELETE CASCADE,
    visitor_email   TEXT CHECK (length(visitor_email) <= 254),
    asker_name      TEXT        NOT NULL CHECK (length(asker_name) BETWEEN 1 AND 100),
    status          TEXT        NOT NULL CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED')),
    created_at      TIMESTAMPTZ NOT NULL,
    last_message_at TIMESTAMPTZ NOT NULL,
    first_reply_at  TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    closed_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    -- Either an account asked or a visitor with a proven email did, never both.
    CHECK ((asker_id IS NULL) <> (visitor_email IS NULL)),
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CHECK (first_reply_at IS NULL OR first_reply_at >= created_at)
);

CREATE INDEX question_threads_guide_idx ON question_threads (guide_id, last_message_at DESC);
CREATE INDEX question_threads_asker_idx ON question_threads (asker_id, last_message_at DESC);
CREATE INDEX question_threads_visitor_idx ON question_threads (visitor_email, created_at);
CREATE INDEX question_threads_last_message_idx ON question_threads (last_message_at DESC);

CREATE TABLE question_messages (
    id          UUID PRIMARY KEY,
    thread_id   UUID        NOT NULL REFERENCES question_threads (id) ON DELETE CASCADE,
    author_role TEXT        NOT NULL CHECK (author_role IN ('TREKKER', 'GUIDE', 'ADMIN')),
    -- Null for a visitor's message.
    author_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    body        TEXT        NOT NULL CHECK (length(body) BETWEEN 1 AND 2000),
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX question_messages_thread_idx ON question_messages (thread_id, created_at);
