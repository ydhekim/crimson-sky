# Implementation prompt — M4 foundation cleanup (docs + placeholder-asset strip)

This is the first of three prep steps agreed before resuming M4 screen work (D1a onward): (a) decide the Turkish-font question, (b) this prompt — docs/repo hygiene + strip the "for testing" assets down to one functional font, (c) a UI foundation refactor (button-style/theme system, separate prompt), (d) a shared M4 UI/UX design note, (e) then screen-by-screen work resumes.

This prompt is deliberately narrow: get the repo accurate and the app compiling/running with no styled placeholder art, not make it look good. Making it look good is prompt (c)'s job — this prompt's `BaseScreen` button-style fix is an intentionally ugly stand-in, not a design decision.

## 1. Docs — remove redundant/stale files, keep what they add

**Before deleting `architecture.md`**, fold two details into `CLAUDE.md` that aren't restated anywhere else:
- The FreeType font is generated with an explicit Turkish character set on top of `FreeTypeFontGenerator.DEFAULT_CHARS` (already visible in `AssetLoader.TURKISH_CHARS`, but worth a one-line project-fact callout so it isn't lost as tribal knowledge).
- The fixed-timestep accumulator shape: `while (accumulator >= FIXED_STEP) { engine.update(FIXED_STEP); }`. CLAUDE.md already says simulation uses fixed timestep; it doesn't spell out the accumulator loop.

Then delete `architecture.md`. It's otherwise fully superseded by `CLAUDE.md` — and one part of it is actively wrong, not just redundant: it states UDP as the decided transport for combat sync, while `CLAUDE.md` and `01-system-design-combat-engine.md` both explicitly call TCP-vs-UDP an *open, unresolved* decision. A grep across the repo confirms nothing else references `architecture.md` by name, so this is a clean removal.

**Before deleting `Mizan_Combat_Engine_GDD_v4.pdf`**, two docs cite the literal filename (not just the "Mizan"/"GDD" name, which is fine to keep everywhere — that's the engine's in-fiction name, not a file reference):
- `01-system-design-combat-engine.md` line 5: `"Source of truth for game rules: Mizan_Combat_Engine_GDD_v4.pdf."` — reword to something like: *"Source of truth for game rules: this document (originally distilled from the Mizan Combat Engine GDD v4; the PDF itself is no longer kept in the repo — this doc and its cross-references are self-sufficient)."*
- `05-implementation-prompt-m2-combat-core.md` line 7: `"don't re-derive rules from memory or from Mizan_Combat_Engine_GDD_v4.pdf alone, the planning docs supersede/extend it"` — this is a historical prompt for already-shipped work (M2 is done), so lower stakes, but fix it for consistency: drop the filename, keep the "planning docs supersede it" point.

Then delete the PDF. `00-project-plan.md` and `02-user-stories.md` also mention "Mizan"/"the GDD" but only as the ruleset's name in the abstract (e.g. "the GDD's three worked scenarios as fixed-seed test fixtures") — those scenarios are already implemented as actual test fixtures since M2 shipped, so nothing is lost; no edit needed there.

## 2. README.md — clarify, don't just delete, the android/ios mention

`README.md`'s Platforms section lists `android`/`ios` as if buildable today. They aren't wired into `settings.gradle`. But don't simply delete the mention — `00-project-plan.md` line 49 explicitly treats this as intentional: M6 is scoped to *"wire up the platforms the README already documents but `settings.gradle` doesn't include yet."* Deleting the README mention would silently break that cross-reference. Instead, annotate the two bullets to say what's actually true:

```
- `android`: Android mobile platform. Needs Android SDK. **Planned for M6 — not yet wired into `settings.gradle`.**
- `ios`: iOS mobile platform using MobiVM. Needs a macOS environment and Xcode. **Planned for M6 — not yet wired into `settings.gradle`.**
```

## 3. CLAUDE.md — small additions, no structural change

Add, in whichever section already covers project facts/conventions (near the "as of this writing" paragraph or the ECS section, whichever reads more naturally):
- The fixed-timestep accumulator loop shape (from §1 above).
- A short asset-policy fact: no real art/audio assets exist in the repo yet; all placeholder rendering is code-generated (solid-color `Pixmap`-backed textures via `TextureFactory`, VisUI's own bundled skin styles) rather than shipped image/atlas files, with the single exception of one Turkish-capable font file kept for correct glyph rendering (see §4 below) — so a future session doesn't go looking for the atlases/backgrounds this prompt removes.

## 4. Asset strip

Delete: `assets/background.png`, `assets/demir_avaz_ui_buttons.atlas`, `assets/demir_avaz_ui_buttons_regions.json`, `assets/demir_avaz_ui_buttons_transparent.png`, `assets/achievements/achievements.atlas`, `assets/achievements/achievements.json`, `assets/achievements/achievements.png`, `assets/uiskin.json`, `assets/fonts/Quicksand-Bold.ttf`, `assets/fonts/Quicksand-Light.ttf`, `assets/fonts/Quicksand-Medium.ttf`, `assets/fonts/Quicksand-SemiBold.ttf`.

Keep exactly one font file, per the Turkish-font decision: replace `Quicksand-Regular.ttf` with a single open-license, Turkish-glyph-complete font (e.g. Noto Sans Regular, SIL Open Font License — pick whatever's convenient to source; it's a functional placeholder, not a brand choice) at `assets/fonts/<name>.ttf`. Update `assets/assets.txt` to list only that one retained file (plus `.gitkeep`).

### Code changes this requires — the app will not run without these

**`AssetLoader.preloadAssets()`**: drop the `background.png`, `demir_avaz_ui_buttons.atlas`, and `achievements/achievements.atlas` loads. Keep the FreeType font load (point `fontFileName` at the new font file) and keep `TURKISH_CHARS` — still needed.

**`CrimsonSky.initializeUI()`**: drop `uiAtlas`/`achievementsAtlas` fetch-and-`addRegions` calls and the `VisUI.getSkin().load(Gdx.files.internal("uiskin.json"))` call. Keep `VisUI.load()` (loads VisUI's own bundled default skin) and keep registering the custom font onto it as `"default-font"`.

**`BaseScreen.setupButtonStyle()`** — this is the one that will crash the app on launch if left alone. It currently does `VisUI.getSkin().get("custom", ...)` / `.get("square", ...)`, both keys that only exist in the `uiskin.json` being deleted, and `Skin.get()` throws on a missing key. Temporary fix, explicitly called out as temporary in a code comment:

```java
private void setupButtonStyle() {
    if (VisUI.isLoaded()) {
        // Placeholder-phase stand-in: "custom"/"square" styles no longer exist (uiskin.json
        // removed, see prompt 24). Both point at VisUI's own bundled default style until the
        // UI foundation refactor (prompt following this one) replaces this with a real theme.
        TextButton.TextButtonStyle defaultStyle = VisUI.getSkin().get(TextButton.TextButtonStyle.class);
        customButtonStyle = defaultStyle;
        squareButtonStyle = defaultStyle;
    }
}
```

Screens will look visually generic (VisUI's stock button style, no custom art) after this — expected and fine; that's exactly the "fresh start before investing in assets" the whole cleanup is for.

**`BaseScreen.setupBackground()`** — no change needed. It's already guarded by `game.getAssetManager().isLoaded("background.png", Texture.class)`, which will now correctly return false and skip adding a background image. Screens render on the plain clear color already set in `render()` (`glClearColor(0,0,0,1)`). Worth confirming this in testing, not worth changing in code.

**`AchievementsScreen.populateAchievements()`** — this one has a real, previously-hidden bug: for each achievement it checks `VisUI.getSkin().has(regionName, ...)` before calling `getRegion(regionName)` (fine, defensive), but the *fallback* branch calls `VisUI.getSkin().getRegion("question_normal")` unconditionally — and `Skin.getRegion()` throws if the region doesn't exist, same as `Skin.get()`. With the achievements atlas gone, `"question_normal"` no longer exists either, so every achievement would throw on render, not fall back gracefully. Fix: drop the atlas-region lookup entirely and use a plain placeholder `Drawable`/`Image` from `TextureFactory` instead (add a `TextureFactory.createPlaceholderIconTexture()` following the exact same pattern as the existing `createPlaceholderAvatarTexture(int size)`). Real per-achievement icon art is content work for a later milestone (Epic E), not something this cleanup should try to half-solve with a fragile atlas fallback.

While in this method: it (and `CharactersScreen`) has several Turkish-language debug log/comment strings mixed in with otherwise-English code (`"Sunucudan veri geldiğinde..."`, `"İkon atlasta bulunamadı..."`, etc.) — leave these alone in this prompt. They're a real cleanup item but belong to the UI foundation refactor (prompt (c)), not this one; don't scope-creep this prompt into a broader code-style pass.

## 5. Project plan cross-reference

`00-project-plan.md` §6 ("Dev workflow while assets and a live DB aren't ready") already documents that M4's *new* `CombatScreen` is meant to need zero assets. It doesn't mention that this cleanup pass also retrofits the *existing*, already-built screens (MainMenu/Characters/CharacterCreation/Achievements/Settings) to drop their placeholder art too. Add one sentence to that bullet noting the retrofit and pointing at this prompt, so the "no assets yet" policy reads as covering the whole client, not just the not-yet-built combat screen.

## 6. Testing / Definition of Done

No server changes; `core` has no UI test source set (established earlier this project — Scene2D screens are verified by manual run, not JUnit). Verify by running:

1. `gradlew.bat lwjgl3:run` boots to Main Menu with no exceptions in the log.
2. Navigate to every existing screen (Characters, Character Creation, Achievements, Settings) and back — no crashes, no missing-region/missing-style exceptions.
3. Confirm Turkish labels (the default running language, per current config) render correctly with the new font — no missing-glyph boxes on ğ/ş/ı/İ/ö/ü/ç.
4. Confirm the achievements list renders a placeholder icon for every achievement (no exception, no reliance on `question_normal`).
5. `grep -r` the repo for `architecture.md`, `Mizan_Combat_Engine_GDD_v4.pdf`, `demir_avaz`, `achievements.atlas`, `uiskin.json`, `Quicksand` — no remaining references anywhere (code, docs, or `assets.txt`) except the two doc edits in §1 that intentionally still name the PDF in past tense.

Definition of done: `architecture.md` and the PDF are gone with their citing docs updated; README accurately describes android/ios as planned-not-wired; CLAUDE.md carries the two preserved facts plus the asset-policy note; `assets/` contains only one font file (plus `.gitkeep`/`assets.txt`); the app boots and every existing screen is navigable with no crashes; `AchievementsScreen` no longer has a throwing fallback path. `ScreenManager.java`'s removal and the real button-style/theme system are explicitly out of scope here — next prompt.
