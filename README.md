# FOE+

FOE+ is a quality-of-life mod for **play.fishonmc.net** (Fabric, Minecraft 1.21.11).
It is built on [FishonMC-Extras-R](https://github.com/FishOnExtras/FishonMC-Extras-R) (FOE-R) and brings in the features of
[FOECollab](https://github.com/Kimberlyow/FOECollab) (FOE) and [FishOnAutotip](https://github.com/Kimberlyow/FishOnAutotip),
plus new features of its own.

Press **O** in game to open the FOE+ menu.

**[See the full feature list →](FEATURES.md)**

## Features

**HUD panels** (move, anchor and resize them in *O → Move HUD Elements*; they never leave the screen or cover each other)
- Contest: timer, type with the fish you need, best bait from a bait sheet, top 3 and your rank. Updates every minute, on a new personal best and when you change area.
- Daily Quests: exact progress after every catch.
- All-Time Stats: fish, fish/hour, fish time, rarities, Gigantic and variants with dry streaks.
- Quests, Pet (with held item), Bait, Luck & Scale (read from your /menu profile, boosters included).
- Alerts: Witching Hour, Titanoboa, Rougarou, Garaboncias, Bessie and Fabled events, with timers.
- Warning box: wrong pet for the location (names the right one), wrong armor climate, inventory almost full. Hide it with `-` until you move.
- Top bar, equipment bar, catch popup, moon timer (inventory screen).

**Chat**
- Clean `[FOE+]` lines for pets, variants, Gigantic catches, alerts, Fabled catches, wrong pet, contest best and tips.
- Contest auto-refresh output is hidden; contest messages get proper spacing.

**Tools**
- Bait sorter (`B`), Personal Vault page buttons and sorting (amount / rarity).
- Auto Tip (`M`): tip reaction winners and chummers automatically.
- Pet Prices (`U`): price table from a Google Sheet (updated once a day).
- Inspect always-on toggle, rating letter and max luck/scale markers on pets.
- `+` after the names of nearby FOE+ users.

**Stats**
- Import stats from previous FOE / FOE-R installs (other profiles too), import dry streaks (newer streaks are never overwritten), edit dry streaks by hand.

## Building

Requires Java 21.

```
./gradlew build
```

The mod jar is written to `build/libs/`.

## Credits

- **FishonMC-Extras-R** by DannyPX and contributors (GPL-3.0): the base of this mod.
- **FOECollab** by Kimberlyow and contributors (AGPL-3.0): many features were ported; see [CREDITS-FOECollab.md](CREDITS-FOECollab.md) for the list.
- **FishOnAutotip** by Kimberlyow (GPL-3.0): Auto Tip.

## License

FOE+ contains code under the GNU GPL-3.0 (from FOE-R and FishOnAutotip) and the GNU AGPL-3.0 (from FOECollab).
GPL-3.0 section 13 allows combining them; the combined work is distributed under the terms of the
**GNU Affero General Public License v3.0** ([LICENSE-AGPL-3.0](LICENSE-AGPL-3.0)), and FOE-R's original GPL-3.0 [LICENSE](LICENSE) file is kept.

**Disclaimer:** Not affiliated with Mojang, Microsoft or the FishOnMC server. This is a player-made mod.
