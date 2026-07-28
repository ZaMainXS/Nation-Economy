<div align="center">

<img src="src/main/resources/assets/nationeconomy/icon.png" width="128" alt="Nation & Economy icon"/>

# Nation & Economy

**A Fabric 1.21.11 server-side mod that combines an EconomyShopGUI-style shop with a full nations / land-claim system.**

Runs entirely on the server — players join with a **vanilla client**, no client mod needed.

</div>

---

## Features

### 🛒 Economy & Shop

* `/shop` opens a polished chest-GUI shop.
  * The main menu is a **9x4 window with 30 category slots** you can arrange freely.
  * Ships with placeholder categories — **Blocks, Redstone Mats, Pots, Food** — all fully buyable/sellable from day one.
  * Hovering an item shows its **buy price and sell price** plus click instructions.
  * Left-click = buy 1, Shift+Left = buy a full stack, Right-click = sell 1, Shift+Right = sell all you own of it.
  * Built-in balance display, one-click *Sell All* button and paginated category pages.
* `/sell [item] [amount]` – sell the held stack or a chosen item.
* `/sellall` – sell everything in your inventory the shop buys.
* `/sellhelditem` – sell exactly what you're holding.
* `/balance` (`/bal`), `/pay <player> <amount>`, `/baltop`.
* Admin money control: `/eco give|take|set <player> <amount>` (works for offline players too).

### 🏴 Nations & Land

* `/nation create <name>` – everyone can found a nation. Names are 3–16 characters and **run through a slur/profanity filter** (incl. the hard-r / n-word, with leetspeak normalization).
* `/nation join <name>`, `/nation leave`, `/nation disband`, `/nation members`, `/nation list`, `/nation info [name]`.
* `/claimland` gives you the **Land Claim Shovel**: left-click one corner, right-click the other, `/claimland confirm`.
* Every nation starts with a **1000×1000 (1,000,000 block) claim allowance**; `/nation upgrade` consumes **1 netherite ingot for +100 claim blocks**.
* Claims belong to your nation and **cannot overlap** other nations.
* Protection: uninvited players **can't break, place, open chests or use anything** inside your land. Granular grants:
  * `/nation allow access <player> <break|place|chest|use|all>` (leader only)
  * `/nation deny access <player> <...>` to revoke, `/nation trusted` to list grants.
* `/nation banish <player>` kicks *and* bans them from rejoining (`/nation unbanish` lifts it).
* `/nation color <color>` – nation color in **every color in existence**: the 16 vanilla colors, ~20 extra named colors, **or any `#RRGGBB` hex**.
* Your nation shows **in the tab list and above your head** (`[Nation]` prefix via scoreboard teams) and **in chat** (`[Nation] <Player> message`).
* Walking into another nation's land shows a **big on-screen title** with the nation name in its color and how much land it has claimed; the action bar notes your own land and wilderness.
* `/nation map [radius]` – a chat-rendered territory map: one colored square per chunk in every nation's color, hover shows the nation name, its total claimed blocks, and a legend listing all visible nations with claim sizes.

> **Why a chat map?** A real full-screen minimap texture can only be drawn by a client mod —
> this mod is 100% server-side so vanilla players can join. The chat map + hover text +
> border titles carry the same information without requiring anything on the client.

---

## Commands

### Player commands

| Command | Description |
| --- | --- |
| `/shop` | Open the shop GUI |
| `/sell [item] [amount]` | Sell held item, or a specific item/amount |
| `/sellall` | Sell everything sellable in your inventory |
| `/sellhelditem` | Sell the item you're holding |
| `/balance`, `/bal` | Show your balance |
| `/pay <player> <amount>` | Pay another player |
| `/baltop` | Richest players |
| `/nation` | Help overview |
| `/nation create <name>` | Found a nation |
| `/nation join <name>` | Join a nation |
| `/nation leave` | Leave your nation |
| `/nation banish <player>` | Kick & ban from your nation *(leader)* |
| `/nation unbanish <player>` | Lift a ban *(leader)* |
| `/nation allow access <player> <perm>` | Grant land permission `break/place/chest/use/all` *(leader)* |
| `/nation deny access <player> <perm>` | Revoke a land permission *(leader)* |
| `/nation trusted` | See who has access to your land |
| `/nation color <color>` | Set nation color — name or `#RRGGBB` *(leader)* |
| `/nation colors` | Show all named colors |
| `/nation info [name]` | Nation details & claimed land |
| `/nation list` | All nations |
| `/nation members` | Your nation's members |
| `/nation map [radius]` | Territory map around you (chunks) |
| `/nation upgrade` | Spend 1 netherite ingot → +100 claim blocks |
| `/nation unclaim` | Unclaim the land you stand in *(leader)* |
| `/nation disband` | Delete your nation *(leader)* |
| `/claimland` | Get the claim shovel |
| `/claimland confirm` | Claim the selected area |
| `/claimland cancel` | Clear your selection |
| `/claimland info` | Selection / land-allowance summary |

### Admin commands (op level 2+)

| Command | Description |
| --- | --- |
| `/shopadmin category create <slot 0-29> <icon-item> <name>` | Create a category. The name supports **custom formatting**: `&0-&f` colors, `&l` bold, `&o` italic, `&n` underline, `&#RRGGBB` hex — e.g. `/shopadmin category create 0 minecraft:grass_block &a&lBlocks` |
| `/shopadmin category delete <category>` | Delete a category |
| `/shopadmin category list` | List categories, slots, icons, item counts |
| `/shopadmin item add <category> <item> <buyPrice> <sellPrice>` | Add/update an item with buy & sell price (`-1` disables selling) |
| `/shopadmin item remove <category> <item>` | Remove an item from a category |
| `/shopadmin reload` | Reload `shop.json` from disk |
| `/eco give/take/set <player> <amount>` | Manage balances |

Ops (permission level 3+) bypass land protection.

---

## Installation

1. Run a **Fabric server for Minecraft 1.21.11** (Loader ≥ 0.18.0).
2. Put **Fabric API ≥ 0.141.x** (`fabric-api-0.141.x+1.21.11.jar`) in `mods/`.
3. Build this mod (below) and drop `nation-economy-1.0.0.jar` into `mods/`.
4. Start the server. Data is stored per world in `<world>/nationeconomy/*.json`:
   `balances.json`, `players.json`, `shop.json`, `nations.json` — all editable while the server is off.

## Building

Requires **Java 21** and internet access (Gradle downloads Minecraft & mappings).

```bash
# The wrapper jar is not committed; bootstrap it once with either:
gradle wrapper            # if you have a local Gradle install
# or download gradle-wrapper.jar from
# https://raw.githubusercontent.com/gradle/gradle/v8.14.3/gradle/wrapper/gradle-wrapper.jar
# into gradle/wrapper/

./gradlew build           # output: build/libs/nation-economy-1.0.0.jar
```

Pinned versions (see `gradle.properties`): Minecraft `1.21.11`, Yarn `1.21.11+build.6`,
Loader `0.18.4`, Fabric API `0.141.6+1.21.11`, Loom `1.13.20`, Gradle `8.14.3`.

## How it works (for developers)

* **Server-side only**, registered via `DedicatedServerModInitializer` — no mixins, no client classes.
* Shop GUIs are vanilla `GenericContainerScreenHandler`s (9x4 main menu / 9x6 category pages) backed by
  `SimpleInventory`; `onSlotClick` is overridden so every click is *cancelled* and instead triggers
  buy/sell/navigation logic — ghost items never occur because the server never mutates the menu slots.
* Protection hooks into Fabric API interaction events (`AttackBlockCallback`, `UseBlockCallback`,
  `UseItemCallback` with a raycast for buckets/boats, `UseEntityCallback`, `AttackEntityCallback`)
  and maps each interaction to a `NationPermission` (`BREAK`, `PLACE`, `CHEST`, `USE`).
* Nation identity in tab/nametag uses scoreboard teams (prefix `[Name] ` + nearest vanilla color);
  chat is reformatted through `ServerMessageEvents.ALLOW_CHAT_MESSAGE`, which interpolates the
  nation tag with the full RGB color.
* Border titles use the title packets on a 4-tick movement check.

### Notes & limits

* Vanilla chest GUIs come in multiples of 9 — "30 slots" is implemented as a 9x4 window where
  slots 0–29 are category slots and the last 6 slots are utility buttons (balance, sell-all, close).
* Explosion/piston/fire/hopper automation across claim borders is not blocked (by design, like most
  land plugins); player interactions are fully protected.
* Land claims are per-world (Overworld, Nether and End claims are independent).
