package com.sahyatri.question.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.question.dto.CloseThreadRequest;
import com.sahyatri.question.dto.QuestionThreadDetail;
import com.sahyatri.question.dto.QuestionThreadSummary;
import com.sahyatri.question.entity.ThreadStatus;
import com.sahyatri.question.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.6. ADMIN only: read every thread, close one (audit-logged). */
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

    private final QuestionService questions;

    public AdminQuestionController(QuestionService questions) {
        this.questions = questions;
    }

    @GetMapping
    public ItemsResponse<QuestionThreadSummary> list(@RequestParam(required = false) ThreadStatus status) {
        return new ItemsResponse<>(questions.adminThreads(status));
    }

    @GetMapping("/{id}")
    public QuestionThreadDetail get(@PathVariable UUID id) {
        return questions.adminThread(id);
    }

    @PostMapping("/{id}/close")
    public QuestionThreadDetail close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @Valid @RequestBody(required = false) CloseThreadRequest req) {
        return questions.close(UUID.fromString(jwt.getSubject()), id, req == null ? null : req.note());
    }
}
