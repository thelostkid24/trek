package com.sahyatri.question.dto;

import jakarta.validation.constraints.Size;

/** Admin close. A non-blank {@code note} is added to the thread as a message from The Empty Valley. */
public record CloseThreadRequest(@Size(max = 2000) String note) {
}
