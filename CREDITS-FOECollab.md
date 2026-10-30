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
