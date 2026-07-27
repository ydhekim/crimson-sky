# Implementation prompt — supersample the body font for crisper text

Different in kind from K7/K8/K9: those were provable code bugs (grounded in a specific line, a specific wrong value). This one is a rendering-quality characteristic, not a logic defect — confirmed **not** OS display scaling (user's Windows scale is 100%, and the window is a fixed, non-resizable 1280×720 matching `BaseScreen.VIRTUAL_WIDTH/HEIGHT` exactly, so there's no viewport-vs-window mismatch either). Flagging that distinction up front: this prompt is a well-established mitigation, not a guaranteed one-shot fix — worth a visual check after merge rather than assuming it fully resolves it.

## Why body text reads soft

`AssetLoader.preloadAssets()` bakes the body font at only 16px (`core/asset/AssetLoader.java:52`), used at `fontScale(1f)` almost everywhere (K8/prompt 41 only added a *second*, larger-baked font for titles — body text was explicitly left alone there since it wasn't being upscaled). Two compounding factors at that size:

1. 16px is genuinely small for anti-aliased FreeType-rendered text — the rasterizer has few source pixels per glyph to work with, so edges look soft even rendered at exactly 1:1.
2. Every other visual element in the app (buttons, rows, swatches) is a flat, hard-edged, solid-color `TextureFactory`-generated texture with zero anti-aliasing (per the M4 foundation cleanup's placeholder-rendering convention) — text sitting next to that harder-edged chrome makes its anti-aliasing softness more noticeable by contrast than it would be in an app with generally softer/rounded visual chrome elsewhere.

## Fix: supersample once, globally, no per-screen changes

The standard mitigation for small FreeType-baked bitmap text: bake the font larger than its intended display size, then downscale — Linear-filtered minification generally produces smoother anti-aliasing than the rasterizer's own hinting does at a tiny native size.

The key piece that avoids a sweep through every screen: `BitmapFontData.setScale(float)` sets a **permanent base scale** on the font itself, which every existing `Label.setFontScale(x)` call already multiplies on top of (that's the same mechanism the `0.85f`/`1.1f`/`1.2f` scale calls use today — this just changes the *baseline* they multiply against, not their own behavior). So baking bigger and immediately scaling back down to the original visual size, once, fixes every current and future label without editing a single screen file.

`AssetLoader.preloadAssets()` — bake at 32px instead of 16 (matching the title font's existing size from K8/prompt 41, so the codebase only carries two font-bake sizes total, not three):

```java
fontParameter.fontFileName = "fonts/Quicksand-Regular.ttf";
fontParameter.fontParameters.size = 32; // was 16 — baked bigger, then scaled back down below
fontParameter.fontParameters.minFilter = Texture.TextureFilter.Linear;
fontParameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
fontParameter.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + TURKISH_CHARS;
```

`CrimsonSky.initializeUI()` — apply the compensating downscale once, right after loading, so every label's visual size stays exactly what it was before this change:

```java
BitmapFont bodyFont = assetManager.get("default-font.ttf", BitmapFont.class);
bodyFont.getData().setScale(0.5f); // baked at 32px, rendered at 16px-equivalent — the supersample
BitmapFont titleFont = assetManager.get("title-font.ttf", BitmapFont.class);
// titleFont unchanged — already baked at its intended 32px display size (K8/prompt 41), no rescale needed
```

Optional, low-risk companion change worth doing in the same pass since it specifically improves downscale quality: enable mipmapping on the body font's parameters (`fontParameter.fontParameters.genMipMaps = true; fontParameter.fontParameters.minFilter = Texture.TextureFilter.MipMapLinearLinear;` — `magFilter` stays `Linear`), since mipmapping exists precisely to make minification look better and this is now, deliberately, a minified font.

## Testing / Definition of Done

1. `gradlew.bat build` — confirm it compiles.
2. `gradlew.bat test` — confirm nothing broke (no test touches font baking).

Manual verification — I'll judge this one myself, since "does it look crisper" isn't something I can confirm from code:

- Compare body text (character names, XP labels, button text, option labels) before/after — looking for genuinely smoother glyph edges, not just a placebo check.
- Confirm no label's visual size shifted — the `setScale(0.5f)` compensation should make this a pure quality change, not a layout change. If anything looks bigger/smaller than before, the scale math is off somewhere.
- If it's still not crisp enough, the next lever to reach for is a higher supersample ratio (e.g. 48px baked, `setScale(0.333f)`) rather than a different technique — flag back rather than assuming this prompt is the final word on it.

Definition of done: body text renders with less visible anti-aliasing softness at the same visual size as before; build and tests are green. This is explicitly a "try the standard fix, then look at it" pass, not a proven-root-cause fix like K7/K9.
