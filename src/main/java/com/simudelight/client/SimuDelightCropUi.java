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
package com.simudelight.client;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.simudelight.farmland.SimuCropRegistry;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxSetCropPacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * SimuDelight 版的农田盒作物选择界面。
 * <p>
 * 复刻 NSUK 原版 FarmlandCropScreen 的布局(保证观感一致),
 * 在原版 6 种作物按钮之后追加「扩展作物」(农夫乐事卷心菜/洋葱)按钮。
 * 点击扩展作物同样发送 FarmlandBoxSetCropPacket(服务端由 FarmlandBoxServiceMixin 接管)。
 */
public final class SimuDelightCropUi {

    private static final int ITEM_HEIGHT = 22;
    /** 滚动区域可视行数(作物按钮过多时滚动查看)。 */
    private static final int VISIBLE_ROWS = 7;
    private static final int GAP = 5;

    private SimuDelightCropUi() {
    }

    /** 构建作物选择 UI(原版作物 + 扩展作物,可滚动)。 */
    public static ModularUI create(FarmlandBoxOpenResponsePacket packet) {
        int screenWidth = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.paddingAll(8);
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));
        root.addChild(topButton("gui.button.back", () -> back(packet.boxPos())));

        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(90);
            layout.maxWidth(240);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.paddingAll(10);
            layout.gapAll(5);
        }).addClass("simukraft_panel");

        panel.addChild(label(Component.translatable("gui.simukraft.farmland_box.select_crop_title"), Horizontal.CENTER, 0xFFFFFF, 16));

        // 作物按钮统一放进可滚动列表(原版 6 + 扩展若干,超出可视行数时滚动)
        UIElement list = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(GAP);
        });

        // 原版作物(NSUK FarmCrop)
        for (FarmCrop crop : FarmCrop.values()) {
            boolean selected = crop.id().equals(packet.cropId());
            Component text = selected
                    ? Component.translatable("gui.simukraft.farmland_box.crop_selected", Component.translatable(crop.translationKey()))
                    : Component.translatable(crop.translationKey());
            list.addChild(cropButton(text, packet.boxPos(), crop.id()));
        }

        // 扩展作物(农夫乐事/扩展乐事)——本 mod 追加的部分
        for (SimuCropRegistry.ResolvedCrop crop : SimuCropRegistry.allAvailable()) {
            boolean selected = crop.id().equals(packet.cropId());
            Component text = selected
                    ? Component.translatable("gui.simukraft.farmland_box.crop_selected", Component.translatable(cropKey(crop.id())))
                    : Component.translatable(cropKey(crop.id()));
            list.addChild(cropButton(text, packet.boxPos(), crop.id()));
        }

        // 可滚动容器:固定可视高度,内容超出时滚动(与 NSUK 规划材料列表同款实现)
        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> {
            layout.widthPercent(100);
            layout.height(VISIBLE_ROWS * ITEM_HEIGHT + (VISIBLE_ROWS - 1) * GAP);
        });
        scroller.addScrollViewChild(list);
        panel.addChild(scroller);

        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    /** 扩展作物翻译 key(assets/simudelight/lang/*.json)。 */
    private static String cropKey(String cropId) {
        return "simudelight.crop." + cropId;
    }

    private static UIElement cropButton(Component text, BlockPos boxPos, String cropId) {
        UIElement slot = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(ITEM_HEIGHT);
        });
        Button button = new Button();
        button.setText(text);
        button.setOnClick(event -> select(boxPos, cropId));
        button.layout(layout -> {
            layout.widthPercent(100);
            layout.height(ITEM_HEIGHT);
        });
        slot.addChild(button);
        return slot;
    }

    private static Button topButton(String key, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(5);
            layout.top(5);
            layout.width(50);
            layout.height(22);
        });
        return button;
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height) {
        Label label = new Label();
        label.setText(text);
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
        label.textStyle(style -> style
                .textColor(color)
                .textShadow(true)
                .textAlignHorizontal(horizontal)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static void select(BlockPos boxPos, String cropId) {
        PacketDistributor.sendToServer(new FarmlandBoxSetCropPacket(boxPos, cropId));
    }

    private static void back(BlockPos boxPos) {
        PacketDistributor.sendToServer(new common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenRequestPacket(boxPos));
    }
}
