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
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandBoxManager;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import common.cn.kafei.simukraft.farmland.FarmlandBoxView;
import common.cn.kafei.simukraft.farmland.FarmlandPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端农田盒服务扩展:
 * <ul>
 *   <li>{@code setCrop}:扩展作物(农夫乐事)走扩展通道存储(原逻辑只认原版 FarmCrop 枚举)。</li>
 *   <li>{@code buildView}:扩展作物模式时把扩展作物 id 回传给客户端
 *       (原逻辑 cropId 来自 data.crop(),扩展作物为 null -> 空 -> 界面显示「无」)。</li>
 * </ul>
 */
@Mixin(FarmlandBoxService.class)
public abstract class FarmlandBoxServiceMixin {

    @Inject(method = "setCrop", at = @At("HEAD"), cancellable = true)
    private static void sd$setExtendedCrop(ServerLevel level, BlockPos boxPos, String cropId,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!SimuCropRegistry.looksExtended(cropId)) {
            return; // 原版作物走原逻辑
        }
        if (SimuCropRegistry.byId(cropId).isEmpty()) {
            cir.setReturnValue(false); // 扩展作物不可用(目标 mod 缺失等)
            return;
        }
        FarmlandBoxManager manager = FarmlandBoxManager.get(level);
        FarmlandBoxData data = manager.getOrCreate(boxPos);
        if (data.running()) {
            cir.setReturnValue(false); // 运行中不允许改作物,与原版一致
            return;
        }
        ((SimuCropHolder) (Object) data).sd_setExtendedCrop(cropId);
        manager.persist(data);
        cir.setReturnValue(true);
    }

    /** 扩展作物模式下,view 携带扩展作物 id,让客户端界面能显示作物名。 */
    @Inject(method = "buildView", at = @At("HEAD"), cancellable = true)
    private static void sd$buildExtendedView(ServerLevel level, BlockPos boxPos,
                                             CallbackInfoReturnable<FarmlandBoxView> cir) {
        FarmlandBoxData data = FarmlandBoxManager.get(level).get(boxPos);
        if (data == null) {
            return; // 无数据,走原逻辑(空 view)
        }
        String extId = ((SimuCropHolder) (Object) data).sd_extendedCropId();
        if (extId == null) {
            return; // 原版作物,走原逻辑
        }
        boolean hasCity = FarmlandBoxService.cityIdFor(level, boxPos) != null;
        CitizenData assignedWorker = FarmlandBoxService.findAssignedWorker(level, boxPos);
        FarmlandPlot plot = data.plot();
        BlockPos chest = FarmlandBoxService.resolveAdjacentChest(level, boxPos);
        boolean hasPlot = plot != null;
        boolean hasChest = chest != null;
        boolean hasWorker = assignedWorker != null;
        cir.setReturnValue(new FarmlandBoxView(
                boxPos.immutable(), hasCity, extId,
                hasPlot, hasPlot ? plot.min() : BlockPos.ZERO, hasPlot ? plot.max() : BlockPos.ZERO,
                hasChest, hasChest ? chest : BlockPos.ZERO,
                data.running(),
                hasWorker, hasWorker ? assignedWorker.name() : ""));
    }
}
