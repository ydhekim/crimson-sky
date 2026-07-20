package io.github.ydhekim.crimson_sky.server.achievement;

/**
 * A just-unlocked achievement, returned so a caller can see what an evaluation pass produced
 * (system design §22). Deliberately not wired to any client packet in this pass — S3's character page is
 * where unlocks become client-visible.
 */
public record UnlockedAchievement(String keyName, int points) {
}
