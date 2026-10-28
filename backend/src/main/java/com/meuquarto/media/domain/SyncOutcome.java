package com.meuquarto.media.domain;

/**
 * What one sync run did: how it ended, how many activity entries it applied, and an error code (ours,
 * never text from a provider) when it did not succeed.
 */
public record SyncOutcome(SyncStatus status, int itemsCount, String error) {

    public static SyncOutcome success(int itemsCount) {
        return new SyncOutcome(SyncStatus.SUCCESS, itemsCount, null);
    }

    public static SyncOutcome failed(String errorCode) {
        return new SyncOutcome(SyncStatus.FAILED, 0, errorCode);
    }

    public static SyncOutcome skipped(String reasonCode) {
        return new SyncOutcome(SyncStatus.SKIPPED, 0, reasonCode);
    }
}
