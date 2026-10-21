package com.sahyatri.question.dto;

import java.util.UUID;

/** A visitor's question went out. They read the thread later through the link we email them. */
public record QuestionAsked(UUID id) {
}
