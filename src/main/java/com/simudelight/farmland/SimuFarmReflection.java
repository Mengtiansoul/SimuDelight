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

/**
 * 反射访问 NSUK 的 package-private 枚举 FarmlandWorkResult。
 * <p>
 * 该枚举是包私有类型,联动 mod 无法在编译期直接引用(同包会触发 JPMS split package 冲突,
 * NeoForge 的 Access Transformer 只对 Minecraft 官方类生效)。因此通过反射在运行时
 * 取到 PROCESSED / SKIPPED / WAITING_SEED 三个枚举值,供 Mixin 的 setReturnValue 使用。
 */
public final class SimuFarmReflection {

    private static final Object PROCESSED;
    private static final Object SKIPPED;
    private static final Object WAITING_SEED;

    static {
        Object processed = null;
        Object skipped = null;
        Object waitingSeed = null;
        try {
            Class<?> resultClass = Class.forName("common.cn.kafei.simukraft.farmland.FarmlandWorkResult");
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = (Class<? extends Enum>) resultClass;
            processed = Enum.valueOf(enumClass, "PROCESSED");
            skipped = Enum.valueOf(enumClass, "SKIPPED");
            waitingSeed = Enum.valueOf(enumClass, "WAITING_SEED");
            SimuDelight.LOGGER.info("[SimuDelight] FarmlandWorkResult reflection ready");
        } catch (Throwable t) {
            SimuDelight.LOGGER.error("[SimuDelight] failed to reflect FarmlandWorkResult", t);
        }
        PROCESSED = processed;
        SKIPPED = skipped;
        WAITING_SEED = waitingSeed;
    }

    private SimuFarmReflection() {
    }

    public static Object processed() {
        return PROCESSED;
    }

    public static Object skipped() {
        return SKIPPED;
    }

    public static Object waitingSeed() {
        return WAITING_SEED;
    }
}
