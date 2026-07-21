# Backend architecture review — 2026-07-21

Requested ahead of M4: a deep, code-grounded pass over the server/common backend (not the ECS/client), covering design, documentation, exception handling, logging, i18n, cross-cutting concerns (AOP), database/scalability, testing, and security. Every finding below is cited against the actual file as it exists on `main` today (post Epic T merge, commit `5cc50a3`) — nothing here is inferred from filenames or memory.

This is a review, not a refactor — per this project's own scope, implementation happens in the separate Claude Code session. Where a fix is warranted, this doc says what and why; a follow-up implementation prompt (mirroring every other epic's `NN-implementation-prompt-*.md`) is the natural next artifact if you want to act on any of it.

## How to read this

Findings are tagged:
- **Bug** — behaves wrong today, independent of any future work. Worth fixing regardless of M4.
- **Gap** — a real, concrete absence (index, test, content) with a specific cost, not a stylistic preference.
- **Tradeoff, already deliberate** — something that looks like a gap in isolation but was already consciously decided elsewhere in this project's own docs, with a documented reason. Flagged so it isn't "rediscovered" and re-litigated.
- **Process suggestion** — nothing wrong with any single line of code, but a pattern repeated ~30 times by hand is a process/tooling opportunity.

## 1. You did not decide anything fundamentally wrong

Worth saying plainly before the findings: the architecture is sound. Manual constructor-injection via `ServiceRegistry`, the Strategy-pattern `RequestHandler`/`PacketHandlerRegistry` split, the raw-`Jdbi`-vs-`onDemand`-DAO distinction (used correctly and consistently — every multi-table atomic write goes through a shared `Handle`, every simple read goes through an onDemand proxy), and the "compute live, never store" rule for quests/ladder/achievements are all still the right calls at this project's current scale, and every one of them is already documented with its own reasoning in `01-system-design-combat-engine.md`. The inline documentation throughout `server/` is genuinely better than most production codebases this size — nearly every non-trivial method explains *why*, not just *what*, including deliberate simplifications and their accepted tradeoffs (e.g., `CharacterService.allocateStatPoints`'s TOCTOU note, `CharacterDao.addExperience`'s "doesn't cascade into a level recompute" note). None of that needs revisiting.

The findings below are real, but they're the kind that show up in *any* codebase built by iterating epic-by-epic over two weeks, not signs of a wrong foundation.

## 2. Bugs — worth fixing regardless of M4

### 2.1 Two handlers dereference `connection.account` without the auth check every other handler has

`server/network/handler/AchievementListRequestHandler.java:22` calls `connection.account.id()` with no preceding `if (connection.account == null) return;` guard — the one guard present in 19 of the other 20 handlers (`LocalizationRequestHandler` and `LoginRequestHandler` are correctly exempt; they're the pre-auth handlers). `SaveAccountSettingsRequestHandler.java:21` has the identical omission. Today, an unauthenticated connection sending either packet throws an NPE inside `KryoPacketRouter.route()`'s try/catch (`KryoPacketRouter.java:63-67`), which logs it and swallows it — so the server doesn't crash, but the client gets **no response at all** (a silent hang) instead of the clean "log and drop" every other unauthenticated request gets. Small, but it's inconsistent behavior an attacker or a buggy client can trigger, and it's exactly the class of one-line omission that's easy to keep reintroducing (see §5's AOP note).

Same file has a second, unrelated bug: `AchievementListRequestHandler.java:12` constructs its logger as `new Logger("LocalizationRequestHandler", ...)` — a copy-paste artifact from whichever handler it was scaffolded from. Cosmetic (only affects log line prefixes), but worth a one-line fix alongside the auth check.

### 2.2 `battle_history.opponent_character_id`'s `ON DELETE CASCADE` deletes the *other* player's row

`server/src/main/resources/db/migration/V8__Battle_History.sql:16`:
```sql
opponent_character_id INTEGER REFERENCES characters (id) ON DELETE CASCADE, -- NULL when opponent_is_bot
```
`battle_history` rows are owned by `character_id` (the attacker) — but the FK on `opponent_character_id` also cascades. If a real opponent later deletes their own character, every `battle_history` row where they were the *opponent* is deleted too — even though those rows are part of a completely different, still-existing character's history. Given this project's own repeated "compute live, never store" principle (§19/§20/§21/§22 — quests, ladder rank, `TOTAL_WINS`/`WIN_STREAK`, achievement unlocks are *all* derived live from this table), that other character's win count, achievement eligibility, and ranked standing can silently regress the moment an unrelated player deletes a character. This is a live-data-integrity bug, not a style nitpick, and it gets more likely to actually bite once ranked play and `DeleteCharacterRequestHandler` both see real traffic. The fix is a one-line migration (`ON DELETE SET NULL` instead of `CASCADE` on that column, treating a deleted opponent the same as a bot opponent — which the existing nullable-column design already anticipates).

### 2.3 Raw identity tokens land in server logs on any login exception

`server/service/UserService.java:66`: `log.error("Exception occurred during login for platform " + platformType + " with token " + identityToken, e);`. Today `identityToken` is just a `PlatformType.TEST` placeholder string with no real security value, so this is harmless *right now*. But this line will still exist verbatim once Steam/Apple/Google auth actually lands (Epic G, per `00-project-plan.md` §5.1) unless someone remembers to come back and remove it — and at that point it's a real OAuth token being written to disk in cleartext on every login failure. Worth fixing now, while it costs nothing, rather than relying on someone noticing it during the Steam auth implementation pass.

## 3. Gaps — concrete, with a specific cost

### 3.1 No index on the single most-queried column in the schema

Grepping every migration file for `CREATE INDEX`/`CREATE UNIQUE INDEX` turns up exactly two results — both the S1-S2 achievement partial indexes (`V15__Redesign_Achievements_And_Character_Statistics.sql:33-34`). **`battle_history.character_id` has no index at all.** Postgres does not automatically index the referencing side of a foreign key (only the referenced primary key gets one) — so every one of the following, all added across this project's last ten epics, is a full sequential scan once `battle_history` grows past a trivial row count: `countTotalWins`, `findRecentOutcomes`, `findFastestWinTurnCount`, `countWins`, `countBattlesSince`, `getRankedEloAsOf`, `findRecentMatches`, plus `CharacterDao.countRankedCharactersAboveEloAsOf`'s correlated subquery over the same table. This is the table every quest check, every achievement check, every ranked-ladder rank computation, and every character-page load reads from — it is the single highest-traffic table in the whole schema, and it has zero indexing beyond the implicit primary key. `characters.account_id` (read by `getCharactersByAccountId`, `getCharacterCount`) is in the same position, though at far lower row-count risk (one row per character, not one row per battle). A migration adding `CREATE INDEX battle_history_character_id_idx ON battle_history (character_id, created_at DESC)` (the composite covers both the plain `character_id` filters and the `ORDER BY created_at DESC` queries in one index) would be cheap now and expensive to retrofit later once there's production data and the table is large enough that adding an index means downtime or a `CONCURRENTLY` build.

### 3.2 Handler-level test coverage is thin, and it's exactly where the two auth-check bugs hid

30 test files exist, and service-layer coverage is genuinely strong (every service touched by an epic this session has a real `TestDatabase`-backed test class). But only 3 of 21 `RequestHandler` implementations have a dedicated handler-level test (`AllocateStatPointsRequestHandlerTest`, `SaveLoadoutRequestHandlerTest`, `AttackRequestHandlerTest`) — none of them the two with the missing auth check. A handler test doesn't need a real database (a handler's whole job is auth-check → delegate → serialize response), so this is cheap coverage to add: one shared "an unauthenticated connection gets no response, and the service is never called" test, parameterized or copy-pasted across all 21, would have caught §2.1 mechanically rather than requiring a manual line-by-line read.

### 3.3 i18n mechanism exists and is architecturally sound, but content coverage is one entry out of fifty-plus

`core/util/LanguageManager.java:35-38` has a real, working `get(MessageCode code)` method — the intended design is that `MessageCode.name()` doubles as a localization key, looked up against the bundle the client already pulls via `LocalizationRequest`/`LocalizationService`. That's a genuinely good reuse of existing infrastructure rather than building a parallel error-message system. The problem: `server/src/main/resources/db/migration/V2__Localization_Setup.sql` seeds exactly **one** `MessageCode`-derived key (`CHAR_NAME_TAKEN`, with real `tr_TR`/`en_US` text) — and no migration since has added another, despite the `MessageCode` enum growing to roughly 50 values across every epic from C through T. Call `LanguageManager.get(MessageCode.STAT_POINTS_INSUFFICIENT)` today and a player sees the literal string `"!STAT_POINTS_INSUFFICIENT!"` (the method's own not-found fallback), not a translated message. On top of that, the *only* place in the entire client that reads a handler response's `message()` field at all is `ConnectionScreen.java:178`, and it routes straight to `Gdx.app.log(...)` — a debug console line, never through `LanguageManager.get(...)`, never shown to the player. So today this is a latent gap rather than a visible bug (M4 hasn't built the UI that would surface these messages yet), but it's worth deciding *now*, before M4 wires up dozens of error-dialog call sites, whether the convention going forward is "every new `MessageCode` gets a localization row in the same migration that adds it" — otherwise this same gap just gets 50 values wider by the time M4 needs it. A cheap process fix: a unit test that asserts `MessageCode.values()` and the seeded localization keys are the same set (or at least that every non-`SUCCESS` failure code has one), so a missing translation fails CI instead of failing silently in front of a player.

### 3.4 No structured/leveled server logging

All 38 files across `server/` that log anything use `com.badlogic.gdx.utils.Logger` — LibGDX's client-side logging utility, constructed with a hardcoded `Logger.DEBUG` level in every single call site (`new Logger("ServiceName", Logger.DEBUG)`). For a headless server this means: no way to raise the log level in production without a code change and redeploy (there's no equivalent of an env-var-driven level), no structured fields (connection id, account id, request id are all string-concatenated into the message rather than queryable), and no straightforward path to a log aggregator (ELK/Loki/CloudWatch) without a custom appender bridge — LibGDX's `Logger` writes straight to stdout via `Application.log`. This is fine for solo local development and was clearly never the focus of any epic so far, which is reasonable — but it's worth flagging explicitly under "scalability," since "how do you find the one failed request out of ten thousand across three server instances" is exactly the kind of thing that's cheap to design in now (swap to SLF4J + Logback/Log4j2, keep the same call-site shape via a thin wrapper) and expensive to retrofit once there's real traffic and real incidents to debug.

## 4. Tradeoffs already deliberate — not new findings, just confirmed still-current

- **`PlatformType.TEST`-only auth, no real credential check** (`UserService.loginTestUser` accepts any non-empty string as an identity token) — already explicitly the documented state in `00-project-plan.md` §5.1 ("implement the `STEAM` branch... currently only handles `PlatformType.TEST`"), tracked as Beta-phase work. Confirmed still accurate; not a new gap.
- **Plaintext KryoNet transport, hardcoded DB credentials/ports** (`DatabaseManager.java:44-46`, plus the TCP/UDP port setup) — both already called out verbatim in `00-project-plan.md` §7's pre-launch hardening checklist and in `CLAUDE.md`'s own guardrail note. Confirmed still present, exactly as documented; don't re-flag as a "new" finding, and per `CLAUDE.md`'s explicit instruction, don't add config plumbing for this without being asked.
- **`DatabaseManager` singleton pattern** — already flagged in `00-project-plan.md` §7 as "may make future service-layer testing more awkward, revisit if Epic C testing gets painful." Confirmed it hasn't become a real problem yet — every test in this session uses `TestDatabase` (a separate in-memory H2 harness), never `DatabaseManager` itself, so the singleton has imposed zero actual testing cost so far.
- **HikariCP pool sized at 10 max / 2 min idle** (`DatabaseManager.java:49-50`) — a reasonable default for current single-server, pre-launch traffic. Not a finding, just worth remembering to revisit alongside real load testing before Beta, not before.

## 5. Process suggestion — the AOP question you asked about directly

You specifically asked about AOP. Here's the honest answer: this codebase doesn't use it, and I don't think it should reach for a real AOP framework (Spring AOP / AspectJ) given everything else here is deliberately framework-light (manual DI, no Spring anywhere) — introducing one just for cross-cutting concerns would be a bigger architectural shift than the problem justifies. But the *symptom* AOP exists to solve is real and visible here in two forms:

1. **The `try { ... } catch (Exception e) { log.error(...); return ServiceResult.failure(...); }` shape appears in 29 places across 11 of 15 service classes**, hand-copied each time (confirmed by grep, not estimated). It's consistent, which is good — but it's consistent because someone has been carefully copying it, not because anything enforces it. A lightweight, non-framework fix that fits this codebase's existing style: a small `ServiceResult.attempt(MessageCode failureCode, Callable<T> action)` static helper that wraps exactly that pattern once, letting each service method shrink to its actual business logic. This is a refactor worth doing gradually (new methods use it, old ones migrate opportunistically), not all at once.
2. **The auth-check-then-ownership-check shape at the top of every handler** (`if (connection.account == null) return;` followed by an `isCharacterOwnedBy` check) is exactly the kind of "must run before the real logic, easy to forget" concern AOP-style `@Before` advice exists for — and §2.1's bug is the direct, concrete cost of it being manual. Given no AOP framework is warranted here, the pragmatic equivalent is a shared static helper (`GuardChecks.requireAuthenticated(connection)` / `requireOwnership(...)`) that at least makes the *check* one line to call correctly, plus the handler-test coverage from §3.2 as the actual enforcement mechanism (tests catching a missing call, since nothing at compile time can).

Neither of these is urgent for M4, but both are cheap, in-keeping-with-existing-style fixes that directly reduce the chance of another §2.1-shaped bug as handler count keeps growing into the 30s and 40s once combat UI wiring starts.

## 6. Summary table

| # | Finding | Type | Cost to fix now | Cost to fix later |
|---|---|---|---|---|
| 2.1 | Two handlers skip the auth-null-check | Bug | One line ×2 + a test | Same, plus whatever incident surfaces it first |
| 2.2 | `opponent_character_id` cascade deletes another character's history | Bug | One migration line | A live data-integrity incident once ranked play has real traffic |
| 2.3 | Raw token logged on login exception | Bug | Delete one string concat | A real secret leaking into logs once Steam auth lands |
| 3.1 | No index on `battle_history.character_id` | Gap | One migration | Downtime/`CONCURRENTLY` rebuild once the table is large |
| 3.2 | 18/21 handlers untested | Gap | A handful of small tests | More §2.1-shaped bugs, found later and more expensively |
| 3.3 | i18n mechanism unused past 1 seed key | Gap | A migration + a CI check | Untranslated `"!CODE!"` strings shipped once M4 wires error dialogs |
| 3.4 | No structured/leveled logging | Gap | A logging-library swap now, low traffic | Debugging real incidents blind, across real traffic |

## Definition of done for this review

This document is the deliverable — no code changes were made (per this project's planning-only scope). If you want any of §2/§3 acted on, the natural next step is a short implementation prompt per item (or one combined "hardening pass" prompt bundling all of §2 plus 3.1, since those four are cheap and self-contained), handed to Claude Code the same way every epic in this session was. §5's AOP-style helpers are a larger, gradual refactor better done alongside real work touching those files, not as a standalone pass.
