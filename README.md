# NHTranslationUpdate

面向 **GT New Horizons / Minecraft 1.7.10** 的多语言翻译自动更新模组，翻译来源为官方 [GTNewHorizons/GTNH-Translations](https://github.com/GTNewHorizons/GTNH-Translations)。

模组自动检测当前 GTNH 版本，下载与该版本精确匹配的翻译包，并根据玩家当前选择的语言动态启用。它不会修改 `options.txt`，也不会替玩家更改默认语言。

## 支持的语言

- 简体中文 `zh_CN`
- 日本語 `ja_JP`
- 한국어 `ko_KR`
- Português do Brasil `pt_BR`
- Français `fr_FR`
- Español `es_ES`
- Türkçe `tr_TR`
- Deutsch `de_DE`
- Polski `pl_PL`
- Русский `ru_RU`

语言列表由官方翻译仓库自动发现；以后官方增加语言时，发布器无需修改代码即可纳入。

## 主要行为

- 优先读取 `config/txloader/load/mainmenu/version.txt` 检测版本，回退到 `config/GTNewHorizons/dreamcraft.cfg`。
- 远程 schema v3 清单按 GTNH 版本精确选包，不会跨版本安装。
- 将官方仓库中每种语言的 `txloader/load` 与 `txloader/forceload` 合并为一个虚拟资源包，优先级高于 TX Loader。
- 虚拟资源包直接注入资源刷新列表，不写入 `resourcepacks`，也不会出现在资源包选择菜单。
- 只有当前语言在该版本的 `languages` 列表中时才启用；英语等未发布语言不会应用翻译包。
- GregTech、Better Loading Screen、Amazing Trophies、InGameInfoXML 的语言专用文件通过固定白名单安装。
- 大型多语言 ZIP 从磁盘按需读取，不再一次性把约 100 MiB 的解压内容放进内存。
- 网络失败时只回退到同一 GTNH 版本最后一次验证成功的翻译包。
- 专用服务端不下载也不加载客户端翻译。

## 玩家安装

1. 下载正式 JAR，放进 GTNH 实例的 `mods/`，删除旧版 JAR。
2. 首次启动会生成 `config/nhtranslationupdate.properties`。
3. 保持 `packVersion=` 为空即可自动识别；自定义实例无法识别时才手动填写。
4. 在游戏语言菜单中选择任一受支持语言；标准 `.lang` 资源会在刷新后应用。GregTech 等 1.7.10 模组会缓存部分名称，首次切换后建议重启一次游戏。

升级时会自动删除 v0.1.0 留下的 `resourcepacks/NHTranslationUpdate.zip` 及其启用记录，但不会修改玩家当前语言或其他资源包。

## 翻译发布

```text
python tools/build_update.py \
  --source ../GTNH-Translations \
  --output site \
  --release 2.9.0-beta-2-multilang-<source-sha> \
  --pack-version 2.9.0-beta-2 \
  --base-url https://dreamyao520.github.io/NHTranslationUpdate \
  --existing-site-url https://dreamyao520.github.io/NHTranslationUpdate
```

默认自动发现并构建官方仓库全部语言；可重复传入 `--language` 只构建指定语言。`--existing-site-url` 会校验并保留旧 GTNH 版本的目录与产物。

GitHub Pages 工作流会同时检出官方翻译仓库和官方整合包仓库：翻译内容来自前者，当前 GTNH 精确版本来自后者的 `version.txt`。

## 构建和测试

```text
./gradlew build
python -m unittest discover -s tests -v
```

Gradle 使用现代 JDK 运行，最终产物仍为 Java 8 字节码。协议细节见 [docs/UPDATE_PROTOCOL.md](docs/UPDATE_PROTOCOL.md)。

## 许可

模组与发布工具采用 MIT License。在线下载的官方翻译内容由 `GTNewHorizons/GTNH-Translations` 提供并遵守其 GPL-3.0 License。
