# Implementation prompt — CharactersScreen row layout: Play/Delete side by side, wider XP bar

Two layout tweaks on the merged CharactersScreen redesign (prompt 35), found from a live screenshot.

## 1. Delete sits below Play, not beside it

`CharacterRowBuilder.build()` currently adds Play, then calls `actionsTable.row()`, then adds Delete — stacking them vertically. Put them side by side instead:

```java
Table actionsTable = new Table();
new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_PLAY"))
    .withStyle(accentButtonStyle)
    .withSize(UiMetrics.DIALOG_BUTTON_WIDTH, UiMetrics.DIALOG_BUTTON_HEIGHT)
    .withAction(onPlayAction)
    .buildAndAddTo(actionsTable, 5);   // padBottom no longer applies once they're side by side — see below

new UIButtonBuilder("X")
    .withStyle(iconButtonStyle)
    .withSize(UiMetrics.ICON_BUTTON_SIZE, UiMetrics.ICON_BUTTON_SIZE)
    .withAction(onDeleteAction)
    .buildAndAddTo(actionsTable);
row.add(actionsTable).right().padLeft(20);
```

Removing the `actionsTable.row()` call is the actual fix — both buttons land in the same row of `actionsTable` automatically once that's gone. `buildAndAddTo(actionsTable, 5)`'s second argument (`padBottom`) has no visual effect once there's no second row beneath it; switch that call to the padding-less `buildAndAddTo(actionsTable)` overload and add `.padRight(8)` on the Play button's own cell instead, so there's breathing room between Play and the Delete "X":

```java
new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_PLAY"))
    .withStyle(accentButtonStyle)
    .withSize(UiMetrics.DIALOG_BUTTON_WIDTH, UiMetrics.DIALOG_BUTTON_HEIGHT)
    .withAction(onPlayAction)
    .buildAndAddTo(actionsTable);
```

(`UIButtonBuilder.buildAndAddTo(Table)` doesn't expose a way to chain `.padRight(...)` on the returned cell today — it returns `void`. Simplest fix without changing `UIButtonBuilder`'s API: call `.build()` directly for the Play button and add it to the table with an explicit cell, matching how `CharactersScreen.createCharacterButton` already does this elsewhere in the codebase:

```java
TextButton playButton = new UIButtonBuilder(game.getLanguageManager().get("UI_BTN_PLAY"))
    .withStyle(accentButtonStyle)
    .withAction(onPlayAction)
    .build();
actionsTable.add(playButton).width(UiMetrics.DIALOG_BUTTON_WIDTH).height(UiMetrics.DIALOG_BUTTON_HEIGHT).padRight(8);

new UIButtonBuilder("X")
    .withStyle(iconButtonStyle)
    .withSize(UiMetrics.ICON_BUTTON_SIZE, UiMetrics.ICON_BUTTON_SIZE)
    .withAction(onDeleteAction)
    .buildAndAddTo(actionsTable);
```
)

## 2. Widen the XP progress bar

`VisProgressBar xpBar` is currently `.width(120)`. Widen to `.width(200)` — the row already has room (`infoTable` is the only thing sharing horizontal space with it, and it doesn't compete with the now-side-by-side action buttons for width). If 200 ever visually crowds the row once real character names or longer XP text are in play, that's a quick follow-up tweak, not a structural change.

## 3. Testing / Definition of Done

1. `gradlew.bat lwjgl3:run`, reach Characters — confirm Play and Delete now sit side by side in the same row (Delete to the right of Play, not beneath it), and the XP progress bar is visibly wider and easier to read at a glance.
2. Confirm nothing else in the row shifted unexpectedly (avatar, name, level/XP text, row background).

Definition of done: Play/Delete are horizontally adjacent; the XP bar reads at `width(200)` instead of `120`.
