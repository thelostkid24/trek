package com.sahyatri.question.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.booking.dto.EmailCodeResponse;
import com.sahyatri.booking.service.BookingEmailCodeService;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.guide.entity.GuideDetails;
import com.sahyatri.guide.repository.GuideDetailsRepository;
import com.sahyatri.guide.service.GuideSlugService;
import com.sahyatri.question.dto.QuestionMessageView;
import com.sahyatri.question.dto.QuestionThreadDetail;
import com.sahyatri.question.dto.QuestionThreadSummary;
import com.sahyatri.question.dto.ReplyTime;
import com.sahyatri.question.dto.VisitorQuestionRequest;
import com.sahyatri.question.entity.AuthorRole;
import com.sahyatri.question.entity.QuestionMessage;
import com.sahyatri.question.entity.QuestionThread;
import com.sahyatri.question.entity.ThreadStatus;
import com.sahyatri.question.notify.QuestionNotice;
import com.sahyatri.question.repository.QuestionMessageRepository;
import com.sahyatri.question.repository.QuestionThreadRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Questions to a guide (docs/TRD.md §7.21.6). Who may read or write a thread is decided here: the asker (their
 * account, or a visitor holding a signed link), the guide it was asked of, and admins. A thread that isn't yours is
 * a 404, so ids can't be probed.
 */
@Service
public class QuestionService {

    /** New threads per asker (account or email) per day. */
    static final int MAX_THREADS_PER_DAY = 5;
    static final Duration REPLY_TIME_WINDOW = Duration.ofDays(90);
    /** Fewer threads than this in the window and the reply time stays hidden. */
    static final int REPLY_TIME_MIN_THREADS = 5;
    /** An unanswered thread younger than this doesn't count towards the reply time yet. */
    static final Duration UNANSWERED_GRACE = Duration.ofHours(24);
    static final int ADMIN_LIST_LIMIT = 200;
    private static final int PREVIEW_LENGTH = 140;
    private static final String TEAM = "The Empty Valley";

    private final QuestionThreadRepository threads;
    private final QuestionMessageRepository messages;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final GuideSlugService slugs;
    private final GuideDetailsRepository details;
    private final BookingEmailCodeService emailCodes;
    private final QuestionLinks links;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    public QuestionService(QuestionThreadRepository threads, QuestionMessageRepository messages, UserRepository users,
                           CurrentUser currentUser, GuideSlugService slugs, GuideDetailsRepository details,
                           BookingEmailCodeService emailCodes, QuestionLinks links, AuditLog audit,
                           ApplicationEventPublisher events, JdbcTemplate jdbc) {
        this.threads = threads;
        this.messages = messages;
        this.users = users;
        this.currentUser = currentUser;
        this.slugs = slugs;
        this.details = details;
        this.emailCodes = emailCodes;
        this.links = links;
        this.audit = audit;
        this.events = events;
        this.jdbc = jdbc;
    }

    // ---- Asking ----

    public EmailCodeResponse sendVisitorCode(String email) {
        return emailCodes.send(email, BookingEmailCodeService.Purpose.QUESTION);
    }

    @Transactional
    public QuestionThreadDetail askAsTrekker(UUID userId, String guideKey, String body) {
        User asker = currentUser.require(userId);
        User guide = guide(guideKey);
        if (threads.countByAskerIdAndCreatedAtAfter(userId, dayAgo()) >= MAX_THREADS_PER_DAY) {
            throw tooMany();
        }
        String name = asker.getFullName() == null || asker.getFullName().isBlank() ? "Trekker" : asker.getFullName().trim();
        QuestionThread thread = threads.save(QuestionThread.byAccount(guide.getId(), userId, truncate(name, 100)));
        QuestionMessage message = add(thread, AuthorRole.TREKKER, userId, body);
        events.publishEvent(new QuestionNotice(thread.getId(), message.getId(), QuestionNotice.Kind.ASKED));
        return detail(thread, false);
    }

    /** The code is checked in its own transaction, so a wrong one counts even though the question fails. */
    @Transactional
    public UUID askAsVisitor(String guideKey, VisitorQuestionRequest req) {
        User guide = guide(guideKey);
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        emailCodes.check(email, req.emailCode());
        if (threads.countByVisitorEmailAndCreatedAtAfter(email, dayAgo()) >= MAX_THREADS_PER_DAY) {
            throw tooMany();
        }
        QuestionThread thread = threads.save(QuestionThread.byVisitor(guide.getId(), email, req.name().trim()));
        QuestionMessage message = add(thread, AuthorRole.TREKKER, null, req.body());
        events.publishEvent(new QuestionNotice(thread.getId(), message.getId(), QuestionNotice.Kind.ASKED));
        return thread.getId();
    }

    // ---- The asker ----

    @Transactional(readOnly = true)
    public List<QuestionThreadSummary> trekkerThreads(UUID userId) {
        return summaries(threads.findByAskerIdOrderByLastMessageAtDesc(userId), false);
    }

    @Transactional(readOnly = true)
    public QuestionThreadDetail trekkerThread(UUID userId, UUID threadId) {
        return detail(asked(userId, threadId), false);
    }

    @Transactional
    public QuestionThreadDetail trekkerReply(UUID userId, UUID threadId, String body) {
        currentUser.require(userId);
        return askerReply(asked(userId, threadId), userId, body);
    }

    @Transactional(readOnly = true)
    public QuestionThreadDetail visitorThread(String token) {
        return detail(visitors(token), false);
    }

    @Transactional
    public QuestionThreadDetail visitorReply(String token, String body) {
        return askerReply(visitors(token), null, body);
    }

    private QuestionThreadDetail askerReply(QuestionThread thread, UUID authorId, String body) {
        open(thread);
        QuestionMessage message = add(thread, AuthorRole.TREKKER, authorId, body);
        events.publishEvent(new QuestionNotice(thread.getId(), message.getId(), QuestionNotice.Kind.ASKER_REPLIED));
        return detail(thread, false);
    }

    // ---- The guide ----

    /** Waiting on the guide first, then the rest; newest activity first within each. */
    @Transactional(readOnly = true)
    public List<QuestionThreadSummary> guideThreads(UUID guideId) {
        List<QuestionThread> list = threads.findByGuideIdOrderByLastMessageAtDesc(guideId).stream()
                .sorted(Comparator.comparing((QuestionThread t) -> t.getStatus() != ThreadStatus.OPEN))
                .toList();
        return summaries(list, false);
    }

    @Transactional(readOnly = true)
    public QuestionThreadDetail guideThread(UUID guideId, UUID threadId) {
        return detail(guides(guideId, threadId), false);
    }

    @Transactional
    public QuestionThreadDetail guideReply(UUID guideId, UUID threadId, String body) {
        currentUser.require(guideId);
        QuestionThread thread = guides(guideId, threadId);
        open(thread);
        QuestionMessage message = add(thread, AuthorRole.GUIDE, guideId, body);
        events.publishEvent(new QuestionNotice(thread.getId(), message.getId(), QuestionNotice.Kind.GUIDE_REPLIED));
        return detail(thread, false);
    }

    // ---- Admins ----

    @Transactional(readOnly = true)
    public List<QuestionThreadSummary> adminThreads(ThreadStatus status) {
        Limit limit = Limit.of(ADMIN_LIST_LIMIT);
        return summaries(status == null ? threads.findAllByOrderByLastMessageAtDesc(limit)
                : threads.findByStatusOrderByLastMessageAtDesc(status, limit), true);
    }

    @Transactional(readOnly = true)
    public QuestionThreadDetail adminThread(UUID threadId) {
        return detail(find(threadId), true);
    }

    @Transactional
    public QuestionThreadDetail close(UUID adminId, UUID threadId, String note) {
        QuestionThread thread = find(threadId);
        open(thread);
        boolean withNote = note != null && !note.isBlank();
        if (withNote) {
            add(thread, AuthorRole.ADMIN, adminId, note);
        }
        thread.close(adminId, Instant.now());
        threads.save(thread);
        audit.record(adminId, "QUESTION_THREAD_CLOSED", "QUESTION_THREAD", threadId,
                Map.of("guide_id", thread.getGuideId(), "with_note", withNote));
        return detail(thread, true);
    }

    // ---- Reply time (law 8: worked out on every read) ----

    /**
     * Median minutes from a question to the guide's first reply, over threads asked in the last 90 days. A thread
     * still unanswered after a day counts with the time it has waited so far, so ignoring questions doesn't flatter
     * the number; one an admin closed without a reply doesn't count. Hidden below five threads.
     */
    @Transactional(readOnly = true)
    public ReplyTime replyTime(String guideKey) {
        UUID guideId = guide(guideKey).getId();
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT count(*) AS n,
                       percentile_cont(0.5) WITHIN GROUP (
                           ORDER BY extract(EPOCH FROM COALESCE(first_reply_at, now()) - created_at)) AS median
                FROM question_threads
                WHERE guide_id = ?
                  AND created_at >= now() - make_interval(days => ?)
                  AND (first_reply_at IS NOT NULL
                       OR (status <> 'CLOSED' AND created_at < now() - make_interval(hours => ?)))""",
                guideId, (int) REPLY_TIME_WINDOW.toDays(), (int) UNANSWERED_GRACE.toHours());
        long n = ((Number) row.get("n")).longValue();
        if (n < REPLY_TIME_MIN_THREADS || row.get("median") == null) {
            return new ReplyTime(null);
        }
        return new ReplyTime(Math.round(((Number) row.get("median")).doubleValue() / 60));
    }

    // ---- Helpers ----

    private User guide(String key) {
        return slugs.resolve(key)
                .flatMap(users::findById)
                .filter(u -> u.getRole() == Role.GUIDE && !u.isDisabled())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GUIDE_NOT_FOUND", "Guide not found"));
    }

    private QuestionThread find(UUID threadId) {
        return threads.findById(threadId).orElseThrow(QuestionService::notFound);
    }

    private QuestionThread asked(UUID userId, UUID threadId) {
        QuestionThread t = find(threadId);
        if (!userId.equals(t.getAskerId())) {
            throw notFound();
        }
        return t;
    }

    private QuestionThread guides(UUID guideId, UUID threadId) {
        QuestionThread t = find(threadId);
        if (!guideId.equals(t.getGuideId())) {
            throw notFound();
        }
        return t;
    }

    /** Signed links are only ever issued for visitor threads; an account's thread is read in the account. */
    private QuestionThread visitors(String token) {
        QuestionThread t = find(links.verify(token));
        if (!t.isVisitor()) {
            throw notFound();
        }
        return t;
    }

    private static void open(QuestionThread thread) {
        if (thread.isClosed()) {
            throw ApiException.conflict("QUESTION_CLOSED", "This conversation was closed");
        }
    }

    private QuestionMessage add(QuestionThread thread, AuthorRole role, UUID authorId, String body) {
        String text = ContactRedactor.redact(body.strip());
        QuestionMessage message = messages.save(new QuestionMessage(thread.getId(), role, authorId,
                truncate(text, 2000), Instant.now()));
        switch (role) {
            case TREKKER -> thread.askerWrote(message.getCreatedAt());
            case GUIDE -> thread.guideWrote(message.getCreatedAt());
            case ADMIN -> { }
        }
        threads.save(thread);
        return message;
    }

    private List<QuestionThreadSummary> summaries(List<QuestionThread> list, boolean admin) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> guides = guideUsers(list.stream().map(QuestionThread::getGuideId).collect(Collectors.toSet()));
        Map<UUID, String> guideSlugs = guideSlugs(guides.keySet());
        Map<UUID, QuestionMessage> last = new HashMap<>();
        for (QuestionMessage m : messages.findByThreadIdInOrderByCreatedAtAsc(list.stream().map(QuestionThread::getId).toList())) {
            last.put(m.getThreadId(), m);
        }
        return list.stream().map(t -> {
            QuestionMessage m = last.get(t.getId());
            User g = guides.get(t.getGuideId());
            return new QuestionThreadSummary(t.getId(), t.getStatus(), t.getGuideId(), g == null ? null : g.getFullName(),
                    guideSlugs.get(t.getGuideId()), t.getAskerName(), admin ? askerEmail(t) : null, t.isVisitor(),
                    t.getCreatedAt(), t.getLastMessageAt(), m == null ? null : m.getAuthorRole(),
                    m == null ? "" : truncate(m.getBody(), PREVIEW_LENGTH));
        }).toList();
    }

    private QuestionThreadDetail detail(QuestionThread t, boolean admin) {
        User guide = users.findById(t.getGuideId()).orElse(null);
        String guideName = guide == null ? null : guide.getFullName();
        List<QuestionMessageView> views = messages.findByThreadIdOrderByCreatedAtAsc(t.getId()).stream()
                .map(m -> new QuestionMessageView(m.getId(), m.getAuthorRole(), switch (m.getAuthorRole()) {
                    case TREKKER -> t.getAskerName();
                    case GUIDE -> guideName;
                    case ADMIN -> TEAM;
                }, m.getBody(), m.getCreatedAt()))
                .toList();
        return new QuestionThreadDetail(t.getId(), t.getStatus(), t.getGuideId(), guideName,
                guideSlugs(Set.of(t.getGuideId())).get(t.getGuideId()), t.getAskerName(),
                admin ? askerEmail(t) : null, t.isVisitor(), t.getCreatedAt(), t.getFirstReplyAt(), t.getClosedAt(),
                !t.isClosed(), views);
    }

    private String askerEmail(QuestionThread t) {
        return t.isVisitor() ? t.getVisitorEmail()
                : users.findById(t.getAskerId()).map(User::getEmail).orElse(null);
    }

    private Map<UUID, User> guideUsers(Set<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<UUID, String> guideSlugs(Set<UUID> ids) {
        Map<UUID, String> out = new LinkedHashMap<>();
        for (GuideDetails d : details.findAllById(ids)) {
            out.put(d.getUserId(), d.getSlug());
        }
        return out;
    }

    private static Instant dayAgo() {
        return Instant.now().minus(Duration.ofDays(1));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Question not found");
    }

    private static ApiException tooMany() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "QUESTION_RATE_LIMITED",
                "You've asked a lot of questions today. Please try again tomorrow.");
    }
}
