package com.sahyatri.question.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.question.dto.AskQuestionRequest;
import com.sahyatri.question.dto.MessageRequest;
import com.sahyatri.question.dto.QuestionThreadDetail;
import com.sahyatri.question.dto.QuestionThreadSummary;
import com.sahyatri.question.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.6. TREKKER only; the asker is the token's subject. */
@RestController
@RequestMapping("/api/trekker")
public class TrekkerQuestionController {

    private final QuestionService questions;

    public TrekkerQuestionController(QuestionService questions) {
        this.questions = questions;
    }

    @PostMapping("/guides/{key}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionThreadDetail ask(@AuthenticationPrincipal Jwt jwt, @PathVariable String key,
                                    @Valid @RequestBody AskQuestionRequest req) {
        return questions.askAsTrekker(userId(jwt), key, req.body());
    }

    @GetMapping("/questions")
    public ItemsResponse<QuestionThreadSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return new ItemsResponse<>(questions.trekkerThreads(userId(jwt)));
    }

    @GetMapping("/questions/{id}")
    public QuestionThreadDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return questions.trekkerThread(userId(jwt), id);
    }

    @PostMapping("/questions/{id}/messages")
    public QuestionThreadDetail reply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @Valid @RequestBody MessageRequest req) {
        return questions.trekkerReply(userId(jwt), id, req.body());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
