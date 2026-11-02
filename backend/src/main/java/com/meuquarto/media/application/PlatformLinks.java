package com.meuquarto.media.application;

import com.meuquarto.media.domain.LinkedAccount;
import com.meuquarto.media.infra.LinkedAccountRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The linked platforms of a profile as the public sees them (FR-12a, ADR 0023): the platform and the
 * link to the profile there, in the fixed platform order. The URL is built from the stored username and
 * a fixed template, so the public page never links to anything a user typed. Read only, local data.
 */
@Service
public class PlatformLinks {

    private final LinkedAccountRepository accounts;

    public PlatformLinks(LinkedAccountRepository accounts) {
        this.accounts = accounts;
    }

    /** {@code platform} is the platform id (such as {@code LETTERBOXD}), so callers need nothing from the domain. */
    public record PlatformLink(String platform, String url) {}

    @Transactional(readOnly = true)
    public List<PlatformLink> forProfile(long profileId) {
        return accounts.findByProfileIdOrderByCreatedAtAscIdAsc(profileId).stream()
                .sorted(Comparator.comparing(LinkedAccount::provider))
                .flatMap(account -> account.profileUrl()
                        .map(url -> new PlatformLink(account.provider().name(), url)).stream())
                .toList();
    }
}
