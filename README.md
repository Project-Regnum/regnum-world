# Regnum World (Fabric 1.21.1)

A comprehensive Minecraft mod designed to establish regional economic balance, provincial specialization, and realistic geographic trade dynamics.

---

## 🌟 Key Features

### 🌾 Regional Agriculture & Crop Growth
- Crops thrive in fertile, temperate climates while suffering steep debuffs in harsh regions.
- In snowy plains, ice spikes, and arid deserts, standard cereals and vegetables barely grow (`0.0x` - `0.1x`).
- Specialized indigenous crops receive regional bonuses (e.g., sweet berries in cold tundras, cacti in deserts, cocoa and melons in jungles).

### ⛏️ Underground Farming Restrictions
- Eliminates underground automated farms: crops grown underground, in mines, or deep caves without direct sky access **will not grow** (`0.0x`).
- Configurable maximum Y-level (`underground_max_y`) and sky-access check (`block_cave_crop_growth_without_sky`).

### 🦴 Bonemeal Balancing & Abuse Prevention
- Base bonemeal growth boost is reduced by **50%** to prevent rapid farming exploits.
- In underground mines and dark caves, bonemeal **does not work at all**.
- In harsh biomes, bonemeal efficiency scales proportionally with the local climate multiplier.

### 🐄 Regional Animal Husbandry
- In fertile pasturelands (Plains, Meadows, Forests, Savannas), livestock offspring (calves, lambs, piglets, chicks) mature and regain breeding readiness **1.8x - 2.0x faster**.
- In frozen tundras, mountains, and arid deserts, breeding and livestock maturation takes **2 to 3 times longer** (`0.3x` - `0.5x`).

### 🏔️ Mountain Altitude Debuff
- Mountain peaks impose severe crop penalties and an altitude debuff representing thin mountain air above Y=95 (`0.5x` modifier).

### 🎣 Regional Fishing Speed
- Dynamic bite rates reflecting aquatic ecology:
  - Rivers, warm oceans, and swamps boast increased fishing speeds (`1.15x` - `1.50x`);
  - Deserts, frozen rivers, and polar oceans feature significantly slower bite rates (`0.2x` - `0.4x`).

### 💎 Geological Ore Balance
- Promotes inter-regional commerce and mining expeditions by concentrating ore generation:
  - **Diamonds**: Exceptionally rare globally (`0.10x` global rate, down to `0.05x` in snowy peaks and deserts);
  - **Gold & Lapis**: Abundant in Badlands/Mesa (`3.5x` gold, `2.6x` lapis) and Deserts;
  - **Emeralds & Iron**: Centered in Mountain Peaks (`3.5x` - `4.0x` emeralds, `2.2x` iron);
  - **Bog Iron**: Swamps yield rich surface and sub-surface iron (`2.5x`);
  - **Coal**: Ancient forested basins yield rich coal deposits (`2.4x` - `2.6x`).

### 🐺 Regional Mob Spawning
- Temperate regions sustain rich wildlife populations (`1.8x` - `2.5x` animal spawns).
- Arid and polar wastes yield scarce wildlife (`0.15x` - `0.2x`), populated primarily by climate-hardened hostile creatures (husks, strays, polar bears).

### 🌍 Terra World Generator Compatibility
- **Full out-of-the-box integration** with the [Terra](https://github.com/PolyhedralDev/Terra) world generation platform (`terra:overworld/overworld`).
- Dynamic registry injection enables the Terra world preset in world creation seamlessly.
- **Smart Climate Archetype Resolution**: Over 100+ Terra custom biomes (`tundra_hills`, `rainforest`, `desert_spikes`, `autumnal_forest`, `active_volcano`, etc.) automatically inherit balanced regional profiles matching their thematic climate.
- Direct per-biome overrides supported in the configuration file using either vanilla IDs (`minecraft:forest`) or Terra IDs (`terra:overworld/overworld/tundra_hills`).

### 🌐 Multilingual Support
- Fully localized in **English (`en_us`)**, **Ukrainian (`uk_ua`)**, and **Russian (`ru_ru`)**.

---

## ⚙️ Configuration (`config/regnum_world.json`)

All multipliers, biome tables, road blocks, and thresholds are fully customizable:

```json
{
  "bonemeal_efficiency": 0.50,
  "block_bonemeal_underground": true,

  "block_underground_crop_growth": true,
  "underground_max_y": 62,
  "block_cave_crop_growth_without_sky": true,

  "mountain_altitude_debuff_enabled": true,
  "mountain_altitude_start_y": 95,
  "mountain_altitude_multiplier": 0.5,

  "global_ore_multipliers": {
    "diamond": 0.10
  },
  "safe_extra_ore_worldgen_enabled": true
}
```

---

## 🤝 Mod Integration & Developer API (`RegnumWorldApi`)

Regnum World exposes a clean, thread-safe public API (`zooller.regnumworld.api.RegnumWorldApi`) for seamless compatibility with companion mods like **Regnum Agriculture**:

- **Query Regional Multipliers**:
  - `RegnumWorldApi.getRegionalCropMultiplier(biomeId, blockId)`: retrieves the configured multiplier for a crop in a biome.
  - `RegnumWorldApi.getFinalCropMultiplier(level, pos, state)`: evaluates full multiplier including altitude debuff and underground restriction.
  - `RegnumWorldApi.isUndergroundGrowthBlocked(level, pos)`: returns `true` if underground/cave growth restrictions apply.
- **Growth Controller Negotiation**:
  - `RegnumWorldApi.registerGrowthController(ExternalGrowthController)`: allows companion mods (such as Regnum Agriculture) to intercept random growth ticks, apply combined seasonal & regional formulas, and decide whether to handle, cancel, or pass the growth event without tick collision or race conditions.

---

## ⚖️ Plant Growth Adapters & Probabilistic Scaling

Rather than relying on re-entrant recursive ticks (which cause race conditions and stack overflows), Regnum World uses specialized block growth adapters (`CactusGrowthAdapter`, `BambooGrowthAdapter`, `SugarCaneGrowthAdapter`, `CropGrowthAdapter`, `StemGrowthAdapter`, `SweetBerryBushGrowthAdapter`, `CocoaGrowthAdapter`, `NetherWartGrowthAdapter`, `SaplingGrowthAdapter`):
- **Debuffed growth ($M < 1.0$)**: probabilistic tick cancellation proportionally extending crop lifecycles.
- **Accelerated growth ($M > 1.0$)**: deterministic stage advancement scaling expected progress per tick without extra recursive game ticks. Discrete 1-block plants (e.g. bamboo) cap single-tick probability at 100% (3x speedup per tick).

---

## 🎮 In-Game Commands

* `/regnum info` (or `/rw info`): Displays an interactive breakdown of the player's current region, including:
  - Localized biome name and identifier;
  - Underground / cave blocking status;
  - Base crop growth & regional specialty crop bonuses;
  - Vegetation growth multipliers (trees, flowers, grass);
  - Mineral ore spawn rates;
  - Animal & monster spawn modifiers;
  - Livestock maturation rate;
  - Fishing speed.
* `/regnum reload` (or `/rw reload`): Reloads `config/regnum_world.json` on the server dynamically without needing a server restart (requires OP level 2).

---

## 🛠️ Building & Installation

### Requirements
- **Java 21** or newer
- **Fabric Loader 0.16.0+**
- **Fabric API**
- **Minecraft 1.21.1**

### Build from Source
```bash
# Clone the repository
git clone https://github.com/zooller/regnum-world.git
cd regnum-world

# Build with Gradle wrapper
./gradlew build --no-configuration-cache
```

The compiled mod JAR will be located at:
`build/libs/regnum-world-1.0.0.jar`

## Static dungeons (operators)

`/regnum dungeon generate <mine|catacombs|bastion>` searches nearby; append `here` for the current chunk origin, or `force` to bypass terrain/spacing preferences. Explicit `<x> <z> [force]`, `preview` and `validate` remain available. Force and preview use clickable **CONFIRM / CANCEL** buttons or `/regnum dungeon confirm` and `/regnum dungeon cancel`, bound to the operator and target for 60 seconds. Buttons carry a six-character nonce so an old button cannot confirm a newer action. Claims, overlap, recognizable construction and intersecting structures remain protected. Ordinary stone/dirt blocks have no player-origin metadata, so inspect force previews before confirming.

`list` shows short IDs with autocomplete for `info <id>`, `tp <id>`, `reset <id>`, `recover <id>` and `remove <id>`. Removal keeps physical blocks because no original snapshot exists; it removes territory/Atlas state and encounter ownership, and preserves earned pending rewards until delivered. Creative operators can edit dungeon blocks directly. Existing layouts are retained until explicitly recovered; new dungeons use the themed v3 geometry. Recovery upgrades an existing layout after confirmation. An administrative reset starts a fresh encounter cycle immediately; automatic recovery waits for the configured boss cooldown.

Clear encounter rooms and the central elite to awaken the boss. Cleared rooms stay cleared until the dungeon cycle resets; inactive owned mobs are cleaned up. Natural hostiles are suppressed only inside registered regions. Seven registered dungeon materials/trophies have original icons, tier loot tables, crafting exchanges and tooltip recipes, without requiring Professions. Config includes encounter cap, inactive cleanup, room unlock count and bounded placement settings. New configurations allow 24 blocks of elevation variation; existing configured limits are retained. Placement anchors below both entrances, while force bypasses slope, substrate, water and spacing preferences and keeps claims, construction, overlap, build limits and the vertical write budget as hard restrictions.

V3 adds supported mine corridors and a haulage siding, crypt arches and a sunken reliquary, and bastion wall walks, ladders, a ruined gallery and courtyard cover. Eight layout seeds per theme are checked for routes to all nine encounter anchors. Forced high entrances have continuous escape ladders, and their full vertical extent persists in protection and Atlas bounds. Encounter spawning checks solid footing and entity clearance; closer triggers pace room activation. Each elite room has one champion with guards. Champions and bosses warn with a fixed particle ring two seconds before their area strike.

`resourceCounts` controls tier materials (boss base amounts; one eighth per room), and `rareChancePercent` controls room crystal drops. Bosses also grant trophies, crystals and tier loot. Configurable reward amounts are snapshotted when earned so pending deliveries cannot reroll after config changes. Economy deliveries retain its authentication, inventory and journal validation and receipt deduplication. Without Economy, rewards are items only and use an at-most-once fallback; a crash between consuming eligibility and saving the inventory can lose that delivery.

Default searches inspect at most 16 candidates and 96 explicit chunk requests, one per tick; vanilla generation can request neighbors internally.
