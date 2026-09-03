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

import com.simudelight.SimuDelight;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.material.WorkContainerService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 扩展作物注册表(数据驱动)。
 * <p>
 * 与 NSUK 的 FarmCrop(硬编码原版枚举)平行:这里登记农夫乐事家族联动作物,
 * 清单来自 {@code config/simudelight/crops.json}(见 {@link SimuCropConfig})。
 * 全部通过 {@link BuiltInRegistries} 运行时解析物品/方块 id——
 * 不直接 import 农夫乐事等 mod 的类,目标 mod 未安装时对应条目自然为空,
 * 不会产生任何类加载或崩溃风险。
 * <p>
 * 每种作物带 {@link CropKind} 种植语义(full/soil/paddy/watertop/colony),
 * 以及农夫乐事番茄的「绳上果实」支持(tomatoes_on_rope)。
 */
public final class SimuCropRegistry {

    /** 种植语义(决定农夫在农田盒里的种/收工作流)。 */
    public enum CropKind {
        /** 满铺 CropBlock:翻耕地 -> 播种 -> 成熟破坏收获 -> 补种。 */
        FULL,
        /** CropBlock 但需专用介质(沃土等):农夫铺 soilBlock 后种,其余同 full。 */
        SOIL,
        /** 水田水稻:农夫把作物格做成水田(格内灌水,下方土),水稻种进水里;成熟在上方结稻穗,收穗不毁株。 */
        PADDY,
        /** 旱地两段作物(玉米):种耕地,下层成熟后自长 topBlock 上层,农夫只收上层。 */
        TALL,
        /** 水面作物:农夫把格下土壤变为水,种在水面上(睡莲式),成熟采摘留株。 */
        WATERTOP,
        /** 菌落(蘑菇):农夫铺 soilBlock 后种菌落,成熟采摘留株(age 回 0)。 */
        COLONY
    }

    /** 解析后的可用扩展作物(按 id 索引,小写)。 */
    private static final Map<String, ResolvedCrop> RESOLVED = new LinkedHashMap<>();
    private static boolean attempted = false;
    /** 农夫乐事的 rope(绳):攀爬作物的支撑,运行时解析。 */
    private static Block ropeBlock;
    private static Item ropeItem;
    /** 农夫乐事的绳上果实(tomatoes_on_rope):番茄攀绳后结的果实层,运行时解析。 */
    private static Block ropeFruitBlock;

    private SimuCropRegistry() {
    }

    /** 判断 cropId 是否为扩展作物(不在 NSUK 原版 FarmCrop 枚举内即是)。 */
    public static boolean looksExtended(String cropId) {
        if (cropId == null || cropId.isBlank()) {
            return false;
        }
        return FarmCrop.fromId(cropId) == null;
    }

    /** 解析后的作物:种子、植株、上层产物、介质与种植语义。 */
    public record ResolvedCrop(
            String id,
            Item seed,
            Block plant,
            @Nullable ItemStack seedVisual,
            boolean supportsRope,
            CropKind kind,
            @Nullable Block topBlock,
            @Nullable Block soilBlock,
            @Nullable Item soilItem) {

        public boolean available() {
            return seed != null && plant != null;
        }

        public boolean isOwnPlant(BlockState state) {
            return plant != null && state.is(plant);
        }

        /** 是否为 CropBlock 且成熟(CropBlock 语义作物用)。 */
        public boolean isMatureCrop(BlockState state) {
            return plant != null
                    && state.getBlock() instanceof CropBlock crop
                    && crop.isMaxAge(state);
        }

        public BlockState plantState() {
            if (plant instanceof CropBlock crop) {
                return crop.getStateForAge(0);
            }
            return plant.defaultBlockState();
        }

        /**
         * 当前格是否可直接播种(非 paddy 用;paddy 下沉水田在 mixin 特判)。
         * full/soil/tall/colony = 可替换格 + 介质就绪;watertop = 可替换格 + 下方水。
         */
        public boolean plantableAt(BlockState cell, BlockState soil) {
            return switch (kind) {
                case FULL, SOIL, TALL, COLONY -> isReplaceableCell(cell) && soilSatisfied(soil);
                case PADDY -> false; // paddy(水稻)由 mixin 特判种到下沉水田格
                case WATERTOP -> isReplaceableCell(cell) && soil.is(Blocks.WATER);
            };
        }

        /** 当前格土壤是否满足本作物的生长介质(full/tall=耕地;soil/colony=soilBlock;watertop=水)。 */
        public boolean soilSatisfied(BlockState soil) {
            return switch (kind) {
                case FULL, TALL -> soil.is(Blocks.FARMLAND);
                case SOIL, COLONY -> soilBlock != null && soil.is(soilBlock);
                case WATERTOP -> soil.is(Blocks.WATER);
                case PADDY -> false; // 介质由 mixin 特判(下沉水田坑底土)
            };
        }

        private static boolean isReplaceableCell(BlockState state) {
            return state.isAir() || state.canBeReplaced();
        }
    }


    /** 确保解析过一次(懒加载;每次只解析一次,之后走缓存)。 */
    private static void ensureResolved() {
        if (attempted) {
            return;
        }
        synchronized (SimuCropRegistry.class) {
            if (attempted) {
                return;
            }
            attempted = true;
            // 解析农夫乐事 rope 体系(番茄攀爬支撑 + 绳上果实层);不存在则该能力不可用
            ropeBlock = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("farmersdelight:rope")).orElse(null);
            ropeItem = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("farmersdelight:rope")).orElse(null);
            ropeFruitBlock = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("farmersdelight:tomatoes_on_rope")).orElse(null);
            for (SimuCropConfig.CropDef def : SimuCropConfig.load()) {
                ResolvedCrop resolved = resolve(def);
                if (resolved != null && resolved.available()) {
                    RESOLVED.put(def.id().toLowerCase(Locale.ROOT), resolved);
                    SimuDelight.LOGGER.info("[SimuDelight] extended crop ready: {} (seed={}, plant={}, kind={}, ropes={})",
                            def.id(), def.seedItem(), def.plantBlock(), resolved.kind(), def.ropes());
                } else {
                    SimuDelight.LOGGER.warn("[SimuDelight] extended crop unavailable (target mod missing or plant not usable?): {} (seed={}, plant={}, kind={})",
                            def.id(), def.seedItem(), def.plantBlock(), def.kind());
                }
            }
        }
    }

    @Nullable
    private static ResolvedCrop resolve(SimuCropConfig.CropDef def) {
        try {
            Item seed = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(def.seedItem())).orElse(null);
            Block plant = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(def.plantBlock())).orElse(null);
            if (seed == null || seed == Items.AIR || plant == null || plant == Blocks.AIR) {
                return null;
            }
            CropKind kind = parseKind(def.kind());
            // 上层产物(tall 玉米 / paddy 水稻 需要:下层成熟后自长的上层,农夫收它)
            Block topBlock = null;
            if (kind == CropKind.PADDY || kind == CropKind.TALL) {
                if (def.topBlock() == null) {
                    SimuDelight.LOGGER.warn("[SimuDelight] crop {} kind={} 缺少 topBlock 字段", def.id(), def.kind());
                    return null;
                }
                topBlock = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(def.topBlock())).orElse(null);
                if (topBlock == null || topBlock == Blocks.AIR) {
                    SimuDelight.LOGGER.warn("[SimuDelight] crop {} topBlock {} 不可用", def.id(), def.topBlock());
                    return null;
                }
            }
            // 介质(soil/colony 需要)
            Block soilBlock = null;
            Item soilItem = null;
            if ((kind == CropKind.SOIL || kind == CropKind.COLONY) && def.soilBlock() != null) {
                soilBlock = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(def.soilBlock())).orElse(null);
                if (def.soilItem() != null) {
                    soilItem = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(def.soilItem())).orElse(null);
                }
                if (soilBlock == null || soilItem == null) {
                    SimuDelight.LOGGER.warn("[SimuDelight] crop {} kind={} 缺少可用介质(soilBlock={}, soilItem={})",
                            def.id(), def.kind(), def.soilBlock(), def.soilItem());
                    return null;
                }
            }
            // full/soil 需要 CropBlock 满铺语义;tall/watertop/colony 允许 BushBlock 等
            if (kind == CropKind.FULL || kind == CropKind.SOIL) {
                if (!(plant instanceof CropBlock)) {
                    SimuDelight.LOGGER.warn("[SimuDelight] plant {} is not a CropBlock, unsupported for kind full/soil", def.plantBlock());
                    return null;
                }
            }
            ItemStack visual = new ItemStack(seed);
            return new ResolvedCrop(def.id().toLowerCase(Locale.ROOT), seed, plant, visual, def.ropes(),
                    kind, topBlock, soilBlock, soilItem);
        } catch (Exception e) {
            SimuDelight.LOGGER.warn("[SimuDelight] failed to resolve crop {}: {}", def.id(), e.toString());
            return null;
        }
    }

    private static CropKind parseKind(String kind) {
        if (kind == null) {
            return CropKind.FULL;
        }
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "soil" -> CropKind.SOIL;
            case "paddy" -> CropKind.PADDY;      // 下沉水田水稻(种作物格下一格,自带水)
            case "tall" -> CropKind.TALL;        // 旱地两段(玉米)
            case "watertop" -> CropKind.WATERTOP;
            case "colony" -> CropKind.COLONY;
            default -> CropKind.FULL;
        };
    }

    /* ------------------------------------------------------------------
     * 绳上果实(农夫乐事番茄攀绳后结的 tomatoes_on_rope)支持
     * ---------------------------------------------------------------- */

    /** rope 体系是否可用(农夫乐事已安装)。 */
    public static boolean ropeSupportAvailable() {
        return ropeBlock != null && ropeItem != null && ropeFruitBlock != null;
    }

    /** 该方块是否是「绳上果实」层(农夫乐事 tomatoes_on_rope)。 */
    public static boolean isRopeFruit(BlockState state) {
        return ropeFruitBlock != null && state.is(ropeFruitBlock);
    }

    /** 该绳上果实是否已成熟(age 满,可收获)。 */
    public static boolean isRopeFruitMature(BlockState state) {
        return isRopeFruit(state)
                && state.getBlock() instanceof CropBlock crop
                && crop.isMaxAge(state);
    }

    /**
     * 从作物格向上扫描最多 {@code maxAbove} 格,找第一层「已成熟绳上果实」的位置。
     * 未找到返回 null。
     */
    @Nullable
    public static BlockPos findMatureRopeFruitAbove(ServerLevel level, BlockPos cropPos, int maxAbove) {
        if (!ropeSupportAvailable()) {
            return null;
        }
        for (int off = 1; off <= maxAbove; off++) {
            BlockPos above = cropPos.above(off);
            if (!level.isLoaded(above)) {
                return null;
            }
            BlockState state = level.getBlockState(above);
            if (isRopeFruitMature(state)) {
                return above;
            }
        }
        return null;
    }

    /** 是否有任意「成熟绳上果实」挂在作物格上方(收获判定用)。 */
    public static boolean hasMatureRopeFruitAbove(ServerLevel level, BlockPos cropPos, int maxAbove) {
        return findMatureRopeFruitAbove(level, cropPos, maxAbove) != null;
    }

    /** 作物格上方是否存在「未成熟绳上果实」(可被骨粉催熟,催熟判定用)。 */
    public static boolean hasImmatureRopeFruitAbove(ServerLevel level, BlockPos cropPos, int maxAbove) {
        if (!ropeSupportAvailable()) {
            return false;
        }
        for (int off = 1; off <= maxAbove; off++) {
            BlockPos above = cropPos.above(off);
            if (!level.isLoaded(above)) {
                return false;
            }
            BlockState state = level.getBlockState(above);
            if (isRopeFruit(state) && !isRopeFruitMature(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为「攀绳作物」(农夫乐事番茄)在植株正上方搭一根绳子。
     * 需材料箱里有 rope 且上方一格为空;绳悬空可放(RopeBlock 继承 IronBarsBlock,无重力)。
     */
    public static void tryPlaceRope(ServerLevel level, BlockPos cropPos, List<BlockPos> chestPositions) {
        if (ropeBlock == null || ropeItem == null) {
            return;
        }
        BlockPos above = cropPos.above();
        if (!level.isLoaded(above) || !level.getBlockState(above).isAir()) {
            return;
        }
        if (!WorkContainerService.consumeItem(level, chestPositions, ropeItem)) {
            return;
        }
        level.setBlock(above, ropeBlock.defaultBlockState(), 3);
    }

    /* ------------------------------------------------------------------
     * 上层产物(tall 两段作物:水稻的稻穗/玉米的顶穗)支持
     * ---------------------------------------------------------------- */

    /** 该方块是否是某 tall 作物的「上层产物」(由每作物 topBlock 决定)。 */
    public static boolean isTopOf(BlockState state, Block topBlock) {
        return topBlock != null && state.is(topBlock);
    }

    /** 该上层产物是否成熟(age 满,可收)。 */
    public static boolean isTopMature(BlockState state, Block topBlock) {
        return isTopOf(state, topBlock)
                && state.getBlock() instanceof CropBlock crop
                && crop.isMaxAge(state);
    }

    /** 作物格上方(最多 maxAbove 层)是否有成熟上层产物。 */
    public static boolean hasMatureTopAbove(ServerLevel level, BlockPos cropPos, Block topBlock, int maxAbove) {
        if (topBlock == null) {
            return false;
        }
        for (int off = 1; off <= maxAbove; off++) {
            BlockPos above = cropPos.above(off);
            if (!level.isLoaded(above)) {
                return false;
            }
            BlockState state = level.getBlockState(above);
            if (isTopMature(state, topBlock)) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------
     * 作物株识别(切换作物后的清场审查用)
     * ---------------------------------------------------------------- */

    /** 该方块是否是「已知作物株」:任一扩展作物植株,或 NSUK 原版 FarmCrop 植株。 */
    public static boolean isKnownCropPlant(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        for (ResolvedCrop crop : allAvailable()) {
            if (crop.isOwnPlant(state)) {
                return true;
            }
        }
        for (FarmCrop vanilla : FarmCrop.values()) {
            if (state.is(vanilla.plantBlock())) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------
     * 常规查询
     * ---------------------------------------------------------------- */

    /** 按 id 取已解析扩展作物(未注册/不可用返回 empty)。 */
    public static Optional<ResolvedCrop> byId(String cropId) {
        ensureResolved();
        if (cropId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(RESOLVED.get(cropId.toLowerCase(Locale.ROOT)));
    }

    /** 所有已解析扩展作物(供 UI/存档遍历)。 */
    public static List<ResolvedCrop> allAvailable() {
        ensureResolved();
        return List.copyOf(RESOLVED.values());
    }

    /** 是否存在任何可用扩展作物。 */
    public static boolean hasAny() {
        ensureResolved();
        return !RESOLVED.isEmpty();
    }

    /** 供「搭绳」使用的绳方块(未安装农夫乐事时为 null)。 */
    @Nullable
    public static Block ropeBlock() {
        return ropeBlock;
    }
}
