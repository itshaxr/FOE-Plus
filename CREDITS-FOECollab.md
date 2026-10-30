# Bait Sorter credit

The bait sorter (`helper/SortPlanner.java`, `handler/logic/BaitSorterHandler.java`,
`type/baitsorter/BaitSortOrder.java`) was ported from **FOECollab**
(https://github.com/Kimberlyow/FOECollab), licensed under the GNU AGPL-3.0.
It was modified: rewritten from Yarn to Mojang mappings and from Cloth Config to Fzzy Config.
If you redistribute a build containing it, those files remain under AGPL-3.0
(GPL-3.0 §13 permits combining them with this GPL-3.0 project).

## Max Luck/Scale pet markers

The pet markers (clover = max luck, anchor = max scale, combined icon = both) and the textures
`textures/gui/sprites/icons/max_luck.png`, `max_scale.png`, `max_perfect.png` were also ported from
FOECollab (AGPL-3.0). The drawing code was rewritten into `ItemRendererHandler.drawMaxPetStats`
using FOE-R's existing `PetTagObject` getters.

## Pet rating letter, vault page buttons, info panels

Also ported from FOECollab (AGPL-3.0) and rewritten for FOE-R:
- Always-on pet rating letter (`ItemRendererHandler.drawPetRatingLetter`).
- Personal vault page buttons 1–7 (`PersonalVaultScreenRenderHandler`).
- Contest, Daily Quests, Quests and All-Time Stats panels (`screens/element/hud/*PanelElement.java`),
  contest and daily-mission parsing (`ContestTrackerHandler`, `DailyQuestHandler`), and the panel
  border textures under `textures/gui/sprites/themes/`.
- Moon cycle timer in the inventory screen (`MoonTimerHandler`, `InventoryScreenRenderHandler.renderMoonTimer`).
- FOE title screen (`screens/FoeTitleScreen.java`) with its logo and background images under `textures/gui/title/` and `sprites/title/`.
- FOE catch popup (`screens/element/hud/CatchPopupElement.java`).
- FOE top bar (`TopBarElement`) and pet panel (`PetPanelElement`).
- FOE main menu (`FoeMainScreen`), HUD editor layout (`MoveElementScreen`), bait panel and equipped-pet highlight (`icons/selected_slot.png`).
- Equipment bar (`EquipmentBarElement`), wrong-gear warning (`GearWarningPanelElement`) and the location/climate table (`helper/FoeLocations.java`).
