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
package com.simudelight.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.simudelight.SimuDelight;
import com.simudelight.farmland.SimuCropRegistry;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 扩展作物设置指令(v0.1 入口;后续客户端 UI 完善后可保留为调试/快捷入口)。
 * <ul>
 *   <li>{@code /simudelight crops} —— 列出可用的扩展作物</li>
 *   <li>{@code /simudelight setcrop <cropId>} —— 对玩家附近 8 格内的农田盒设置扩展作物</li>
 * </ul>
 */
@EventBusSubscriber(modid = SimuDelight.MOD_ID)
public final class SimuDelightCommands {

    private static final int SEARCH_RADIUS = 8;

    private SimuDelightCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("simudelight")
                        .then(Commands.literal("crops").executes(ctx -> listCrops(ctx.getSource())))
                        .then(Commands.literal("setcrop")
                                .then(Commands.argument("crop", StringArgumentType.word())
                                        .executes(ctx -> setCrop(ctx.getSource(), StringArgumentType.getString(ctx, "crop")))))
        );
    }

    private static int listCrops(CommandSourceStack source) {
        var crops = SimuCropRegistry.allAvailable();
        if (crops.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[SimuDelight] 无可用的扩展作物(请确认已安装 Farmer's Delight)"), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("[SimuDelight] 可用扩展作物: ");
        for (SimuCropRegistry.ResolvedCrop crop : crops) {
            sb.append(crop.id()).append("  ");
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return crops.size();
    }

    private static int setCrop(CommandSourceStack source, String cropId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[SimuDelight] 该指令需由玩家执行"));
            return 0;
        }
        if (SimuCropRegistry.byId(cropId).isEmpty()) {
            source.sendFailure(Component.literal("[SimuDelight] 未知扩展作物: " + cropId + "。用 /simudelight crops 查看可用作物"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos box = findNearbyFarmlandBox(level, player.blockPosition());
        if (box == null) {
            source.sendFailure(Component.literal("[SimuDelight] 附近 8 格内未找到农田盒"));
            return 0;
        }
        boolean ok = FarmlandBoxService.setCrop(level, box, cropId);
        if (ok) {
            source.sendSuccess(() -> Component.literal("[SimuDelight] 已设置扩展作物: " + cropId + " @ " + box.toShortString()), false);
            return 1;
        }
        source.sendFailure(Component.literal("[SimuDelight] 设置失败(农田盒可能正在运行,请先停止)"));
        return 0;
    }

    private static BlockPos findNearbyFarmlandBox(ServerLevel level, BlockPos center) {
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
                        return p;
                    }
                }
            }
        }
        return null;
    }
}
