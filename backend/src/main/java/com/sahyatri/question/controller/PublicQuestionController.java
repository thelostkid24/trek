package com.sahyatri.question.controller;

import com.sahyatri.booking.dto.EmailCodeResponse;
import com.sahyatri.question.dto.MessageRequest;
import com.sahyatri.question.dto.QuestionAsked;
import com.sahyatri.question.dto.QuestionEmailCodeRequest;
import com.sahyatri.question.dto.QuestionThreadDetail;
import com.sahyatri.question.dto.ReplyTime;
import com.sahyatri.question.dto.VisitorQuestionRequest;
import com.sahyatri.question.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract: docs/TRD.md §7.21.6. Public: a visitor asks with a proven email and comes back through the signed link
 * in our emails ({@code token}); anyone reads a guide's reply time. Signed-in trekkers ask under /api/trekker.
 */
@RestController
@RequestMapping("/api/public")
public class PublicQuestionController {

    private final QuestionService questions;

    public PublicQuestionController(QuestionService questions) {
        this.questions = questions;
    }

    @PostMapping("/questions/email-code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailCodeResponse emailCode(@Valid @RequestBody QuestionEmailCodeRequest req) {
        return questions.sendVisitorCode(req.email());
    }

    @PostMapping("/guides/{key}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionAsked ask(@PathVariable String key, @Valid @RequestBody VisitorQuestionRequest req) {
        return new QuestionAsked(questions.askAsVisitor(key, req));
    }

    @GetMapping("/guides/{key}/reply-time")
    public ReplyTime replyTime(@PathVariable String key) {
        return questions.replyTime(key);
    }

    @GetMapping("/questions/{token}")
    public QuestionThreadDetail thread(@PathVariable String token) {
        return questions.visitorThread(token);
    }

    @PostMapping("/questions/{token}/messages")
    public QuestionThreadDetail reply(@PathVariable String token, @Valid @RequestBody MessageRequest req) {
        return questions.visitorReply(token, req.body());
    }
}
