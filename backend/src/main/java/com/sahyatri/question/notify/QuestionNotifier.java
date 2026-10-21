package com.sahyatri.question.notify;

import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.common.config.AppProperties;
import com.sahyatri.common.mail.MailTransport;
import com.sahyatri.question.entity.QuestionMessage;
import com.sahyatri.question.entity.QuestionThread;
import com.sahyatri.question.repository.QuestionMessageRepository;
import com.sahyatri.question.repository.QuestionThreadRepository;
import com.sahyatri.question.service.QuestionLinks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails about questions (§7.21.6). Mail goes from our address with no Reply-To, and never names the other side's
 * email or phone: replies happen on the site. Runs after commit, so a mail failure is logged and never loses a message.
 */
@Component
public class QuestionNotifier {

    private static final Logger log = LoggerFactory.getLogger(QuestionNotifier.class);
    private static final String SIGN = "\n\n— The Empty Valley team";

    private final QuestionThreadRepository threads;
    private final QuestionMessageRepository messages;
    private final UserRepository users;
    private final QuestionLinks links;
    private final MailTransport mail;
    private final String frontendBaseUrl;

    public QuestionNotifier(QuestionThreadRepository threads, QuestionMessageRepository messages, UserRepository users,
                            QuestionLinks links, MailTransport mail, AppProperties props) {
        this.threads = threads;
        this.messages = messages;
        this.users = users;
        this.links = links;
        this.mail = mail;
        this.frontendBaseUrl = props.frontendBaseUrl();
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onNotice(QuestionNotice notice) {
        try {
            QuestionThread thread = threads.findById(notice.threadId()).orElseThrow();
            QuestionMessage message = messages.findById(notice.messageId()).orElseThrow();
            User guide = users.findById(thread.getGuideId()).orElseThrow();
            switch (notice.kind()) {
                case ASKED -> {
                    toGuide(guide, thread, "New question from " + firstName(thread.getAskerName()),
                            firstName(thread.getAskerName()) + " asked you a question on The Empty Valley:", message);
                    if (thread.isVisitor()) {
                        mail.send(thread.getVisitorEmail(), "Your question to " + firstName(guide.getFullName()),
                                "Hi " + firstName(thread.getAskerName()) + ",\n\n"
                                        + "We've passed your question to " + firstName(guide.getFullName())
                                        + ". We'll email you when they reply.\n\n" + quote(message.getBody())
                                        + "\n\nSee the conversation or add to it: " + visitorLink(thread)
                                        + "\nThe link works for 30 days; every email we send about it has a fresh one."
                                        + SIGN);
                    }
                }
                case ASKER_REPLIED -> toGuide(guide, thread, firstName(thread.getAskerName()) + " replied to you",
                        firstName(thread.getAskerName()) + " replied on The Empty Valley:", message);
                case GUIDE_REPLIED -> toAsker(guide, thread, message);
            }
        } catch (RuntimeException e) {
            log.error("Could not email {} notice for question thread {}", notice.kind(), notice.threadId(), e);
        }
    }

    private void toGuide(User guide, QuestionThread thread, String subject, String lead, QuestionMessage message) {
        if (guide.getEmail() == null) {
            return;
        }
        mail.send(guide.getEmail(), subject, "Hi " + firstName(guide.getFullName()) + ",\n\n" + lead + "\n\n"
                + quote(message.getBody())
                + "\n\nReply here: " + frontendBaseUrl + "/guide/questions/" + thread.getId()
                + "\nReplies happen on the site. Replying to this email doesn't reach them, and contact details "
                + "aren't shared either way." + SIGN);
    }

    private void toAsker(User guide, QuestionThread thread, QuestionMessage message) {
        String to;
        String link;
        if (thread.isVisitor()) {
            to = thread.getVisitorEmail();
            link = visitorLink(thread) + "\nThe link works for 30 days.";
        } else {
            to = users.findById(thread.getAskerId()).filter(u -> !u.isDisabled()).map(User::getEmail).orElse(null);
            link = frontendBaseUrl + "/account/questions/" + thread.getId();
        }
        if (to == null) {
            return;
        }
        String guideName = firstName(guide.getFullName());
        mail.send(to, guideName + " replied to your question", "Hi " + firstName(thread.getAskerName()) + ",\n\n"
                + guideName + " replied on The Empty Valley:\n\n" + quote(message.getBody())
                + "\n\nReply or ask more: " + link
                + "\nReplying to this email doesn't reach " + guideName + "." + SIGN);
    }

    private String visitorLink(QuestionThread thread) {
        return frontendBaseUrl + "/questions/" + links.issue(thread.getId());
    }

    private static String quote(String body) {
        return "> " + body.strip().replace("\n", "\n> ");
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        return fullName.trim().split("\\s+")[0];
    }
}
