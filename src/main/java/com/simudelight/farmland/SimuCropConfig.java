/*
 * SimuDelight - a New:Sim-U-Kraft (NSUK) x Farmer's Delight farmland bridge
 * Copyright (C) 2026 Mengtiansoul
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Third-party notes:
 *  - Links to / is a derivative work of New:Sim-U-Kraft (GPL-3.0, (c) NSUK Studio).
 *  - References Farmer's Delight crops by registry id only (MIT, (c) vectorwing);
 *    no Farmer's Delight code or assets are included.
 */
package com.simudelight.farmland;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.simudelight.SimuDelight;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扩展作物清单的数据驱动加载器。
 * <p>
 * 作物清单从 {@code config/simudelight/crops.json} 读取(客户端与服务端共用同一文件)。
 * 首次启动自动写出内置默认模板;文件损坏时回退默认清单但绝不覆写用户文件;
 * 旧版本文件自动按内置默认升级(补全新作物、补齐新字段,保留用户自定义条目与删除意图)。
 * <p>
 * 每条作物定义:
 * <pre>
 *   {
 *     "id": "fd_rice",                             // 存档标识(勿与原版作物 id 重复)
 *     "seedItem": "farmersdelight:rice",           // 放进材料箱的种子/种植材料
 *     "plantBlock": "farmersdelight:rice",         // 种下的作物方块(下层)
 *     "kind": "full",                              // full/soil/tall/watertop/colony
 *     "topBlock": "farmersdelight:rice_panicles",  // kind=tall:下层成熟后自长的上层(稻穗/玉米顶),农夫收它
 *     "soilBlock": "...", "soilItem": "...",       // kind=soil/colony:介质方块/物品
 *     "ropes": false                               // 可选:攀绳作物(农夫乐事番茄)
 *   }
 * </pre>
 * kind 语义(农夫在农田盒的工作流):
 * <ul>
 *   <li>full  —— 满铺 CropBlock:翻耕地→播种→成熟破坏收获→补种(默认)</li>
 *   <li>soil  —— CropBlock 但需专用介质:农夫铺 soilBlock 后种(如洞穴胡萝卜/蘑菇介质沃土)</li>
 *   <li>tall  —— 两段作物:种耕地(水稻/玉米),下层成熟后自长 topBlock 上层,农夫只收上层(不毁株)</li>
 *   <li>watertop —— 水面作物:农夫把格下土壤灌成水后种,成熟采摘留株</li>
 *   <li>colony —— 菌落:农夫铺 soilBlock 后种菌落,成熟采摘留株(age 回 0)</li>
 * </ul>
 */
public final class SimuCropConfig {

    /** 清单格式版本:低于此版本的旧文件会被内置默认升级覆盖。 */
    private static final int FORMAT_VERSION = 5;

    /** 已从内置清单移除的作物 id(升级时从旧配置文件里一并剔除)。 */
    private static final java.util.Set<String> REMOVED_BUILTINS = java.util.Set.of(
            "fd_brown_mushroom", // 蘑菇菌落(种植已取消)
            "fd_red_mushroom"
    );

    /** 数据驱动文件的默认内容(首启写出;也是文件缺失/损坏时的回退清单)。 */
    private static final String DEFAULT_JSON = """
            {
              "_comment": "SimuDelight 扩展作物清单(数据驱动,格式版本 5)。id=存档标识(勿与原版作物 wheat/carrots/potatoes/beetroots/melon/pumpkin 重复);seedItem=放进材料箱的种子;plantBlock=种下的下层作物方块;kind=种植语义(full 满铺 CropBlock / soil 专用介质 / tall 两段作物(下层+上层如玉米) / paddy 水田水稻 / watertop 水面作物 / colony 菌落);topBlock=kind=tall 时下层成熟后自长的上层方块(农夫收它);soilBlock+soilItem=kind=soil/colony 的介质;ropes=true=攀绳作物(番茄)。删除条目=禁用;目标 mod 未安装的条目自动跳过。",
              "crops": [
                { "id": "fd_cabbage",        "seedItem": "farmersdelight:cabbage_seeds",   "plantBlock": "farmersdelight:cabbages",  "kind": "full" },
                { "id": "fd_onion",          "seedItem": "farmersdelight:onion",           "plantBlock": "farmersdelight:onions",    "kind": "full" },
                { "id": "fd_tomato",         "seedItem": "farmersdelight:tomato_seeds",    "plantBlock": "farmersdelight:tomatoes",  "kind": "full", "ropes": true },
                { "id": "fd_rice",           "seedItem": "farmersdelight:rice",            "plantBlock": "farmersdelight:rice",      "kind": "paddy", "topBlock": "farmersdelight:rice_panicles" },
                { "id": "ed_asparagus",      "seedItem": "expandeddelight:asparagus_seeds",    "plantBlock": "expandeddelight:asparagus_crop",  "kind": "full" },
                { "id": "ed_chili_pepper",   "seedItem": "expandeddelight:chili_pepper_seeds", "plantBlock": "expandeddelight:chili_pepper_crop", "kind": "full" },
                { "id": "ed_peanut",         "seedItem": "expandeddelight:peanut",            "plantBlock": "expandeddelight:peanut_crop",      "kind": "full" },
                { "id": "ed_sweet_potato",   "seedItem": "expandeddelight:sweet_potato",      "plantBlock": "expandeddelight:sweet_potato_crop", "kind": "full" },
                { "id": "ed_cranberry",      "seedItem": "expandeddelight:cranberries",       "plantBlock": "expandeddelight:cranberry_plant",   "kind": "watertop" },
                { "id": "md_cave_carrot",    "seedItem": "minersdelight:cave_carrot",         "plantBlock": "minersdelight:cave_carrots",       "kind": "soil", "soilBlock": "farmersdelight:rich_soil", "soilItem": "farmersdelight:rich_soil" },
                { "id": "vg_bellpepper",     "seedItem": "veggiesdelight:bellpepper_seeds",  "plantBlock": "veggiesdelight:bellpepper_crop",  "kind": "full" },
                { "id": "vg_broccoli",       "seedItem": "veggiesdelight:broccoli_seeds",    "plantBlock": "veggiesdelight:broccoli_crop",    "kind": "full" },
                { "id": "vg_cauliflower",    "seedItem": "veggiesdelight:cauliflower_seeds", "plantBlock": "veggiesdelight:cauliflower_crop", "kind": "full" },
                { "id": "vg_garlic",         "seedItem": "veggiesdelight:garlic_clove",      "plantBlock": "veggiesdelight:garlic_crop",      "kind": "full" },
                { "id": "vg_sweet_potato",   "seedItem": "veggiesdelight:sweet_potato",      "plantBlock": "veggiesdelight:sweet_potato_crop", "kind": "full" },
                { "id": "vg_turnip",         "seedItem": "veggiesdelight:turnip_seeds",      "plantBlock": "veggiesdelight:turnip_crop",      "kind": "full" },
                { "id": "vg_zucchini",       "seedItem": "veggiesdelight:zucchini_seeds",    "plantBlock": "veggiesdelight:zucchini_crop",    "kind": "full" },
                { "id": "ru_bell_pepper",    "seedItem": "rusticdelight:bell_pepper_seeds",     "plantBlock": "rusticdelight:bell_peppers",     "kind": "full" },
                { "id": "ru_dark_bell_pepper", "seedItem": "rusticdelight:dark_bell_pepper_seeds", "plantBlock": "rusticdelight:dark_bell_peppers", "kind": "full" },
                { "id": "ru_pale_bell_pepper", "seedItem": "rusticdelight:pale_bell_pepper_seeds", "plantBlock": "rusticdelight:pale_bell_peppers", "kind": "full" },
                { "id": "ru_coffee",         "seedItem": "rusticdelight:coffee_beans",       "plantBlock": "rusticdelight:coffee",           "kind": "full" },
                { "id": "ru_cotton",          "seedItem": "rusticdelight:cotton_seeds",      "plantBlock": "rusticdelight:cotton",           "kind": "full" },
                { "id": "ub_garlic",          "seedItem": "ubesdelight:garlic",              "plantBlock": "ubesdelight:garlic_crop",        "kind": "full" },
                { "id": "ub_ginger",          "seedItem": "ubesdelight:ginger",              "plantBlock": "ubesdelight:ginger_crop",        "kind": "full" },
                { "id": "ub_ube",             "seedItem": "ubesdelight:ube",                 "plantBlock": "ubesdelight:ube_crop",           "kind": "full" },
                { "id": "pp_pineapple",       "seedItem": "pineapple_delight:pineapple_crop", "plantBlock": "pineapple_delight:pineapple_crop", "kind": "full" },
                { "id": "cd_cucumber",        "seedItem": "culturaldelights:cucumber_seeds", "plantBlock": "culturaldelights:cucumbers",     "kind": "full" },
                { "id": "cd_eggplant",        "seedItem": "culturaldelights:eggplant_seeds", "plantBlock": "culturaldelights:eggplants",     "kind": "full" },
                { "id": "cd_corn",            "seedItem": "culturaldelights:corn_kernels",   "plantBlock": "culturaldelights:corn",          "kind": "tall", "topBlock": "culturaldelights:corn_upper" },
                { "id": "mx_corn",            "seedItem": "mexicansdelight:corn",            "plantBlock": "mexicansdelight:corn_crop",      "kind": "tall", "topBlock": "mexicansdelight:corn_crop_top" }
              ]
            }
            """;

    private SimuCropConfig() {
    }

    /** 配置文件绝对路径:config/simudelight/crops.json */
    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve("simudelight").resolve("crops.json");
    }

    /**
     * 读取作物定义列表(顺序即 UI/存档展示顺序)。
     * 文件不存在 → 写出默认模板;格式过旧 → 升级合并(补新条目/新字段);
     * 解析失败 → 回退默认清单(不覆写用户文件)。
     */
    public static List<CropDef> load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            writeDefault(path);
            return defaults();
        }
        Root root;
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            root = new Gson().fromJson(json, Root.class);
        } catch (IOException | JsonParseException e) {
            SimuDelight.LOGGER.error("[SimuDelight] 读取 crops.json 失败,使用默认清单(不覆写用户文件): {}", e.toString());
            return defaults();
        }
        if (root == null || root.crops == null) {
            SimuDelight.LOGGER.warn("[SimuDelight] crops.json 无有效内容,使用默认清单");
            return defaults();
        }
        List<CropDef> defs = new ArrayList<>();
        for (CropDef def : root.crops) {
            if (def != null && def.id != null && !def.id.isBlank()) {
                defs.add(def);
            }
        }
        // 旧版本文件:内置条目以当前默认模板覆盖(补齐 kind/topBlock/介质等演进字段),
        // 新增默认条目追加;已移除的内置条目剔除;用户自定义条目(不在默认模板内)保留。
        if (root.version < FORMAT_VERSION) {
            List<CropDef> defaults = defaults();
            Map<String, CropDef> defaultById = new LinkedHashMap<>();
            for (CropDef d : defaults) {
                defaultById.put(d.id, d);
            }
            List<CropDef> upgraded = new ArrayList<>(defs.size() + defaults.size());
            boolean changed = false;
            for (CropDef def : defs) {
                if (REMOVED_BUILTINS.contains(def.id)) {
                    changed = true; // 已取消的内置作物:剔除
                    continue;
                }
                CropDef fresh = defaultById.get(def.id);
                if (fresh != null) {
                    upgraded.add(fresh); // 内置条目:以默认模板覆盖
                    changed = true;
                } else {
                    upgraded.add(def); // 用户自定义:保留
                }
            }
            for (CropDef d : defaults) {
                boolean present = false;
                for (CropDef u : upgraded) {
                    if (u.id.equals(d.id)) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    upgraded.add(d);
                    changed = true;
                }
            }
            if (changed || root.version != FORMAT_VERSION) {
                root.version = FORMAT_VERSION;
                root.crops = upgraded;
                try {
                    Files.writeString(path, new Gson().newBuilder().setPrettyPrinting().create().toJson(root),
                            StandardCharsets.UTF_8);
                    SimuDelight.LOGGER.info("[SimuDelight] crops.json 已升级到格式 v{}", FORMAT_VERSION);
                } catch (IOException e) {
                    SimuDelight.LOGGER.warn("[SimuDelight] 无法写回升级版 crops.json: {}", e.toString());
                }
                defs = upgraded;
            }
        }
        if (defs.isEmpty()) {
            SimuDelight.LOGGER.warn("[SimuDelight] crops.json 全部条目无效,使用默认清单");
            return defaults();
        }
        SimuDelight.LOGGER.info("[SimuDelight] loaded {} crop definitions from {}", defs.size(), path);
        return defs;
    }

    private static void writeDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, DEFAULT_JSON, StandardCharsets.UTF_8);
            SimuDelight.LOGGER.info("[SimuDelight] 已生成默认作物清单 {}", path);
        } catch (IOException e) {
            SimuDelight.LOGGER.warn("[SimuDelight] 无法写出默认 crops.json: {}", e.toString());
        }
    }

    private static List<CropDef> defaults() {
        try {
            Root root = new Gson().fromJson(DEFAULT_JSON, Root.class);
            return root != null && root.crops != null ? root.crops : Collections.emptyList();
        } catch (JsonParseException e) {
            return Collections.emptyList();
        }
    }

    /** 一条作物定义(与 JSON 条目一一对应)。 */
    public static final class CropDef {
        private String id;
        private String seedItem;
        private String plantBlock;
        private String kind;
        private String topBlock;
        private String soilBlock;
        private String soilItem;
        private boolean ropes;

        public String id() {
            return id;
        }

        public String seedItem() {
            return seedItem;
        }

        public String plantBlock() {
            return plantBlock;
        }

        public String kind() {
            return kind == null || kind.isBlank() ? "full" : kind;
        }

        /** kind=tall/paddy 时的上层产物方块(下层成熟后自长,农夫收上层)。 */
        public String topBlock() {
            return topBlock;
        }

        public String soilBlock() {
            return soilBlock;
        }

        public String soilItem() {
            return soilItem;
        }

        public boolean ropes() {
            return ropes;
        }
    }

    /** JSON 根结构。 */
    private static final class Root {
        private String _comment;
        private int version;
        private List<CropDef> crops;
    }
}
