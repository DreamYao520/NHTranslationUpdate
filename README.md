# NHTranslationUpdate

面向 **GT New Horizons / Minecraft 1.7.10** 的简体中文汉化自动更新模组。

模组在 Forge 加载游戏资源前读取远程清单，下载经过 SHA-256 与大小校验的资源，原子安装到整合包，并在网络不可用或更新失败时继续使用上一版汉化。构建基于 GTNH 的 [ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10)，更新思想参考 [CFPAOrg/I18nUpdateMod3](https://github.com/CFPAOrg/I18nUpdateMod3)，但下载协议和安装器是针对 GTNH 重新实现的。

## 能做什么

- 把普通模组语言文件转换成 Minecraft 1.7.10 `pack_format: 1` 资源包。
- 自动把资源包安装到 `resourcepacks/NHTranslationUpdate.zip`，并写入 `options.txt` 启用它。
- 安装 GTNH 任务书等必须由 TX Loader 读取的覆盖文件。
- 默认仅允许覆盖 `resources/`、`config/txloader/`、BetterLoadingScreen、Amazing Trophies 和 InGameInfoXML 的汉化目录。
- 保存被替换文件的备份；清理旧版本时，只删除本模组管理且未被用户修改的文件。
- 支持客户端与服务端。资源包只在客户端安装，任务书覆盖包可在两端安装。

它不会从更新站点安装模组、脚本、存档或可执行文件，也不会解压任何越过白名单目录的路径。

## 玩家安装

1. 从 Releases 下载模组 jar，放进 GTNH 实例的 `mods/`。
2. 首次启动会生成 `config/nhtranslationupdate.properties`。
3. 默认更新地址为 `https://dreamyao520.github.io/NHTranslationUpdate/manifest.json`。
4. 推荐在配置中填写精确的 `packVersion`，例如 `2.8.4`，然后重启。

模组默认把语言设为 `zh_CN`。若不希望它改语言，把 `forceLanguage` 留空。

## 汉化发布

仓库附带了一个只使用 Python 标准库的发布器，可直接读取 `Translation-of-GTNH` 的现有目录结构：

```text
python tools/build_update.py \
  --source ../Translation-of-GTNH \
  --output site \
  --release 2.8.4-cn.1 \
  --pack-version 2.8.4 \
  --base-url https://dreamyao520.github.io/NHTranslationUpdate
```

输出包括：

- `site/manifest.json`：客户端读取的稳定入口；
- `site/releases/<release>/gtnh-zh-cn-resource-pack.zip`：语言资源包；
- `site/releases/<release>/gtnh-zh-cn-overlay.zip`：TX Loader 等覆盖文件；
- `site/index.html`：简单的当前版本页面。

GitHub Actions 中的 **Publish translation update site** 可以手动选择汉化仓库、提交和 GTNH 版本，随后构建并发布到 GitHub Pages；它也会在每日汉化构建之后自动刷新。仓库设置中需要把 Pages 的 Source 设为 **GitHub Actions**。

## 构建和测试

需要 JDK 21 来运行当前 Gradle，产物仍是 Java 8 字节码，可在 GTNH 支持的 Java 8 及现代 Java 环境中运行。

```text
./gradlew build
python -m unittest discover -s tests -v
```

Windows 使用 `gradlew.bat build`。

协议细节与威胁边界见 [docs/UPDATE_PROTOCOL.md](docs/UPDATE_PROTOCOL.md)。

## 与参考模组的主要区别

I18nUpdateMod3 是跨 Minecraft 版本、跨加载器的通用资源包下载器；NHTranslationUpdate 固定面向 Forge 1.7.10 和 GTNH，因此不需要做跨版本语言格式转换，反而需要处理 GTNH 的 `resources/`、TX Loader 与任务书汉化。这里使用 SHA-256、HTTPS、下载/解压上限、路径白名单、原子替换、归属索引与备份，远程清单不能自行指定任意安装目标。

## 许可

模组与发布工具采用 MIT License。下载的汉化内容仍遵守其来源项目自己的许可；例如 `Translation-of-GTNH` 当前声明为 CC-BY-NC-SA，发布者需要保留署名与许可说明。
