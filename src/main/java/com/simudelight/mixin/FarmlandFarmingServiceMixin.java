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
package com.simudelight.mixin;

import com.simudelight.farmland.SimuCropHolder;
import com.simudelight.farmland.SimuCropRegistry;
import com.simudelight.farmland.SimuFarmReflection;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import common.cn.kafei.simukraft.farmland.FarmlandFarmingService;
import common.cn.kafei.simukraft.material.WorkContainerService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 耕作服务扩展:让农田盒市民种植「农夫乐事家族扩展作物」。
 * <p>
 * NSUK 2.2.0 的 FarmlandFarmingService 全部依赖硬编码 FarmCrop 枚举,
 * 扩展作物时 {@code data.crop()} 为 null,原逻辑全部短路(不播种/不收获)。
 * 本 Mixin 在「扩展作物模式」下拦截耕作判定与执行方法,按作物 kind 分流:
 * <ul>
 *   <li>full/soil —— CropBlock 满铺:播种、成熟破坏收获、自动补种(soil 需农夫先铺专用介质)</li>
 *   <li>tall    —— 旱地两段(玉米):种耕地,下层成熟自长上层 topBlock,农夫只收上层</li>
 *   <li>paddy   —— 下沉水田水稻:水稻种在作物格下一格(自带水贴地),成熟收上层稻穗</li>
 *   <li>watertop —— 水面作物(蔓越莓):农夫把格下土壤灌成水后种在水面,成熟采摘留株</li>
 *   <li>colony  —— 菌落:农夫铺介质后种菌落,成熟采摘留株(机制保留,内置作物已下架)</li>
 * </ul>
 * 非扩展作物时全部走原逻辑,零影响。
 */
@Mixin(FarmlandFarmingService.class)
public abstract class FarmlandFarmingServiceMixin {

    /** 作物格正上方最多扫描几层「上层产物(稻穗/玉米顶/绳上果实)」。 */
    private static final int MAX_ABOVE_LAYERS = 2;

    /* ------------------------------------------------------------------
     * hasSeed:crop == null 表示「扩展作物」,放行 PLANT 阶段,
     * 真正的种子校验在 applyPlantWork 里完成。
     * ---------------------------------------------------------------- */
    @Inject(method = "hasSeed", at = @At("HEAD"), cancellable = true)
    private static void sd$hasSeed(ServerLevel level, List<BlockPos> chestPositions, FarmCrop crop,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (crop == null) {
            cir.setReturnValue(true);
        }
    }

    /* ------------------------------------------------------------------
     * needsTillWork:扩展作物是否需要「准备场地」。
     * ---------------------------------------------------------------- */
    @Inject(method = "needsTillWork", at = @At("HEAD"), cancellable = true)
    private static void sd$needsTillWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!sd_isExtended(data)) {
            return;
        }
        SimuCropRegistry.ResolvedCrop crop = sd_crop(data);
        if (crop == null) {
            cir.setReturnValue(false);
            return;
        }
        BlockState top = level.getBlockState(cropPos);
        BlockState soil = level.getBlockState(cropPos.below());
        if (crop.isOwnPlant(top)) {
            cir.setReturnValue(false); // 已是目标作物,走生长/收获,不折腾
            return;
        }
        if (crop.kind() == SimuCropRegistry.CropKind.PADDY && crop.isOwnPlant(soil)) {
            cir.setReturnValue(false); // 水田水稻株在下一格(下沉水田),不折腾
            return;
        }
        // 地里残留其它作物株(切换作物后):需要清场审查(拔掉 -> 重新开垦种植)
        if (sd_isCropLike(top)) {
            cir.setReturnValue(true);
            return;
        }
        boolean need;
        switch (crop.kind()) {
            case FULL, TALL -> need = isCropCellFree(top) && !soil.is(Blocks.FARMLAND);
            case SOIL, COLONY -> need = isCropCellFree(top) && !soil.is(crop.soilBlock());
            case PADDY -> {
                // 下沉水田:需要坑口(作物格)敞开 + 下一格是可扎根土(水稻种坑里);坑口预灌的水排掉
                need = !isCropCellFree(top) || top.is(Blocks.WATER) || !sd_isGrowableSoil(soil);
            }
            case WATERTOP -> need = !soil.is(Blocks.WATER) || !isCropCellFree(top);
            default -> need = false;
        }
        cir.setReturnValue(need);
    }

    /* ------------------------------------------------------------------
     * applyTillWork:扩展作物「准备场地」。full/tall 放行原逻辑(犁地);
     * paddy 造水田;watertop 造水面;soil/colony 铺介质。
     * ---------------------------------------------------------------- */
    @Inject(method = "applyTillWork", at = @At("HEAD"), cancellable = true)
    private static void sd$applyTillWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                         CallbackInfoReturnable<Object> cir) {
        if (!sd_isExtended(data)) {
            return;
        }
        SimuCropRegistry.ResolvedCrop crop = sd_crop(data);
        if (crop == null) {
            cir.setReturnValue(SimuFarmReflection.skipped());
            return;
        }
        // 审查清场:拔掉格内/上层残留的其它作物株(掉落回收入箱),再按 kind 重新开垦
        sd_clearOldCrops(level, data, cropPos);
        // full/tall:放行原逻辑(其内部会重评 needsTillWork,把下方翻成耕地)
        if (crop.kind() == SimuCropRegistry.CropKind.FULL
                || crop.kind() == SimuCropRegistry.CropKind.TALL) {
            return;
        }
        BlockPos soilPos = cropPos.below();
        switch (crop.kind()) {
            case PADDY -> {
                // 下沉水田:坑口(作物格)清成敞开(有水排干,有方块挖掉),下一格保留为可扎根土;
                // 水稻种下一格(自带水),不做平地放水
                BlockState top2 = level.getBlockState(cropPos);
                if (top2.is(Blocks.WATER) || !isCropCellFree(top2)) {
                    level.setBlock(cropPos, Blocks.AIR.defaultBlockState(), 3);
                }
                if (!sd_isGrowableSoil(level.getBlockState(soilPos))) {
                    level.setBlock(soilPos, Blocks.DIRT.defaultBlockState(), 3);
                }
                cir.setReturnValue(SimuFarmReflection.processed());
            }
            case WATERTOP -> {
                // 水面:下方土壤灌成水,作物格清空为可种水面(睡莲式)
                if (!level.getBlockState(soilPos).is(Blocks.WATER)) {
                    level.setBlock(soilPos, Blocks.WATER.defaultBlockState(), 3);
                }
                BlockState top = level.getBlockState(cropPos);
                if (!isCropCellFree(top)) {
                    level.setBlock(cropPos, Blocks.AIR.defaultBlockState(), 3);
                }
                cir.setReturnValue(SimuFarmReflection.processed());
            }
            case SOIL, COLONY -> {
                // 从农田盒旁的箱子取介质(如沃土)铺格;无箱/无料则等待
                List<BlockPos> chests = FarmlandBoxService.resolveAdjacentChests(level, data.boxPos());
                if (chests.isEmpty()
                        || crop.soilItem() == null
                        || !WorkContainerService.consumeItem(level, chests, crop.soilItem())) {
                    cir.setReturnValue(SimuFarmReflection.waitingSeed()); // 缺介质,等待
                    return;
                }
                level.setBlock(soilPos, crop.soilBlock().defaultBlockState(), 3);
                cir.setReturnValue(SimuFarmReflection.processed());
            }
            default -> cir.setReturnValue(SimuFarmReflection.skipped());
        }
    }

    /* ------------------------------------------------------------------
     * needsPlantWork:扩展作物 = 当前格可直接播种。
     * paddy(下沉水田)特判:坑口作物格可替换 + 下一格是可扎根土且未种。
     * ---------------------------------------------------------------- */
    @Inject(method = "needsPlantWork", at = @At("HEAD"), cancellable = true)
    private static void sd$needsPlantWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!sd_isExtended(data)) {
            return;
        }
        SimuCropRegistry.ResolvedCrop crop = sd_crop(data);
        if (crop == null) {
            cir.setReturnValue(false);
            return;
        }
        if (crop.kind() == SimuCropRegistry.CropKind.PADDY) {
            BlockState cell = level.getBlockState(cropPos);
            BlockState root = level.getBlockState(cropPos.below());
            cir.setReturnValue(isCropCellFree(cell) && sd_isGrowableSoil(root) && !crop.isOwnPlant(root));
            return;
        }
        cir.setReturnValue(crop.plantableAt(level.getBlockState(cropPos), level.getBlockState(cropPos.below())));
    }

    /* ------------------------------------------------------------------
     * needsHarvestWork:
     *  full/soil —— 株成熟(CropBlock.isMaxAge)或(攀绳)上方绳果成熟;
     *  paddy/tall —— 下层株在且上方上层产物(稻穗/玉米顶)成熟;
     *  watertop/colony —— 株满 age(采摘型)。
     * ---------------------------------------------------------------- */
    @Inject(method = "needsHarvestWork", at = @At("HEAD"), cancellable = true)
    private static void sd$needsHarvestWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!sd_isExtended(data)) {
            return;
        }
        SimuCropRegistry.ResolvedCrop crop = sd_crop(data);
        if (crop == null) {
            cir.setReturnValue(false);
            return;
        }
        BlockState state = level.getBlockState(cropPos);
        switch (crop.kind()) {
            case FULL, SOIL -> {
                if (crop.isOwnPlant(state) && crop.isMatureCrop(state)) {
                    cir.setReturnValue(true);
                    return;
                }
                if (crop.supportsRope() && SimuCropRegistry.hasMatureRopeFruitAbove(level, cropPos, MAX_ABOVE_LAYERS)) {
                    cir.setReturnValue(true);
                    return;
                }
                cir.setReturnValue(false);
            }
            case PADDY -> {
                // 下沉水田:水稻株在下一格,其上方(坑口及以上)有成熟稻穗
                BlockState rootState = level.getBlockState(cropPos.below());
                cir.setReturnValue(crop.isOwnPlant(rootState)
                        && SimuCropRegistry.hasMatureTopAbove(level, cropPos.below(), crop.topBlock(), MAX_ABOVE_LAYERS));
            }
            case TALL -> {
                cir.setReturnValue(crop.isOwnPlant(state)
                        && SimuCropRegistry.hasMatureTopAbove(level, cropPos, crop.topBlock(), MAX_ABOVE_LAYERS));
            }
            case WATERTOP, COLONY -> cir.setReturnValue(crop.isOwnPlant(state) && sd_isAgeMax(state));
            default -> cir.setReturnValue(false);
        }
    }

    /* ------------------------------------------------------------------
     * needsBonemealWork:扩展作物按 kind 判定「可催熟」。
     * ---------------------------------------------------------------- */
    @Inject(method = "needsBonemealWork", at = @At("HEAD"), cancellable = true)
    private static void sd$needsBonemealWork(ServerLevel level, FarmlandBoxData data,
                                             List<BlockPos> chestPositions, BlockPos cropPos,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (!sd_isExtended(data)) {
            return;
        }
        SimuCropRegistry.ResolvedCrop crop = sd_crop(data);
        if (crop == null) {
            cir.setReturnValue(false);
            return;
        }
        BlockState state = level.getBlockState(cropPos);
        boolean can;
        switch (crop.kind()) {
            case FULL, SOIL -> {
                if (!crop.isOwnPlant(state)) {
                    can = false;
                } else if (crop.isMatureCrop(state)) {
                    can = crop.supportsRope()
                            && SimuCropRegistry.hasImmatureRopeFruitAbove(level, cropPos, MAX_ABOVE_LAYERS);
                } else {
                    can = state.getBlock() instanceof BonemealableBlock b
                            && b.isValidBonemealTarget(level, cropPos, state);
                }
            }
            case PADDY -> {
                // 下沉水田:催熟看下一格的水稻株
                BlockState rootState = level.getBlockState(cropPos.below());
                if (!crop.isOwnPlant(rootState)) {
                    can = false;
                } else if (rootState.getBlock() instanceof BonemealableBlock b) {
                    can = b.isValidBonemealTarget(level, cropPos.below(), rootState);
                } else {
                    can = false;
                }
            }
            case TALL -> {
                if (!crop.isOwnPlant(state)) {
                    can = false;
                } else if (state.getBlock() instanceof BonemealableBlock b) {
                    can = b.isValidBonemealTarget(level, cropPos, state);
                } else {
                    can = false;
                }
            }
            case WATERTOP, COLONY -> {
                can = crop.isOwnPlant(state)
                        && state.hasProperty(BlockStateProperties.AGE_3)
                        && state.getValue(BlockStateProperties.AGE_3) < 3
                        && state.getBlock() instanceof BonemealableBlock b
                        && b.isValidBonemealTarget(level, cropPos, state);
            }
            default -> can = false;
        }
        if (can && !WorkContainerService.hasItem(level, chestPositions, Items.BONE_MEAL)) {
            can = false;
        }
        cir.setReturnValue(can);
    }

    /* ------------------------------------------------------------------
     * applyPlantWork:扩展作物按 kind 播种。
     * paddy(下沉水田):种到作物格下一格(坑里);其余种在作物格。
     * ---------------------------------------------------------------- */
    @Inject(method = "applyPlantWork", at = @At("HEAD"), cancellable = true)
    private static void sd$applyPlantWork(ServerLevel level, FarmlandBoxData data,
                                          List<BlockPos> chestPositions, BlockPos cropPos,
                                          CallbackInfoReturnable<Object> cir) {
        if (!sd_isExtended(data)) {
            return;
        }
        SimuCropRegistry.ResolvedCrop crop = sd_crop(data);
        if (crop == null) {
            cir.setReturnValue(SimuFarmReflection.skipped());
            return;
        }
        BlockPos plantPos = cropPos;
        if (crop.kind() == SimuCropRegistry.CropKind.PADDY) {
            // 下沉水田:坑口(作物格)需敞开,下一格可扎根且未种,水稻种下一格
            BlockState cell = level.getBlockState(cropPos);
            BlockState root = level.getBlockState(cropPos.below());
            if (!isCropCellFree(cell) || !sd_isGrowableSoil(root) || crop.isOwnPlant(root)) {
                cir.setReturnValue(SimuFarmReflection.skipped());
                return;
            }
            plantPos = cropPos.below();
        } else if (!crop.plantableAt(level.getBlockState(cropPos), level.getBlockState(cropPos.below()))) {
            cir.setReturnValue(SimuFarmReflection.skipped());
            return;
        }
        if (!WorkContainerService.consumeItem(level, chestPositions, crop.seed())) {
            cir.setReturnValue(SimuFarmReflection.waitingSeed());
            return;
        }
        level.setBlock(plantPos, crop.plantState(), 3);
        // 番茄「搭绳技能」:材料箱里有绳时,在植株正上方搭绳
        if (crop.supportsRope()) {
            SimuCropRegistry.tryPlaceRope(level, cropPos, chestPositions);
        }
        cir.setReturnValue(SimuFarmReflection.processed());
    }

    /* ------------------------------------------------------------------
     * applyHarvestWork:扩展作物按 kind 收获。
     * ---------------------------------------------------------------- */
    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true)
    private static void sd$applyHarvestWork(ServerLevel level, FarmlandBoxData data,
                                            List<BlockPos> chestPositions, BlockPos cropPos,
                                            CallbackInfoReturnable<Object> cir) {
        if (!sd_isExtended(data)) {
            return;
        }
        SimuCropRegistry.ResolvedCrop crop = sd_crop(data);
        if (crop == null) {
            cir.setReturnValue(SimuFarmReflection.skipped());
            return;
        }
        boolean didWork;
        switch (crop.kind()) {
            case FULL, SOIL -> didWork = sd_harvestFull(level, chestPositions, cropPos, crop);
            case PADDY -> didWork = sd_harvestTop(level, chestPositions, cropPos.below(), crop); // 下沉水田:株在下一格
            case TALL -> didWork = sd_harvestTop(level, chestPositions, cropPos, crop);
            case WATERTOP, COLONY -> didWork = sd_harvestPick(level, chestPositions, cropPos, crop);
            default -> didWork = false;
        }
        cir.setReturnValue(didWork ? SimuFarmReflection.processed() : SimuFarmReflection.skipped());
    }

    /* ------------------------------------------------------------------
     * 实现:full/soil —— 破坏成熟株 -> 掉落入箱 -> 补种
     * ---------------------------------------------------------------- */
    private static boolean sd_harvestFull(ServerLevel level, List<BlockPos> chestPositions, BlockPos cropPos,
                                          SimuCropRegistry.ResolvedCrop crop) {
        boolean did = false;
        // 1) 绳上成熟果实(攀绳作物)
        if (crop.supportsRope() && SimuCropRegistry.ropeSupportAvailable()) {
            did |= sd_harvestRopeFruits(level, chestPositions, cropPos);
        }
        // 2) 地面成熟株
        BlockState state = level.getBlockState(cropPos);
        if (crop.isOwnPlant(state) && crop.isMatureCrop(state)) {
            List<ItemStack> drops = Block.getDrops(state, level, cropPos, level.getBlockEntity(cropPos));
            level.setBlock(cropPos, Blocks.AIR.defaultBlockState(), 3);
            WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, cropPos);
            // 自动补种(介质仍就绪才补)
            if (crop.plantableAt(level.getBlockState(cropPos), level.getBlockState(cropPos.below()))
                    && WorkContainerService.consumeItem(level, chestPositions, crop.seed())) {
                level.setBlock(cropPos, crop.plantState(), 3);
                if (crop.supportsRope()) {
                    SimuCropRegistry.tryPlaceRope(level, cropPos, chestPositions);
                }
            }
            did = true;
        }
        return did;
    }

    /* ------------------------------------------------------------------
     * 实现:paddy/tall —— 收成熟上层产物(稻穗/玉米顶;不毁下层,会再结)
     * ---------------------------------------------------------------- */
    private static boolean sd_harvestTop(ServerLevel level, List<BlockPos> chestPositions, BlockPos cropPos,
                                         SimuCropRegistry.ResolvedCrop crop) {
        boolean did = false;
        Block top = crop.topBlock();
        if (!crop.isOwnPlant(level.getBlockState(cropPos)) || top == null) {
            return false;
        }
        for (int off = 1; off <= MAX_ABOVE_LAYERS; off++) {
            BlockPos pos = cropPos.above(off);
            if (!level.isLoaded(pos)) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!SimuCropRegistry.isTopMature(state, top)) {
                continue;
            }
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, pos);
            did = true;
        }
        return did;
    }

    /* ------------------------------------------------------------------
     * 实现:watertop/colony —— 采摘留株
     * 蔓越莓:满 age 掉 2-3 果,age -> 1 继续长;
     * 菌落:满 age 掉 3 个产物,age -> 0 继续长。
     * ---------------------------------------------------------------- */
    private static boolean sd_harvestPick(ServerLevel level, List<BlockPos> chestPositions, BlockPos cropPos,
                                          SimuCropRegistry.ResolvedCrop crop) {
        BlockState state = level.getBlockState(cropPos);
        if (!crop.isOwnPlant(state) || !sd_isAgeMax(state)) {
            return false;
        }
        int count = (crop.kind() == SimuCropRegistry.CropKind.WATERTOP)
                ? 2 + level.random.nextInt(2)   // 2..3
                : 3;                            // colony:3 个蘑菇
        ItemStack harvest = new ItemStack(crop.seed(), count);
        int nextAge = (crop.kind() == SimuCropRegistry.CropKind.WATERTOP) ? 1 : 0;
        level.setBlock(cropPos, state.setValue(BlockStateProperties.AGE_3, nextAge), 3);
        WorkContainerService.depositDropsOrDrop(level, chestPositions, List.of(harvest), cropPos);
        return true;
    }

    /* ------------------------------------------------------------------
     * 绳上果实收获:扫作物格上方 tomatoes_on_rope,成熟即收。
     * 破坏后 FD 自身(onRemove)恢复绳;配置关闭时兜底补绳。
     * ---------------------------------------------------------------- */
    private static boolean sd_harvestRopeFruits(ServerLevel level, List<BlockPos> chestPositions, BlockPos cropPos) {
        boolean any = false;
        for (int off = 1; off <= MAX_ABOVE_LAYERS; off++) {
            BlockPos pos = cropPos.above(off);
            if (!level.isLoaded(pos)) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!SimuCropRegistry.isRopeFruitMature(state)) {
                continue;
            }
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            Block rope = SimuCropRegistry.ropeBlock();
            if (rope != null && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, rope.defaultBlockState(), 3);
            }
            WorkContainerService.depositDropsOrDrop(level, chestPositions, drops, pos);
            any = true;
        }
        return any;
    }

    /* ------------------------------------------------------------------
     * 辅助
     * ---------------------------------------------------------------- */

    /** 该方块是否「像作物株」(需要清场的对象):已知作物/上层产物/绳上果实/满铺 CropBlock。 */
    private static boolean sd_isCropLike(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return SimuCropRegistry.isRopeFruit(state)
                || SimuCropRegistry.isKnownCropPlant(state)
                || state.getBlock() instanceof CropBlock;
    }

    /**
     * 审查清场:拔掉作物格、其下方一格(paddy 下沉株)及上方 1..2 层的残留作物株(非目标作物),
     * 掉落真实产物回收入箱,腾出场地。返回是否清理了任何东西。
     */
    private static boolean sd_clearOldCrops(ServerLevel level, FarmlandBoxData data, BlockPos cropPos) {
        boolean any = false;
        List<BlockPos> chests = FarmlandBoxService.resolveAdjacentChests(level, data.boxPos());
        SimuCropRegistry.ResolvedCrop target = sd_crop(data);
        for (int off = -1; off <= MAX_ABOVE_LAYERS; off++) {
            BlockPos pos = cropPos.above(off);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!sd_isCropLike(state)) {
                continue;
            }
            // 保留目标作物本身(正常流程处理);只清其它株
            if (target != null && target.isOwnPlant(state)) {
                continue;
            }
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            if (!drops.isEmpty()) {
                WorkContainerService.depositDropsOrDrop(level, chests, drops, pos);
            }
            any = true;
        }
        return any;
    }

    private static boolean sd_isExtended(FarmlandBoxData data) {
        return ((SimuCropHolder) (Object) data).sd_extendedCropId() != null;
    }

    private static SimuCropRegistry.ResolvedCrop sd_crop(FarmlandBoxData data) {
        String id = ((SimuCropHolder) (Object) data).sd_extendedCropId();
        return SimuCropRegistry.byId(id).orElse(null);
    }

    /** 采摘型作物(蔓越莓/菌落)满 age 判定:AGE_3 == 3。 */
    private static boolean sd_isAgeMax(BlockState state) {
        return state.hasProperty(BlockStateProperties.AGE_3)
                && state.getValue(BlockStateProperties.AGE_3) == 3;
    }

    /** 可让水稻扎根的土(耕地与泥土系)。 */
    private static boolean sd_isGrowableSoil(BlockState state) {
        return state.is(Blocks.FARMLAND)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MYCELIUM);
    }

    private static boolean isCropCellFree(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }
}
