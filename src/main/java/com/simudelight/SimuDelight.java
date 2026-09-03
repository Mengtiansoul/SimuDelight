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
package com.simudelight;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SimuDelight —— New:Sim-U-Kraft(NSUK)×Farmer's Delight 农田联动
 *
 * 目标:让 NSUK「农田建筑盒」雇佣的市民可以种植农夫乐事作物。
 * 原理:农田盒作物表(FarmCrop)是硬编码原版枚举,NSUK 2.2.0 无扩展接口;
 * 本 mod 通过 Mixin 为农田数据/耕作服务桥接「扩展作物注册表」,
 * 扩展作物以 (种子, 植株 CropBlock) 形式注册,复用 NSUK 的种/收/补种工作流。
 */
@Mod(SimuDelight.MOD_ID)
public final class SimuDelight {
    public static final String MOD_ID = "simudelight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public SimuDelight(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[SimuDelight] loading...");
    }
}
