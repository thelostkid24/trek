package com.sahyatri.question.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.question.dto.MessageRequest;
import com.sahyatri.question.dto.QuestionThreadDetail;
import com.sahyatri.question.dto.QuestionThreadSummary;
import com.sahyatri.question.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.6. GUIDE only; the guide is the token's subject. */
@RestController
@RequestMapping("/api/guide/questions")
public class GuideQuestionController {

    private final QuestionService questions;

    public GuideQuestionController(QuestionService questions) {
        this.questions = questions;
    }

    @GetMapping
    public ItemsResponse<QuestionThreadSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return new ItemsResponse<>(questions.guideThreads(userId(jwt)));
    }

    @GetMapping("/{id}")
    public QuestionThreadDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return questions.guideThread(userId(jwt), id);
    }

    @PostMapping("/{id}/messages")
    public QuestionThreadDetail reply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @Valid @RequestBody MessageRequest req) {
        return questions.guideReply(userId(jwt), id, req.body());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
