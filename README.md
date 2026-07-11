# NHTranslationUpdate

面向 **GT New Horizons / Minecraft 1.7.10** 的简体中文汉化自动更新模组。

模组从远程清单下载与当前 GTNH 版本精确匹配的翻译包，并通过自定义 `IResourcePack` 把汉化插入资源加载链末尾，优先级高于 TX Loader 的 `forceload` 资源包。网络不可用或更新失败时，只会回退到同一 GTNH 版本最后一次验证成功的汉化。

## 主要行为

- 自动读取 `config/txloader/load/mainmenu/version.txt`；不存在时读取 `config/GTNewHorizons/dreamcraft.cfg` 的 `S:ModPackVersion=`。
- 远程清单按 GTNH 版本分别记录翻译包，不会把其他版本的汉化当成“最新版”安装。
- 发布时按 `resources → txloader/load → txloader/forceload` 合并同一资源；后面的同名语言键拥有更高优先级。
- 仅当玩家当前语言是 `zh_CN` 时才把资源包加入 Minecraft。模组不会修改 `options.txt`，也不会替玩家更改语言。
- 玩家在游戏内切换到简体中文时，Minecraft 刷新资源后立即启用；切换到其他语言时立即移除。
- `GregTech.lang` 会以 `config/GregTech_zh_CN.lang` 安装。GregTech 只会在玩家选择简体中文时读取它。
- 专用服务端不下载也不加载客户端汉化。

它不会从更新站点安装模组、脚本、存档或可执行文件。

## 玩家安装

1. 从 Releases 下载模组 jar，放进 GTNH 实例的 `mods/`。
2. 首次启动会生成 `config/nhtranslationupdate.properties`。
3. 默认更新地址为 `https://dreamyao520.github.io/NHTranslationUpdate/manifest.json`。
4. 正常情况下保持 `packVersion=` 为空即可自动识别；只有自定义实例无法识别时才手动填写。
5. 游戏会保持原来的语言。玩家手动选择“简体中文”后才会应用汉化。

## 汉化发布

发布器可直接读取 `Translation-of-GTNH` 的目录结构：

```text
python tools/build_update.py \
  --source ../Translation-of-GTNH \
  --output site \
  --release 2.8.4-cn.1 \
  --pack-version 2.8.4 \
  --base-url https://dreamyao520.github.io/NHTranslationUpdate \
  --existing-site-url https://dreamyao520.github.io/NHTranslationUpdate
```

`--existing-site-url` 会下载并校验站点现有的 schema v3 目录和历史版本翻译包，再加入本次版本，防止部署新版本时删除旧版本。

定时发布所对应的 GTNH 版本写在 `update/current-pack-version.txt`。GTNH 大版本变化时应先确认翻译仓库已适配，再修改这个文件；手动运行工作流时也可用 `pack_version` 覆盖。

输出包括：

- `site/manifest.json`：按 GTNH 版本索引的 schema v3 清单；
- `site/releases/<release>/gtnh-zh-cn-translation.zip`：已经扁平合并的统一翻译包；
- `site/index.html`：已发布版本列表。

## 构建和测试

需要 JDK 21 来运行当前 Gradle；产物仍是 Java 8 字节码。

```text
./gradlew build
python -m unittest discover -s tests -v
```

Windows 使用 `gradlew.bat build`。协议细节见 [docs/UPDATE_PROTOCOL.md](docs/UPDATE_PROTOCOL.md)。

## 架构

```text
CoreMod (SortingIndex 2000)
  ├── 检测 GTNH 版本 → schema v3 精确选包 → 校验并加载最后可用版本/新版本
  └── Minecraft.refreshResources()
        └── 仅 zh_CN：把 NHTranslationResourcePack 追加到 TX Loader 之后
```

## 许可

模组与发布工具采用 MIT License。下载的汉化内容仍遵守来源项目自己的许可，发布者需要保留相应署名与许可说明。
