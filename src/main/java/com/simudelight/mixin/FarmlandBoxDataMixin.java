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
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FarmlandBoxData 扩展:让农田盒数据对象能承载「扩展作物」(农夫乐事作物)。
 * <p>
 * 原版 crop 字段只认 FarmCrop 枚举;扩展作物时该字段保持 null,
 * 扩展作物 id 存于本 Mixin 混入的 {@code sd_extendedCropId} 字段,
 * 存档 "Crop" 字符串直接写扩展 id(原版 fromId 不认识,不影响原版数据)。
 */
@Mixin(FarmlandBoxData.class)
public abstract class FarmlandBoxDataMixin implements SimuCropHolder {

    @Unique
    private String sd_extendedCropId;

    @Override
    public String sd_extendedCropId() {
        return sd_extendedCropId;
    }

    @Override
    public void sd_setExtendedCrop(String cropId) {
        // 先清原版作物(setCrop(null) 会触发 sd$onSetVanillaCrop,此刻扩展 id 尚未设置,无副作用)
        ((FarmlandBoxData) (Object) this).setCrop(null);
        // 再写扩展 id
        this.sd_extendedCropId = cropId;
    }

    /** 切回原版作物时,清掉扩展作物 id。 */
    @Inject(method = "setCrop", at = @At("HEAD"))
    private void sd$onSetVanillaCrop(FarmCrop crop, CallbackInfo ci) {
        this.sd_extendedCropId = null;
    }

    /** 扩展作物模式下 isConfigured 只要求作业区域已设置。 */
    @Inject(method = "isConfigured", at = @At("HEAD"), cancellable = true)
    private void sd$isConfigured(CallbackInfoReturnable<Boolean> cir) {
        if (this.sd_extendedCropId != null) {
            cir.setReturnValue(((FarmlandBoxData) (Object) this).plot() != null);
        }
    }

    /** 扩展作物模式下 toTag 用扩展 id 序列化(避开原版枚举)。 */
    @Inject(method = "toTag", at = @At("HEAD"), cancellable = true)
    private void sd$toTag(CallbackInfoReturnable<CompoundTag> cir) {
        if (this.sd_extendedCropId != null) {
            FarmlandBoxData self = (FarmlandBoxData) (Object) this;
            CompoundTag tag = new CompoundTag();
            tag.putLong("BoxPos", self.boxPos().asLong());
            tag.putString("Crop", this.sd_extendedCropId);
            if (self.plot() != null) {
                tag.put("Plot", self.plot().toTag());
            }
            tag.putBoolean("Running", self.running());
            cir.setReturnValue(tag);
        }
    }

    /** 扩展作物模式下 fromTag 恢复:未知 Crop id 走扩展作物通道。 */
    @Inject(method = "fromTag", at = @At("HEAD"), cancellable = true)
    private static void sd$fromTag(CompoundTag tag, CallbackInfoReturnable<FarmlandBoxData> cir) {
        if (tag == null || !tag.contains("Crop")) {
            return;
        }
        String cropId = tag.getString("Crop");
        if (!SimuCropRegistry.looksExtended(cropId)) {
            return;
        }
        FarmlandBoxData data = new FarmlandBoxData(BlockPos.of(tag.getLong("BoxPos")));
        if (tag.contains("Plot")) {
            FarmlandPlot plot = FarmlandPlot.fromTag(tag.getCompound("Plot"));
            if (plot != null) {
                data.setPlot(plot);
            }
        }
        data.setRunning(tag.getBoolean("Running"));
        ((SimuCropHolder) (Object) data).sd_setExtendedCrop(cropId);
        cir.setReturnValue(data);
    }
}
