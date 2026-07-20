package io.github.ydhekim.crimson_sky.server.achievement;

import java.time.Instant;

/**
 * The single fact an account-scoped criterion reads today (system design §22) — the account's creation
 * instant, which {@code ACCOUNT_CREATED_BEFORE} compares against a cutoff date.
 */
public record AccountAchievementFacts(Instant accountCreatedAt) {
}
