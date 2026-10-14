package com.meuquarto.identity.infra;

import java.util.Map;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Ends a user's sessions stored by Spring Session (after a password change or account deletion). */
@Component
public class UserSessions {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public UserSessions(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** Ends every session of the user except {@code keepSessionId} (may be null to end all). */
    public void endAllExcept(String email, String keepSessionId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(email);
        found.keySet().stream()
                .filter(id -> !id.equals(keepSessionId))
                .forEach(sessions::deleteById);
    }
}
