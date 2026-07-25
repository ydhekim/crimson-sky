# Implementation prompt — AchievementsScreen redesign

Fifth screen in the M4 screen-by-screen pass (after Connection/27, Main Menu/28, Settings/29-32). Approved design: centered crimson title + centered "X / Y unlocked" count beneath it, inside the existing boxed panel (`createMainContentPanel()`, already used here — unlike Connection's full-bleed treatment); rows keep their current icon/title/description shape but gain an XP-reward badge and, for unlocked rows, a relative "earned N ago" timestamp; unlocked achievements sort to the top (stable), locked ones stay in a divider-separated group below.

Grounded in the current `AchievementsScreen.java` before writing this — three things below aren't cosmetic, they're real gaps found while reading it.

## 1. Server: `getAchievementsForAccount` has no `ORDER BY`

`AchievementDao.getAchievementsForAccount()` (the query this screen's `AchievementListRequest` hits) has no `ORDER BY` clause at all — unlike its sibling `getAchievementsForCharacterPage()`, which has `ORDER BY ad.id`. That means "locked achievements stay in server order" (the agreed sort) currently means *whatever order Postgres's heap scan happens to return*, not a stable or meaningful order. Fix: add `ORDER BY ad.id` to `getAchievementsForAccount`, matching the sibling query. One-line DAO change, no migration needed (not a schema change).

## 2. Client-side sort: unlocked first, locked stays in (now-stable) server order

In `populateAchievements(List<AccountAchievement> achievements)`, partition into two lists — `unlocked` (sorted by `unlockedAt` descending, newest first) and `locked` (left in the order received, now meaningful per fix #1) — then render unlocked rows, a divider, then locked rows:

```java
List<AccountAchievement> unlocked = new ArrayList<>();
List<AccountAchievement> locked = new ArrayList<>();
for (AccountAchievement ach : achievements) {
    (ach.isUnlocked() ? unlocked : locked).add(ach);
}
unlocked.sort(Comparator.comparing(AccountAchievement::unlockedAt).reversed());
```

## 3. Localize the header count and the load-error message

Two new keys needed — `"Error loading achievements."` (`AchievementsScreen.java:150`) is a hardcoded, unlocalized literal (the same class of gap K4 fixed elsewhere), and the new "X / Y unlocked" subtitle needs a template string since nothing in this codebase currently does parameterized localization. Simplest approach, no new formatting utility: embed `%d`/`%d` placeholders directly in the localized value and use `String.format`.

New migration, `server/src/main/resources/db/migration/V25__Add_Achievements_Screen_Localization.sql`:

```sql
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_LBL_ACHIEVEMENTS_UNLOCKED_COUNT', 'UI'),
    ('UI_MSG_ACHIEVEMENTS_LOAD_ERROR', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_ACHIEVEMENTS_UNLOCKED_COUNT'), 'tr_TR', '%d / %d açıldı'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_LBL_ACHIEVEMENTS_UNLOCKED_COUNT'), 'en_US', '%d / %d unlocked'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_ACHIEVEMENTS_LOAD_ERROR'), 'tr_TR', 'Başarımlar yüklenemedi.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_MSG_ACHIEVEMENTS_LOAD_ERROR'), 'en_US', 'Couldn''t load achievements.');
```

(`UI_LBL_ACHIEVEMENTS` already exists from V23 — reused as-is for the title, no change needed there.)

## 4. Header: centered title + centered count

Replace the current header block in `setupUIShell()`:

```java
VisLabel headerLabel = new VisLabel(game.getLanguageManager().get("UI_LBL_ACHIEVEMENTS"));
headerLabel.setFontScale(2f);
headerLabel.setColor(UiPalette.ACCENT_CRIMSON);
headerLabel.setAlignment(Align.center);
mainPanel.add(headerLabel).padBottom(4).center().row();

unlockedCountLabel = new VisLabel("");
unlockedCountLabel.setColor(UiPalette.TEXT_MUTED);
unlockedCountLabel.setAlignment(Align.center);
mainPanel.add(unlockedCountLabel).padBottom(20).center().row();
```

(`unlockedCountLabel` becomes a field, same shape as `scrollTable`, so `populateAchievements` can set its text once the list arrives — the count isn't known until the response comes back.) In `populateAchievements`, before the per-row loop:

```java
int unlockedCount = (int) achievements.stream().filter(AccountAchievement::isUnlocked).count();
unlockedCountLabel.setText(String.format(
    game.getLanguageManager().get("UI_LBL_ACHIEVEMENTS_UNLOCKED_COUNT"), unlockedCount, achievements.size()));
```

Import `io.github.ydhekim.crimson_sky.ui.UiPalette` and `com.badlogic.gdx.utils.Align` (the latter is already imported for row alignment).

## 5. Row treatment: unlocked vs. locked backgrounds, icon tint, XP badge, timestamp

Build these once in `setupUIShell()` (constructor-time, matching the texture-lifecycle discipline established for `ConnectionScreen` — never rebuild inside a method that can run more than once), add each to `disposables`:

```java
Texture rowBgUnlockedTexture = TextureFactory.createSolidTexture(1, 1, new Color(1f, 1f, 1f, 0.05f));
Texture rowBgLockedTexture = TextureFactory.createSolidTexture(1, 1, new Color(1f, 1f, 1f, 0.025f));
rowBgUnlockedDrawable = new TextureRegionDrawable(new TextureRegion(rowBgUnlockedTexture));
rowBgLockedDrawable = new TextureRegionDrawable(new TextureRegion(rowBgLockedTexture));

Texture xpBadgeUnlockedTexture = TextureFactory.createSolidTexture(1, 1,
    new Color(UiPalette.ACCENT_GOLD.r, UiPalette.ACCENT_GOLD.g, UiPalette.ACCENT_GOLD.b, 0.15f));
Texture xpBadgeLockedTexture = TextureFactory.createSolidTexture(1, 1, new Color(1f, 1f, 1f, 0.05f));
xpBadgeUnlockedDrawable = new TextureRegionDrawable(new TextureRegion(xpBadgeUnlockedTexture));
xpBadgeLockedDrawable = new TextureRegionDrawable(new TextureRegion(xpBadgeLockedTexture));

Texture dividerTexture = TextureFactory.createSolidTexture(1, 1, new Color(1f, 1f, 1f, 0.15f));
dividerDrawable = new TextureRegionDrawable(new TextureRegion(dividerTexture));
```

(Drop the old single `rowBgDrawable` and the old `placeholderIconTexture`/`placeholderIconRegion` pair — icon tint now comes from coloring the existing shared placeholder icon image, not from swapping textures, so only one icon texture is still needed; keep `placeholderIconTexture` creation as-is, just retire `rowBgDrawable`.)

Update `createAchievementRow(...)` to take the `AccountAchievement` itself (rather than pre-extracted title/description strings) so it has `xpReward` and `unlockedAt` available:

```java
private VisTable createAchievementRow(TextureRegion icon, AccountAchievement ach, String title, String description) {
    VisTable rowTable = new VisTable();
    rowTable.setBackground(ach.isUnlocked() ? rowBgUnlockedDrawable : rowBgLockedDrawable);
    rowTable.pad(10);

    Image iconImage = new Image(icon);
    iconImage.setColor(ach.isUnlocked() ? UiPalette.ACCENT_CRIMSON : new Color(0.29f, 0.28f, 0.25f, 1f));
    rowTable.add(iconImage).size(64, 64).padRight(15).align(Align.left);

    VisTable textTable = new VisTable();
    VisLabel titleLabel = new VisLabel(title);
    titleLabel.setColor(ach.isUnlocked() ? Color.WHITE : Color.GRAY);
    titleLabel.setAlignment(Align.left);
    textTable.add(titleLabel).growX().row();

    VisLabel descriptionLabel = new VisLabel(description);
    descriptionLabel.setWrap(true);
    descriptionLabel.setColor(ach.isUnlocked() ? UiPalette.TEXT_MUTED : Color.DARK_GRAY);
    descriptionLabel.setAlignment(Align.topLeft);
    textTable.add(descriptionLabel).growX().row();

    rowTable.add(textTable).expandX().fillX().align(Align.top);

    if (ach.isUnlocked()) {
        VisLabel timeLabel = new VisLabel(formatRelativeTime(ach.unlockedAt()));
        timeLabel.setColor(UiPalette.TEXT_MUTED);
        timeLabel.setFontScale(0.85f);
        rowTable.add(timeLabel).padRight(10).align(Align.right);
    }

    VisLabel xpLabel = new VisLabel("+" + ach.xpReward() + " XP");
    xpLabel.setColor(ach.isUnlocked() ? UiPalette.ACCENT_GOLD : UiPalette.TEXT_MUTED);
    xpLabel.setFontScale(0.85f);
    VisTable xpBadge = new VisTable();
    xpBadge.setBackground(ach.isUnlocked() ? xpBadgeUnlockedDrawable : xpBadgeLockedDrawable);
    xpBadge.pad(4, 10, 4, 10);
    xpBadge.add(xpLabel);
    rowTable.add(xpBadge).align(Align.right);

    return rowTable;
}
```

Update the call site in `populateAchievements` to pass `ach` through, and insert the divider between the two groups:

```java
for (AccountAchievement ach : unlocked) {
    String translatedTitle = game.getLanguageManager().get(ach.titleLocKey());
    String translatedDesc = game.getLanguageManager().get(ach.descLocKey());
    scrollTable.add(createAchievementRow(placeholderIconRegion, ach, translatedTitle, translatedDesc))
        .growX().padBottom(5).row();
}

if (!unlocked.isEmpty() && !locked.isEmpty()) {
    Image divider = new Image(dividerDrawable);
    scrollTable.add(divider).growX().height(1).padTop(4).padBottom(9).row();
}

for (AccountAchievement ach : locked) {
    String translatedTitle = game.getLanguageManager().get(ach.titleLocKey());
    String translatedDesc = game.getLanguageManager().get(ach.descLocKey());
    scrollTable.add(createAchievementRow(placeholderIconRegion, ach, translatedTitle, translatedDesc))
        .growX().padBottom(5).row();
}
```

Add a small relative-time helper. `unlocked_at` is a Postgres `TIMESTAMP` (no timezone) read via `ResultSet.getString()`, so the wire format is space-separated (`"2026-07-21 18:22:51.398"`), not ISO's `T`-separated form — normalize before parsing:

```java
private String formatRelativeTime(String unlockedAt) {
    if (unlockedAt == null) return "";
    try {
        LocalDateTime then = LocalDateTime.parse(unlockedAt.replace(' ', 'T'));
        Duration elapsed = Duration.between(then, LocalDateTime.now());
        long minutes = elapsed.toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = elapsed.toHours();
        if (hours < 24) return hours + "h ago";
        long days = elapsed.toDays();
        if (days == 1) return "Yesterday";
        if (days < 30) return days + "d ago";
        return unlockedAt.substring(0, 10);
    } catch (Exception e) {
        return "";
    }
}
```

(This compares against the client's local clock, not a synced server time — acceptable for a cosmetic relative-time label at this stage; not worth a proper clock-sync mechanism yet. Import `java.time.LocalDateTime` and `java.time.Duration`.)

## 6. Fix the error path and remove dead code

`onAchievementListResponse`'s failure branch (`AchievementsScreen.java:150`):

```java
scrollTable.add(new VisLabel(game.getLanguageManager().get("UI_MSG_ACHIEVEMENTS_LOAD_ERROR"))).expand().center();
```

Remove `getFactionColor(String)` and `createPlaceholderTexture(Color)` — both private, both confirmed unused anywhere in the file (dead code, predates this pass).

## 7. Testing / Definition of Done

No new JUnit coverage needed for the client changes (layout/content, same shape as prompts 24-29); do add a quick assertion to `AchievementListRequestHandlerTest` (or a new DAO-level test if one already exists for this query) that `getAchievementsForAccount` returns rows in ascending `id` order, covering fix #1.

1. `gradlew.bat lwjgl3:run`, reach Achievements with a mix of locked/unlocked achievements — confirm the title is centered and crimson, the "X / Y unlocked" count is centered beneath it and correct, unlocked rows sort above a divider with locked rows below, each row shows an XP badge, and unlocked rows additionally show a relative timestamp.
2. Confirm locked rows stay in a stable order across repeated app restarts (fix #1) rather than shuffling.
3. Switch language, confirm the count label and the error-path message (temporarily stop the server to trigger it) both localize correctly.
4. Confirm dead-code removal didn't break anything (`getFactionColor`/`createPlaceholderTexture` had no call sites).

Definition of done: header matches the approved mockup (centered title + count, inside the existing boxed panel); rows show XP reward and, when unlocked, a relative timestamp; unlocked achievements lead a stable-ordered locked group below a divider; the load-error message and count label are both localized; no dead code remains.
