package com.meuquarto.billing.application;

import com.meuquarto.billing.infra.EntitlementStore;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a user owns (BR-05: purchases are permanent). Other modules ask this service; they never read the
 * billing tables. Rows appear when a verified payment grants them (#38); until then nobody owns anything.
 */
@Service
public class Entitlements {

    private final EntitlementStore store;

    public Entitlements(EntitlementStore store) {
        this.store = store;
    }

    /** Ids of the premium themes the user bought, e.g. {@code locadora}; empty when none. */
    @Transactional(readOnly = true)
    public Set<String> ownedThemeIds(long userId) {
        return store.themeIdsOwnedBy(userId);
    }
}
