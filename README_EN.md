# SimuDelight — NSUK × Farmer's Delight Farmland Bridge

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-orange)
![NeoForge 21.1.249](https://img.shields.io/badge/NeoForge-21.1.249-green)

> ⚠️ **UNOFFICIAL ADD-ON — READ FIRST**
>
> SimuDelight is an unofficial third-party extension.
>
> Issues with this mod belong in this repository — please do not report them to the upstream authors.
> Use at your own risk.

**SimuDelight** lets the citizens you hire in [New:Sim-U-Kraft](https://github.com/New-Sim-U-Kraft/New-Simukraft-1.21.1) (NSUK) city-sim farmland boxes grow the whole [Farmer's Delight](https://github.com/vectorwing/FarmersDelight) family of crops — field crops, paddy rice, two-stage corn, watertop cranberries, mushroom colonies, rich-soil crops and trellis tomatoes.

> NSUK's farmland crop table is a hard-coded vanilla enum (6 crops only) with no third-party extension API. This mod bridges a custom "extended crop registry" into NSUK's farmland data and farming services via Mixin, re-implementing the plant/harvest/replant workflow with generic crop semantics. The vanilla crop logic is untouched.

---

## ✨ Features

- **Six planting semantics** covering different growth habits:
  - `full` — standard field CropBlocks (cabbage, onion, bell pepper…)
  - `paddy` — rice paddies: the farmer floods the cell with water, plants rice in it, and the plant bears panicles above when mature
  - `tall` — two-stage crops (corn): lower stage grows by itself, upper stage bears the ears the farmer harvests
  - `watertop` — water-surface crops (cranberries): the farmer turns the soil below into water, plants on the surface, and picks without destroying the plant
  - `soil` — special-medium crops (cave carrots): the farmer first consumes materials to lay rich soil
  - `colony` — colony crops (mushrooms): the farmer lays rich soil, grows a colony and harvests 3 while leaving 1 to regrow
- **Data-driven crop list**: `config/simudelight/crops.json` — add or remove crops by editing the file, no recompile needed
- **Auto-detection**: install any Farmer's Delight add-on and its crops show up in the farmland box; entries for missing mods are skipped automatically
- **Trellis tomatoes**: the farmer erects rope above the plant; tomatoes climb, ripen and are harvested automatically, then the rope is restored
- **Zero intrusion**: extended crops live in their own registry; the original 6 crops and NSUK's own logic are unaffected

## 🌾 Built-in crops (30, grouped by source mod)

| mod | crops |
|---|---|
| Farmer's Delight | Cabbage, Onion, Tomato (trellis), Rice (paddy) |
| Expanded Delight | Asparagus, Chili Pepper, Peanut, Sweet Potato, Cranberry (watertop) |
| Miner's Delight | Cave Carrot (rich soil) |
| Veggies Delight | Bell Pepper, Broccoli, Cauliflower, Garlic, Sweet Potato, Turnip, Zucchini |
| Rustic Delight | Bell Peppers ×3, Coffee, Cotton |
| Ube's Delight | Garlic, Ginger, Ube |
| Pineapple Delight | Pineapple |
| Cultural Delights | Cucumber, Eggplant, Corn (two-stage) |
| Mexican Delight | Corn (two-stage) |

Install the matching add-on and its crops appear in the farmland box automatically (put their seeds in the chest).

## 📦 Dependencies

| mod | version | type |
|---|---|---|
| Minecraft | 1.21.1 | required |
| NeoForge | ≥ 21.1.0 | required |
| New:Sim-U-Kraft | ≥ 2.0.0 (developed against 2.2.0) | required |
| LowDragLib2 (LDLib) | bundled with NSUK | satisfied by NSUK |
| Farmer's Delight | ≥ 1.0.0 (developed against 1.3.4) | optional (crop source) |
| Any add-on above | current | optional (one more crop batch each) |

Without Farmer's Delight the mod degrades to an empty extension and does not affect gameplay.

## 🚀 Installation

1. Install [NeoForge 21.1](https://neoforged.net/) and Java 21
2. Install NSUK 2.2.0 and LDLib
3. Drop `simudelight-<version>.jar` into your `mods/` folder
4. (Optional) Install Farmer's Delight and add-ons to unlock their crops
5. Launch, place a farmland box, hire farmers, open the box UI and pick an extended crop

## ⚙️ Configuration

On first launch `config/simudelight/crops.json` is generated. Each entry:

```jsonc
{
  "id": "fd_rice",                    // stable id (do not clash with NSUK vanilla crops)
  "seedItem": "farmersdelight:rice",  // seed to place in the material chest
  "plantBlock": "farmersdelight:rice",// block to plant
  "kind": "paddy",                    // full / paddy / tall / watertop / soil / colony
  "topBlock": "farmersdelight:rice_panicles", // upper block for kind=paddy/tall
  "soilBlock": "farmersdelight:rich_soil",    // medium block for kind=soil/colony
  "soilItem": "farmersdelight:rich_soil",     // item consumed to lay the medium
  "ropes": false                      // trellis crop (tomato)
}
```

Remove an entry to disable that crop; on a corrupt file the mod falls back to the built-in list without overwriting your file.

## 📜 License & Credits

- This mod is released under the **GNU GPL v3** (full text in [LICENSE](LICENSE); `META-INF/LICENSE-GPL-3.0.txt` is shipped inside the jar). Copyright © 2026 Mengtiansoul.
- It is a compatibility/derivative extension of **New:Sim-U-Kraft** — © NSUK Studio, licensed under **GPL-3.0** ([official repository](https://github.com/New-Sim-U-Kraft/New-Simukraft-1.21.1)).
- Crops are referenced by registry id only from **Farmer's Delight** (© vectorwing, [MIT](https://github.com/vectorwing/FarmersDelight)) and its add-ons. **No Farmer's Delight code or assets are included or bundled.**
- The client UI is built on NSUK's theme API (LDLib2, LGPL-3.0).
- Build scaffolding: [NeoForge MDK](https://github.com/NeoForged/MDK) (template files MIT © NeoForged project).

## 🔨 Building from source

Requires JDK 21:

```bash
./gradlew build
# output: build/libs/simudelight-<version>.jar
```

## 🐛 Bug reports

Please include: `logs/latest.log`, your `config/simudelight/crops.json`, and the NSUK/FD versions.
