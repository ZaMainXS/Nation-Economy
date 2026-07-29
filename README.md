<div align="center">

<img src="src/main/resources/assets/nationeconomy/icon.png" width="128" alt="Nation & Economy icon"/>

# Nation & Economy

**A Fabric 1.21.11 server-side mod that combines an EconomyShopGUI-style shop with a full nations / land-claim / raiding / combat system.**

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
* `/sell [item] [amount]`, `/sellall`, `/sellhelditem`.
* `/balance` (`/bal`), `/pay <player> <amount>`, `/baltop`.

#### 🔐 Shop security / anti-dupe

* **Nothing can ever leave or enter a menu:** every click action is cancelled server-side — no dragging, shift-clicking, Q-dropping, number-key swapping or pick-block cloning.
* Trades never use the *displayed* item stacks: prices and stock are always read from the live server data at click time, bought items are created from scratch, and balance is checked + withdrawn atomically on the server thread before items are handed out.
* Combat-tagged players can't buy (see below).

### ⚙️ Shop administration (op level 2+)

* **`/shopadmin`** opens the full **admin GUI**:
  * create categories (anvil text input, `&`-codes & `&#RRGGBB` hex supported),
  * manage any category: its items, its **slot in the 30-slot menu**, its name, its icon,
  * manage any item: exact **buy/sell prices** (±1/±10 clicks or type an exact number), buyable/sellable toggles, its **position inside the category**, removal,
  * *Push Changes* refreshes every open shop GUI, and a built-in *How the shop works* page.
* `/economycategory create <slot 0-29> <name>` — quick category creation (icon can be set in the GUI).
* `/economyhanditem add <buy> <sell> [category]` — adds the item you're **holding** (falls back to an auto-created `misc` category).
* `/sreload confirm` — reloads `shop.json` and **refreshes every open shop GUI** (asked to confirm first).
* `/shopadmin item add/remove`, `category delete/list`, classic command variants — all still available.
* `/eco give|take|set <player> <amount>` — balance control (works offline).

### 🏴 Nations & Land

* `/nation create <name>` — found a nation; **one nation per player** until it's destroyed. Names pass a **slur/profanity filter** (n-word/hard-r included, leetspeak-normalized).
* Creating a nation spawns the **Nation Core** where you stand: an invulnerable glowing **nether star orb** floating above the ground with the nation's name over it.
* `/claimland` → golden shovel, left/right-click corners, `/claimland confirm`. **1,000,000 blocks** base allowance (each selection max **1000×1000** — split bigger areas); `/nation upgrade` = **1 netherite ingot → +100 blocks**. Claims can't overlap.
* Protection: uninvited players can't place blocks, open chests or use anything in your land.
* `/nation allow access <player> <break|place|chest|use|all>` & `/nation deny access`, `/nation trusted`.
* `/nation banish <player>` (kick + ban, `/nation unbanish` lifts), `/nation leave`, `/nation members`, `/nation list`, `/nation info`.
* `/nation disband` asks for confirmation first: **`/nation disband confirm`** then announces it server-wide.
* `/nation color <color>` — the 16 vanilla colors, ~20 extra named colors, or **any `#RRGGBB` hex** — used in **tab list, above-head name, chat and the map**.
* Nation prefix in **chat** (`[Nation] <Player> message`), big title banner when entering foreign land showing the nation + how much it has claimed.
* `/nation map [radius]` — chat territory map: colored square per chunk, hover for nation + claimed blocks, legend totals.
* Guide to everything in-game: **`/nationalexplain`** (paged GUI manual).
* **Nation homes:** leaders set up to **3 homes** (`/nation sethome [1-3]`, `/nation delhome <1-3>`, `/nation homes`), any member teleports with **`/nation home [1-3]`** — but not in combat.

### ⚔️ Combat system

* Damaging another player (or getting hit) applies a **30-second combat tag** to both.
* While tagged: **no buying from the shop**, **no `/nation home`**, and the action bar shows the countdown.
* **Logging out while tagged kills you and drops your items** (announced in chat).
* Tags are **persistent**: if your game crashes or you lag out, the timer keeps running while you're offline and resumes when you rejoin. (Note: a graceful quit and a network lag-out can't be told apart server-side, so both are penalized; server restarts never kill anyone.)

### 🏴‍☠️ Raiding & the Nation Core

* Outsiders **can break into claimed land** — but **every single block takes 1,000 hits** (progress shown, counters persist through restarts, unbreakable blocks stay unbreakable).
* The **Nation Core** can be damaged by hitting the orb: **10,000 hits** to crack it. Progress shows on the core's name tag; the top attacker is tracked.
* When a core falls the **nation is destroyed**: a server-wide announcement says *who* defeated *which nation* (and their nation, if any), the land becomes wilderness, and a **nether star trophy** drops.
* **Core Healer** — a craftable nether star that restores **500 core hits** when you right-click your own core with it:

```
Ancient Debris   | Diamond Block   | Ancient Debris
Netherite Upgrade| Diamond Block   | Netherite Upgrade
Gold Block       | Gold Block      | Gold Block
```

(Recipe ships inside the mod — `data/nationeconomy/recipe/core_healer.json`.)

---

## Commands

### Player commands

| Command | Description |
| --- | --- |
| `/shop` | Open the shop GUI |
| `/sell [item] [amount]` | Sell held item, or a specific item/amount |
| `/sellall` | Sell everything sellable |
| `/sellhelditem` | Sell the stack in your hand |
| `/balance`, `/bal` | Show your balance |
| `/pay <player> <amount>` | Pay another player |
| `/baltop` | Richest players |
| `/nationalexplain` | **Guide GUI explaining the whole mod** |
| `/nation create/join/leave` | Nation membership |
| `/nation disband` → `confirm` | Delete your nation *(leader, with confirm + announcement)* |
| `/nation banish/unbanish <player>` | Ban management *(leader)* |
| `/nation allow/deny access <player> <perm>` | Land permissions: `break/place/chest/use/all` *(leader)* |
| `/nation trusted` | See access grants |
| `/nation color <color>` | Nation color — name or `#RRGGBB` *(leader)* |
| `/nation colors` | List named colors |
| `/nation info [name]`, `/nation list`, `/nation members` | Info |
| `/nation map [radius]` | Territory map |
| `/nation sethome [1-3]` | Set a nation home *(leader, max 3)* |
| `/nation home [1-3]`, `/nation homes` | Teleport to a nation home *(blocked in combat)* |
| `/nation delhome <1-3>` | Delete a nation home *(leader)* |
| `/nation upgrade` | 1 netherite ingot → +100 claim blocks |
| `/nation unclaim` | Unclaim land *(leader)* |
| `/claimland [confirm/cancel/info]` | Claim shovel & claiming |

### Admin commands (op level 2+)

| Command | Description |
| --- | --- |
| `/shopadmin` | **Full admin GUI**: categories, items, prices, slots, icons, names, push changes, shop help |
| `/shopadmin category/item ...` | Command variants of the same actions |
| `/economycategory create <slot> <name>` | Quick category creation |
| `/economycategory delete <category>` | Delete a category |
| `/economyhanditem add <buy> <sell> [category]` | Add the held item to the shop |
| `/sreload confirm` | Reload shop data + refresh open shop GUIs |
| `/eco give/take/set <player> <amount>` | Manage balances |

Ops (permission level 3+) bypass land protection.

---

## Installation

1. Run a **Fabric server for Minecraft 1.21.11** (Loader ≥ 0.19.3 recommended).
2. Put **Fabric API ≥ 0.141.x** in `mods/`.
3. Build this mod (below) and drop `nation-economy-1.0.0.jar` into `mods/`.
4. Start the server. Data lives per world in `<world>/nationeconomy/*.json`
   (`balances.json`, `players.json`, `shop.json`, `nations.json`, `raids.json`, `combattags.json`).

## Building

Requires **Java 21** and internet access.

```bash
# The wrapper jar is not committed; bootstrap it once with either:
gradle wrapper            # if you have a local Gradle install
# or download gradle-wrapper.jar from
# https://raw.githubusercontent.com/gradle/gradle/v8.14.3/gradle/wrapper/gradle-wrapper.jar
# into gradle/wrapper/

./gradlew build           # output: build/libs/nation-economy-1.0.0.jar
```

Pinned versions (see `gradle.properties`): Minecraft `1.21.11`, Loader `0.19.3`,
Fabric API `0.141.6+1.21.11`, Loom `1.17`, Gradle `8.14.3`.

The mod is written against **Mojang's official mappings** (`loom.officialMojangMappings()`)
— the standard mapping set for 1.21.11 modding, used by the fabric-example-mod template and
Fabric API itself for this version. (Older chat logs may mention Yarn — that was swapped out:
Yarn's 1.21.11 mappings have large gaps such as unmapped registry constants, while official
mappings are complete.)

Every vanilla API call in the codebase has been cross-checked against the actual
`0.141.6+1.21.11` Fabric API and NeoForge `1.21.11` sources (both compile against vanilla
1.21.11). Notable 1.21.x API shifts this code accounts for:

* `ResourceLocation` is now `Identifier` (`fromNamespaceAndPath` / `tryParse`).
* Permission levels are objects: commands use `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`,
  player checks use `Commands.LEVEL_ADMINS.check(player.permissions())`
  (`MinecraftServer#getProfilePermissions` now takes a `NameAndId` and returns a
  `LevelBasedPermissionSet`, not an int).
* `LivingEntity#hurt` is `(DamageSource, float)`; teleports use
  `new TeleportTransition(world, pos, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING)`.
* `authlib`'s `GameProfile` is a record (`name()` / `id()`).

## How it works (for developers)

* **Server-side only** (`DedicatedServerModInitializer`) — no mixins, no client classes.
* GUIs are vanilla screen handlers (`ChestMenu` 9x3/9x4/9x6 chest GUIs and an
  `AnvilMenu` text prompt). `AbstractContainerMenu#clicked` is overridden everywhere so clicks
  are cancel-only and drive the logic — this is also the anti-dupe backbone.
* Nation identity uses scoreboard teams (tab/nametag prefix) + `ServerMessageEvents.ALLOW_CHAT_MESSAGE`
  for chat; territory titles use the title packets on a 4-tick movement check.
* The core is an invisible, invulnerable, small armor stand holding a nether star on its head
  (only stable, public entity APIs are used).
* Combat tags use `ServerLivingEntityEvents.ALLOW_DAMAGE` (covers melee *and* projectiles); the
  logout-kill happens in `ServerPlayConnectionEvents.DISCONNECT` via the damage system so items and
  the death message behave exactly like a normal death.
* Text input inside the admin GUI uses the vanilla anvil rename field — typed text is read from
  the result slot, with all clicks cancelled.

### Notes & limits

* Vanilla chest GUIs come in multiples of 9 — "30 slots" is a 9x4 window where slots 0–29 are
  category slots and the last 6 are utility buttons.
* Explosion/piston/fire/hopper automation across claim borders is not blocked (player actions are).
* A graceful quit vs a network lag-out cannot be distinguished on the server — combat-logging
  kills apply to both (timer persistence softens lag-outs; server restarts never kill).
* The crafted Core Healer recipe relies on the mod's bundled datapack JSON; if your datapack
  loader complains, the item can also be produced by renaming a nether star to `Core Healer`
  in an anvil (any nether star with that exact name works as a healer).
