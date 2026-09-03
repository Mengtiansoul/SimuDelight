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

/**
 * FarmlandBoxData 的 Mixin 扩展访问接口:
 * 通过 Mixin 把扩展作物 id 挂到 NSUK 农田盒数据对象上。
 * 运行期目标类(FarmlandBoxData)会被 Mixin 混入该接口实现。
 */
public interface SimuCropHolder {

    /** 读取扩展作物 id(非扩展作物时为 null)。 */
    String sd_extendedCropId();

    /** 记录扩展作物 id 并清除原版作物(两者互斥)。 */
    void sd_setExtendedCrop(String cropId);

    /** 是否处于扩展作物模式。 */
    default boolean sd_isExtended() {
        return sd_extendedCropId() != null;
    }
}
