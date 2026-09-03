# SimuDelight —— 模拟大都市 × 农夫乐事 农田联动(正式版 1.0.2)

让 New:Sim-U-Kraft(NSUK)农田作业盒里的市民,种植农夫乐事家族的作物。

## 功能

- **数据驱动作物清单**:`config/simudelight/crops.json`(首启自动生成;缺哪个 addon 就自动隐藏哪些作物)。
- **支持的种植形态**(crops.json 的 `kind` 字段):
  - `full` 满铺 CropBlock(卷心菜/洋葱/番茄/芦笋/辣椒/花生/红薯/蔬菜乐事全家/乡村乐事/芋泥乐事/菠萝/文化黄瓜茄子等)
  - `paddy` 水田水稻(稻种在作物格下一层、自带水贴地;低洼水田把农田盒放进坑里即可;成熟收上层稻穗不毁株)
  - `tall` 旱地两段玉米(文化/墨西哥:种耕地,下层自长,农夫收上层玉米棒)
  - `watertop` 水面作物(蔓越莓:农夫把格下土壤灌成水,睡莲式种水面,成熟采摘留株)
  - `soil`/`colony` 专用介质作物(洞穴胡萝卜等;colony 菌落机制保留供自定义)
  - 番茄绳上果实(搭绳攀爬,成熟果实自动收获并恢复绳)
- **市民自动种收循环**:翻地/铺介质/灌水 → 播种 → 施骨粉 → 收获入箱 → 补种;切换作物时自动「清场审查」(拔掉旧株回收入箱,再按新作物重新开垦),不会尬住。
- **界面集成**:主面板正确显示扩展作物名;作物选择滚动列表含全部已安装 addon 的作物。

## 依赖

- 必需:New:Sim-U-Kraft(simukraft ≥2.0)、NeoForge 1.21.1、Minecraft 1.21.1
- 可选:Farmer's Delight 及任意 FD 附属 addon(装了哪些,农田盒就显示哪些作物)

## 使用

1. 放置农田作业盒,雇佣农民,在旁放箱子(种子/材料放入);
2. 「切换作物」选择想种的扩展作物(如水稻/玉米/蔓越莓);
3. 「设置区域」框选田地,开耕——市民自动准备场地并种收。

作物显示名与指令:`/simudelight crops`(列出可用)、`/simudelight setcrop <id>`(快捷设置)。

## 配置

`config/simudelight/crops.json` 增删条目即可加减作物(内置条目会随版本自动升级,用户自定义条目保留)。

## 📜 许可与致谢

- 本 mod 以 **GNU GPL v3** 发布(全文见 [LICENSE](LICENSE),随 jar 附送 `META-INF/LICENSE-GPL-3.0.txt`)。Copyright © 2026 Mengtiansoul。
- 本 mod 是 **New:Sim-U-Kraft** 的兼容/衍生扩展 —— New:Sim-U-Kraft © NSUK Studio,以 **GPL-3.0** 授权([官方仓库](https://github.com/New-Sim-U-Kraft/New-Simukraft-1.21.1))。
- 作物仅通过注册表 id 引用 **Farmer's Delight**(© vectorwing,[MIT](https://github.com/vectorwing/FarmersDelight)) 及各家 add-on 的方块/物品,**不含也不捆绑任何 FD 代码或资源**。
- 客户端界面基于 NSUK 主题 API(LDLib2,LGPL-3.0)构建。
- 构建脚手架:[NeoForge MDK](https://github.com/NeoForged/MDK)(模板文件 MIT © NeoForged project)。

## 🔨 从源码构建

需要 JDK 21:

```bash
./gradlew build
# 产物:build/libs/simudelight-<version>.jar
```

## 🐛 问题反馈

请带上:游戏日志 `logs/latest.log`、`config/simudelight/crops.json` 内容、NSUK/FD 版本。
