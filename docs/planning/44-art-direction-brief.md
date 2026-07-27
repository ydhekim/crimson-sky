# Art direction brief — for the designer

**This is not a Claude Code implementation prompt.** Every other numbered doc from 26 onward in this folder is written for Claude Code to implement directly. This one is written for a human artist/designer to work from. It exists so the eventual M5 asset swap (system design §24, project plan §6) has a concrete, bounded brief behind it instead of ad hoc requests — and so "what does the designer need to know" is captured once, durably, rather than re-explained per conversation.

Decided 2026-07-27: working with a commissioned/collaborating designer rather than a licensed asset pack. Style: retro pixel art. Character portraits: modular/layered parts, not one drawing per appearance combination. Sequencing: UI chrome first.

**Fact-check pass, 2026-07-27:** every count, color, and name below was verified against the actual code before this brief goes to a human who'll quote a price from it — palette hexes against `UiPalette.java`, the item/skill/pet names against `04-starter-content.md`, the eight stats against `Stats.java`, the four badge categories against the `V15` seed migration, the appearance lists against `Appearance.java`. Three things didn't survive that check and are corrected in place: the display-scaling claim in §2 (the app *does* rescale at non-integer factors today), the potion count in §4 (understated), and the hair-layer count in §5 (overstated). One asset category was missing entirely and has been added (§4, app icon).

## 1. Why this exists, and what it doesn't block

Nothing in M2–M4 (the combat engine, matchmaking, persistence, and every screen built so far) needs any of this art to exist. M4 is deliberately placeholder-only — flat `TextureFactory`-generated rectangles, no atlases, no `uiskin.json` (`docs/planning/24-implementation-prompt-m4-foundation-cleanup.md`) — specifically so real art can be commissioned on its own timeline without blocking engineering. The designer can start today; nothing here is on the M4 critical path. Real assets get consumed at M5/Beta, through the seam system design §24 already describes (`CombatVisualFactory`'s category→color mapping becomes category→`Drawable`, same call sites, no screen rewrite).

## 2. Style direction

Retro pixel art — grid-locked sprites, not smooth vector/painted illustration. Concretely:

- **Fixed pixel grid.** Pick one base unit (e.g. every sprite's canvas is a multiple of 16px) and hold it across every asset category below. Mixing pixel densities (a 16px-grid icon next to a 24px-grid one) is the single most common thing that makes a pixel-art UI look inconsistent — worth being strict about this from asset one, not fixing it later.
- **Palette discipline: reuse what's already committed in code, don't reinvent it.** Several screens already render these exact colors programmatically (`UiPalette.java`): crimson `#8A2A2A` / gold `#C99A4A` for the Wardens faction, blue `#3A6EA5` / silver `#B8C4D0` for the Skyborn, near-black `#141210` background. The designer's art needs to read correctly *against* these, and faction-coded art (crests, faction-locked skill icons) should use these hues specifically — not close-but-different shades — since UI chrome elsewhere on the same screen will be rendered in the exact hex value, not the designer's interpretation of it.
- **Integer scaling — an open engineering problem, not a solved one.** Pixel art rendered at a non-integer scale factor (1.25x, 1.5x) looks smeared regardless of filtering, and *this app rescales at exactly those factors today*. `BaseScreen` renders every screen through a `FitViewport` at a fixed 1280×720 virtual resolution (`VIRTUAL_WIDTH/HEIGHT`), and `Lwjgl3Launcher` does start at a non-resizable 1280×720 — but `setResizable(false)` only stops the user *dragging* the window edge. `SettingsScreen`'s resolution dropdown offers **1280x720 / 1600x900 / 1920x1080** and applies the choice immediately via `DisplaySettings.apply()` → `Gdx.graphics.setWindowedMode()`, and its fullscreen checkbox switches to whatever the monitor's native mode is. So the live scale factors a player can already select are 1.0x, **1.25x**, and **1.5x**, plus arbitrary fullscreen factors (a 2560×1440 monitor lands on a clean 2.0x; a 1366×768 laptop lands on 1.067x). Two of the three windowed options and most fullscreen cases are non-integer.

  This is an engineering problem to fix, not a constraint the designer should draw around — nothing in the art changes based on how it's resolved. It's tracked as story **K12** in `02-user-stories.md`. The designer should assume the game will eventually present their art at clean integer multiples; the fix (restricting the offered resolutions to whole multiples, or snapping the viewport to an integer scale and letterboxing the remainder) is on our side. Flagged here because the brief previously asserted the opposite — that there's no scaling at all today — and a base-grid decision made on that assumption would have been made on a false premise.

## 3. Technical integration contract

For whoever wires these assets into the engine later (most likely Claude Code, working from a follow-up implementation prompt once real files exist):

- **Format:** PNG, transparent background, RGBA.
- **Filtering:** `Texture.TextureFilter.Nearest` for every pixel-art asset (items, portraits, chrome, crests, badges) — `Linear`, which the body/title fonts intentionally use (K10/K8), would blur pixel art. Two different asset families, two different filter settings; don't unify them.
- **Packing:** these should end up in a `TextureAtlas` (LibGDX's standard multi-sprite-sheet format, built via TexturePacker) rather than loaded as individual loose files the way the current font is — this project has zero atlases today, so this is new infrastructure, not an extension of an existing pattern. The designer doesn't need to run TexturePacker themselves; delivering organized, consistently-named individual PNGs is enough, and packing is an engineering step.
- **Naming convention:** match the category vocabulary system design §24 already established for `CombatVisualFactory`/`ItemCategoryPalette` — e.g. `weapon_longsword.png`, `skill_fireball.png`, `pet_wolf.png`, `stat_strength.png`, `crest_wardens.png` — rather than inventing a separate naming scheme, so the eventual loader code is a straightforward key lookup.
- **File location:** a new `assets/` subfolder structure (e.g. `assets/ui/`, `assets/items/`, `assets/portraits/`), created when the first real files land. Precise current state: `assets/` holds only `.gitkeep` and `fonts/Quicksand-Regular.ttf` in version control. An empty `assets/ui/` directory exists on disk but is untracked and empty — git can't track an empty directory, so it isn't a committed convention yet, just a leftover. Don't read it as a structure already agreed on.

## 4. Asset list — bounded, not open-ended

Every item below is enumerable from content that's already locked in, not speculative. Counting each button state as its own drawn piece, a complete v1 set is roughly **40 pieces**, not counting portrait layer variants (§5).

**Priority 1 — UI chrome** (touches every screen already built this session; highest visual return per asset):
- Panel/border frame (the boxed-content-area treatment `BaseScreen.createMainContentPanel()` currently renders as a flat rectangle)
- Primary button — up/hover/pressed states (currently `UiTheme`'s flat crimson/gold rectangles)
- Secondary/icon button — up/hover/pressed states
- Progress bar — track + fill (used for XP bars today, stat point allocation, quest progress)
- Text field frame (character name entry, currently a bare `VisTextField`)
- Dialog/modal frame (confirmation dialogs currently use VisUI's stock look)
- **App/window icon**, at 16/32/64/128px. Added on the 2026-07-27 fact-check pass — the brief originally missed it entirely. `Lwjgl3Launcher.getDefaultConfiguration()` still calls `configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png")`, and those four files (`lwjgl3/src/main/resources/`) are the stock libGDX logo shipped verbatim from the project template. It's the only place in the running product that displays *someone else's* logo as if it were ours, it's the asset a player sees before any screen renders, and a 16px icon is the most natural pixel-art brief imaginable. Note these four sizes are already an integer-multiple ladder, so §2's scaling problem doesn't touch them. (Steam capsule/library art is a different, later ask — Epic G, out of scope here.)

**Priority 2 — faction crests** (2): Wardens (crimson/gold), Skyborn (blue/silver). Replaces the placeholder rotated-square treatment (`CrestFactory`).

**Priority 3 — stat icons** (8): Strength, Dexterity, Vitality, Intelligence, Wisdom, Spirit, Speed, Insight.

**Priority 4 — item icons**, from `04-starter-content.md` (the locked starter set — expect more once Epic E makes content data-driven, but this is the real v1 list, not a placeholder):
- Weapons (3): Twin Daggers, Steel Longsword, Warhammer
- Skills (4): Spark, Lightning Bolt, Fireball, Meteor
- Pets (4): Sparrow, Hound, Wolf, Bear
- Shop/consumables (~6 fixed + potions, system design §18): weapon repair, pet health repair, skill-restoration scroll, skill-tree reset token, Repair Token, Pet Care Kit.
- **Potions — the count here is a design decision, not a number to read off.** Corrected on the fact-check pass: the brief originally said "~3, one per `ResourceType`," but §18 authors potions along *two* axes, not one. A potion is a `Skill` with `SkillType.CONSUMABLE`, carrying both a `ResourceType` (HEALTH / MANA / STAMINA) and a flat `restoreAmount`, where each potency is its own authored instance — §18's worked example is literally "Small Health Potion restores 100, Medium 200, Large 300." Taken to its full cross product that's **9** icons, not 3. Two axes to collapse, and the cheap answer collapses both: one potion silhouette, hue carrying the resource (red/blue/green) and a size or fill-level variation carrying the potency — 1 drawn shape plus tint/fill rules, instead of 9 drawings. Worth one direct conversation with the designer; it's the single largest count swing in this whole list (1 vs. 9) and it's entirely a style call, not an engineering constraint.

**Priority 5 — achievement badges.** Cheapest v1 option: one shared badge shape, tinted per the real `category` column (`COMBAT`/`PROGRESSION`/`COLLECTION`/`ONBOARDING`) — matches what `AchievementsScreen` already does with its single placeholder shape today, just with real art instead of a tinted rectangle. Unique per-achievement badge art (10 today, growing) is a legitimate stretch goal, not a v1 requirement.

**Priority 6 — character portrait layers** (modular, see §5 for why this isn't "96 portraits").

## 5. Character portraits — modular layers, not combinatorial art

`Appearance` (`common/model/Appearance.java`) curates 2 genders × 3 hair types × 4 hair colors × 4 skin colors = 96 raw combinations. Hand-drawing 96 portraits isn't the intent — draw a bounded set of *layers* that composite at runtime instead:

- **Body/base silhouette** — 1 per gender (2 total), drawn in a neutral tone if skin color will be applied as a runtime tint, or drawn once per skin tone if the designer prefers hand-painted shading per tone (worth a direct conversation with the designer on which — hand-tinting is cheaper/more consistent, hand-painting per tone can look better but is 4x the work for this one layer).
- **Hair shape** — **2**, not 3. `HAIR_TYPES` is `SHORT`, `LONG`, `BALD`, and `BALD` is the *absence* of the hair layer, not a third thing to draw — it composites as body + no hair. So it's 2 drawn shapes if they can work gender-independently, 4 if not. Hair *color* is a strong candidate for a runtime tint rather than 4 separately-drawn colors per shape, for the same cost reason.
- Result: roughly **4–8** drawn layers instead of 96 portraits, composited per-character at runtime the same way `Loadout` items already get assembled into a display, not a new architectural pattern.

Also worth knowing when talking scope: 96 is the raw cross product, but it overstates how many *visually distinct* portraits actually exist, because `hairColor` is meaningless on a `BALD` character — the model stores it (it's a valid, validated field) and nothing renders it. The real distinct-appearance count is 2 genders × (2 hair shapes × 4 colors + 1 bald) × 4 skin tones = **72**. Doesn't change what the designer draws — the layer counts above are what they're quoting on — but if the number 96 comes up in a scope conversation, it's the wrong number to be anchoring on.

This is a scope/cost conversation to have directly with the designer once they're engaged — the split above (tint vs. hand-paint per skin tone) materially changes their workload, and only they can say which fits their process and rate.

## 6. Explicit non-goals for v1

- **No animation frames.** D1a's `CombatScreen` (system design §10) plays back turns via Scene2D `Action`s — position/scale/color tweening on static images — not hand-drawn frame-by-frame animation. Static icons are enough.
- **No unique art per weapon rarity tier beyond the existing category-color system** (Firebrick/Royal/Forest/Gold per `ActionSource`, system design §24) unless the designer specifically wants to design a rarity-tier visual language — that would be an addition to scope, not an assumption baked into this brief.
- **No combat background/environment art** — battles aren't visually staged yet; that's downstream of D1a existing at all (currently shelved, task tracked separately).

## 7. Cross-references

- `01-system-design-combat-engine.md` §24 — the `CombatVisualFactory`/`ItemCategoryPalette` seam this brief's assets eventually plug into. (Both are still design-only; neither class exists in the codebase yet.)
- `01-system-design-combat-engine.md` §18 — the source of truth for the shop/consumable list and the potion `ResourceType`×potency cross product in §4.
- `00-project-plan.md` §6 — "dev workflow while assets aren't ready," the M4-placeholder/M5-real-art split this brief operates inside of.
- `02-user-stories.md` **K12** — the non-integer display-scaling issue §2 depends on; ours to fix, doesn't block the designer.
- `04-starter-content.md` — the source of truth for the weapon/skill/pet list in §4.
- `common/model/Appearance.java` — the source of truth for the gender/hair/skin curated lists in §5.
- `core/ui/UiPalette.java` — the source of truth for the five hex values in §2.
- `core/screen/SettingsScreen.java` + `core/ui/DisplaySettings.java` + `lwjgl3/Lwjgl3Launcher.java` — where §2's real scale factors and §4's stock window icon come from.
