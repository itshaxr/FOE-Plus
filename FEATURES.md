# FOE+ Features

Everything FOE+ adds to **play.fishonmc.net**. Press **O** in game to open the FOE+ menu, where every feature can be turned on, off or moved.

<p align="center">
  <img src="docs/images/foe-plus-menu.png" alt="The FOE+ menu" width="640">
</p>

**Contents:** [HUD panels](#hud-panels) · [Alerts](#alerts) · [Chat notifications](#chat-notifications) · [Pets](#pets) · [Dry streaks & stats](#dry-streaks--stats) · [Contests](#contests) · [Tools](#tools) · [Keybinds](#keybinds) · [From FOE-R](#from-foe-r)

---

## HUD panels

Every panel can be moved, anchored to any corner or edge, and resized in **O → Move HUD Elements**.
Panels stay exactly where you put them (only kept on screen), and can overlap if you place them that way. A panel whose text changes width, like a countdown, doesn't jump sideways.

| Panel | What it shows |
|---|---|
| **Contest** | Time left, contest type with the fish you need in brackets, best bait, the top 3 and your rank. Warns you when you're not in the contest area. |
| **Daily Quests** | Exact progress on each daily quest, updated after every catch. |
| **Quests** | Your active location quests. |
| **All-Time Stats** | Total Caught (with the share of your catches that were fish), fish per hour, fish time and quests; Total Pets, Shards, Infusion Capsules and Lightning Bottles with their dry streaks; then rarities, variants and Gigantic. |
| **Luck & Scale** | Your real Luck and Scale, read from your `/menu` profile so boosters count, with a live countdown while a global Luck or Scale booster is on. Refreshes automatically when you change location. |
| **Events** | An active Fish Frenzy (where, and how long is left of its hour), a Blooming Oasis (about 15 minutes, while its ♣ is on the boss bar) and an active Fabled event (until it's caught). Kept in sync with `/events`, which FOE+ checks quietly every 15 minutes. |
| **Pet** | Your equipped pet (framed in its rarity colour) and the item it's holding. |
| **Bait** | The bait on your rod and how many you have left. |
| **Alerts** | Active and upcoming cryptid windows with live timers. |
| **Warning** | Wrong pet for this location (names the right one), wrong armor climate, or a nearly full inventory. Not shown at Cypress Lake's spawn until you reach the water. Its text is centred and it stays where you put it. |
| **Top bar** | Level, location and the spot you're at (Big Bear Dock, Thunderwood Marina...), time, balance and catches, plus the community event total and your contribution. |
| **Timers & Crew** | Next to your inventory and as a HUD panel: next moon, Tackle Shop restock (every 4 hours, ✔ once you've checked it), Cosmetic Store reset (7pm EST), your votes (x/7 in the last 24 hours), then whether a crew member is in range and your crew and friends, online first. In the inventory a **+** adds a friend and the list scrolls. |
| **Equipment bar** | Armor, rod parts and bait at a glance. |
| **Catch popup** | Big popup for the fish you just caught. |

The default FOE-R HUDs start hidden so the screen isn't cluttered. Turn any of them back on in **O → Custom HUDs**.

---

## Alerts

The Alerts panel and chat tell you when a rare catch is possible and how long the window lasts. The timers count down smoothly.

| Alert | Where | When | Odds |
|---|---|---|---|
| **Witching Hour** (Bigfoot) | Cypress Lake | 1–3 AM in game | 1 in 300 |
| **Titanoboa** (Titan's Fang) | Amazon River | 2–4 AM in game | 1 in 1,500 |
| **Bessie** (Silver Scale) | Great Lakes | 4–6 AM in game | 1 in 1,000 |
| **Bessie**, moon | Great Lakes | During a moon | 1 in 500 |
| **Rougarou** (Blood / Claw) | Toledo Bend | During a moon | 1 in 300 |
| **Garaboncias** (Concoction / Essence) | Danube River | During a rainbow | 1 in 250 |
| **Fabled event** | Announced in chat | Until someone catches it | — |

- **Fabled tracker:** starts when the event appears in chat and ends when the fish is caught, and FOE+ tells you who caught it.
- **Moon timer:** counts down to the next moon on its 2 h 40 min cycle, and re-syncs itself every time a moon starts.
- **Optional:** chat lines, sounds, "only alerts for where I am", and how far ahead to show upcoming windows.

---

## Chat notifications

All FOE+ messages share one clean style, starting with a bold **[FOE+]** tag, with location names in the server's own colours:

```
[FOE+] PET PULLED! Eagle Pet (87.0% A) · Dry streak 164
[FOE+] You caught MELANISTIC 🐟 Largemouth Bass · Dry streak 1,234
[FOE+] You caught GIGANTIC 🐟 Walleye · Dry streak 5,321
[FOE+] RARE CATCH! Infusion Capsule · Dry streak 2,410
[FOE+] RARE CATCH! Lightning in a Bottle · Dry streak 220
[FOE+] WITCHING HOUR Cypress Lake · Bigfoot 1/300 · 2:10 left
[FOE+] FABLED CAUGHT Danube River · by Steve
[FOE+] WRONG PET Eagle Pet · Danube River needs a Wolf Pet (in inventory)
[FOE+] CONTEST New personal best · Rank #4 · 8.0 lb
```

In game, the rarity, variant and Gigantic badges appear in place of the words.

- **Pet pulls:** rarity badge, pet, percentage, rating and your pet dry streak.
- **Variants:** Albino, Melanistic, Trophy and Fabled, with the fish and that variant's dry streak.
- **Gigantic catches:** the Gigantic badge, the fish and its dry streak.
- **Infusion Capsules and Lightning Bottles:** when the server says you pulled one, with its dry streak.
- **Contest chat:** proper spacing, and the hourly auto-refresh output is hidden.
- You can turn each notification on or off in Settings.

---

## Pets

- **Exact pet per location:** FOE+ knows the one pet each location needs.

  | Location | Pet | Location | Pet |
  |---|---|---|---|
  | Cypress Lake | Bullfrog | Oil Rig | Pelican |
  | Kenai River | Bear | Amazon River | Capybara |
  | Lake Biwa | Fox | Mediterranean Sea | Lynx |
  | Murray River | Kangaroo | Cape Cod | Shark |
  | Everglades | Marsh Rabbit | Hawaii | Dolphin |
  | Key West | Sea Turtle | Cairns | Koala |
  | Toledo Bend | Duck | Lofoten Islands | Sheep |
  | Great Lakes | Eagle | Nile River | Hippo |
  | Danube River | Wolf | | |

- **Wrong pet warning:** a warning box plus a chat line when you arrive with the wrong pet, telling you whether the right one is in your inventory.
- **Held item:** the item your pet is holding shows in the Pet panel.
- **Pet tooltips:** rating letter and max Luck / max Scale markers.
- **Max rod parts:** a bright purple border round rod parts rolled at the highest their rarity can roll, and on rods with two different max stats.
- **Pet Prices (`U`):** the full price table from the community price sheet, updated once a day.

---

## Dry streaks & stats

- **Live dry streaks** for every rarity, variant, Gigantic, pets, shards, Infusion Capsules and Lightning Bottles, shown on the All-Time Stats panel. Like FOE, the capsule streak only counts Blood Moon catches and the bottle streak only thunderstorm catches, since that's when they drop.
- **Import from old installs:** FOE+ finds previous FOE and FOE-R installs (Prism, Modrinth, CurseForge, `.minecraft` and other profiles) and imports your stats on first launch.
- **Import Dry Streaks:** pulls streaks (including pets, shards, Infusion Capsules and Lightning Bottles) from your newest old FOE or FOE-R install, plus your pet, shard, capsule and bottle totals where they're higher there. A streak you've reset since then is never overwritten by an older, higher one.
- **Edit Dry Streaks:** set any streak by hand (rarities, variants, Gigantic, pets, shards, Infusion Capsules and Lightning Bottles). Manual edits count as newest, so an import won't undo them.
- **Edit All-Time Stats:** fix any total by hand (fish caught, pets, shards, Infusion Capsules, Lightning Bottles, quests, rarities, variants and Gigantic). Dry streaks stay as they are.
- **Import FOE-R stats:** uses another install's stats if it has more fish, after backing yours up.
- **Reset Fish Timer:** start fish time and fish per hour from zero.

---

## Contests

- The top 3 always show, plus your own rank and best catch.
- The fish the contest wants is shown in brackets next to the contest type.
- The panel updates as soon as the server posts a new personal best (`CONTESTS » 26.85lb → 27.45lb`).
- It also refreshes when you change area, and tells you when you're outside the contest area.

---

## Tools

- **Auto Tip (`M`):** automatically tips reaction winners and chummers, with per-tip amounts, rarity badges and a session total.
- **Bait sorter (`B`):** merges and sorts the baits in your inventory, chests and vault, by rarity or by amount. It remembers the order you last picked on the vault's sort buttons.
- **Personal Vault:** page buttons 1–7, sorting baits by amount, rarity or name (name groups every Crankbait together; Shift-click to reverse; pets and other items stay put), and rank badges on locked pages.
- **Inspect:** hold Shift to see fish details, pet % and armor rolls, or set Inspect to **Always On**, **Pets Only** or **Armor Only** in the O menu.
- **Friends:** `/foe friend add <name>`, `remove` and `list`. Friends show on the Timers & Crew panel.
- **Crew and friend alerts:** a chat line when a crew member or friend comes online or goes offline.
- **Dismiss warnings (`-`):** hides the warning box until you move to another location.
- **Bobber presets:** 16 colour sets for the 3D bobber (Classic, Cherry, Ocean, Ice, Lime, Emerald, Mint, Gold, Bee, Pumpkin, Lava, Candy, Royal, Galaxy, Midnight, Snow), picked in **O → Bobber**. Other FOE+ users near you see your preset, and you see theirs. Players without FOE+ stay Classic.
- **FOE+ users:** a **+** (with a 1-pixel gap so it never touches) after the name of nearby players who use FOE+, in the colour they pick in the O menu (16 colours).

---

## Keybinds

| Key | Action |
|---|---|
| `O` | Open the FOE+ menu |
| `M` | Auto Tip |
| `U` | Pet Prices |
| `B` | Sort baits |
| `-` | Dismiss the warning box at this location |
| `Left Shift` | Inspect (hold) |

All keys can be changed in **O → Settings**.

---

## From FOE-R

FOE+ keeps everything from FishonMC-Extras-R. Find it under **O → More (FOE-R)**:

- Profile, location and notifier HUDs (caught fish and items, completed quests, full inventory)
- Sidebar quests and the hotbar HUD (armor, rod parts, bait)
- Rarity markers on items and a smaller stack counter (it also counts baits)
- A full real-time stats tracker that can import from your server profile
- A 3D fish bobber model
- Triggers, timers, notifications and chat snippets

---

<sub>FOE+ is built on [FishonMC-Extras-R](https://github.com/FishOnExtras/FishonMC-Extras-R), with features from [FOECollab](https://github.com/Kimberlyow/FOECollab) and [FishOnAutotip](https://github.com/Kimberlyow/FishOnAutotip). Licensed under the AGPL-3.0. Not affiliated with Mojang, Microsoft or the FishOnMC server.</sub>
