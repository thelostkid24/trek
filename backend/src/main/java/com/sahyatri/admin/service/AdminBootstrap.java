package com.sahyatri.admin.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.config.AdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Promotes the accounts listed in ADMIN_EMAILS to ADMIN at startup. The account must already exist. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties props;
    private final UserRepository users;
    private final AuditLog audit;

    public AdminBootstrap(AdminProperties props, UserRepository users, AuditLog audit) {
        this.props = props;
        this.users = users;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.emails().isEmpty()) {
            return;
        }
        List<User> listed = users.findByEmailIn(props.emails());
        for (User user : listed) {
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                audit.record(null, "USER_PROMOTED", "USER", user.getId(), Map.of("role", Role.ADMIN.name()));
                log.info("Promoted {} to ADMIN (ADMIN_EMAILS)", user.getEmail());
            }
        }
        if (listed.size() < props.emails().size()) {
            log.warn("ADMIN_EMAILS lists {} address(es) with no account yet; sign up, then restart",
                    props.emails().size() - listed.size());
        }
    }
}
