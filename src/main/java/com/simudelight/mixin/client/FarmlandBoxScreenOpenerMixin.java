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

import com.simudelight.farmland.SimuCropRegistry;
import client.cn.kafei.simukraft.client.farmland.FarmlandBoxScreenOpener;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 农田盒主面板「作物」行扩展:NSUK 原版把 cropId 拼成
 * {@code gui.simukraft.farmland_box.crop.<id>} 翻译,只认识原版枚举;
 * 扩展作物(农夫乐事家族)id 走本 mod 的 lang key(simudelight.crop.<id>)翻译。
 */
@Mixin(FarmlandBoxScreenOpener.class)
public abstract class FarmlandBoxScreenOpenerMixin {

    @Inject(method = "cropLine", at = @At("HEAD"), cancellable = true)
    private static void sd$cropLine(FarmlandBoxOpenResponsePacket packet, CallbackInfoReturnable<Component> cir) {
        String cropId = packet.cropId();
        if (cropId == null || cropId.isBlank()) {
            return; // 未设置,走原版「无」
        }
        if (!SimuCropRegistry.looksExtended(cropId)) {
            return; // 原版作物,走原版翻译
        }
        MutableComponent crop = Component.translatable("simudelight.crop." + cropId);
        cir.setReturnValue(Component.translatable("gui.simukraft.farmland_box.crop_line", crop));
    }
}
