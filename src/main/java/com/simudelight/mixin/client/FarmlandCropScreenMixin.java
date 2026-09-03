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
package com.simudelight.mixin.client;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.simudelight.client.SimuDelightCropUi;
import com.simudelight.farmland.SimuCropRegistry;
import client.cn.kafei.simukraft.client.farmland.FarmlandCropScreen;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 农田盒作物选择界面扩展:存在扩展作物(农夫乐事)时,用 SimuDelightCropUi
 * 接管界面构建(原版布局 + 扩展作物按钮);无扩展作物时走 NSUK 原版界面。
 */
@Mixin(FarmlandCropScreen.class)
public abstract class FarmlandCropScreenMixin {

    @Inject(method = "createUi", at = @At("HEAD"), cancellable = true)
    private static void sd$onCreateUi(FarmlandBoxOpenResponsePacket packet, CallbackInfoReturnable<ModularUI> cir) {
        if (!SimuCropRegistry.hasAny()) {
            return; // 无扩展作物,原版 UI 即可
        }
        cir.setReturnValue(SimuDelightCropUi.create(packet));
    }
}
