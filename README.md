# NHTranslationUpdate

面向 **GT New Horizons / Minecraft 1.7.10** 的简体中文汉化自动更新模组。

模组通过 ASM 字节码变换在游戏资源加载链中插入一个自定义 `IResourcePack`，从远程清单下载统一的翻译 ZIP，在内存中提供所有汉化资源（标准 Minecraft 语言文件 + TX Loader 资源），并在 TX Loader 的 `forceload` 包之后注入以确保持最高优先级。网络不可用或更新失败时继续使用上一版汉化。

## 能做什么

- 下载包含所有翻译内容的统一 ZIP 包，在**内存**中提供所有翻译资源，无需解压到磁盘。
- 通过 ASM 变换（`@SortingIndex(2000)`）将翻译资源包注入到资源包链最末尾，优先级高于 TX Loader 的 `forceload` 包。
- 自动设置 `options.txt` 中的 `lang` 选项为配置的语言（默认 `zh_CN`）。
- 支持客户端与服务端。ASM 资源包注入仅对客户端生效，但文件下载两端均可运行。

它不会从更新站点安装模组、脚本、存档或可执行文件。

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

- `site/manifest.json`：客户端读取的稳定入口（schema v2）；
- `site/releases/<release>/gtnh-zh-cn-translation.zip`：统一翻译 ZIP（`assets/` + `txloader/`）；
- `site/index.html`：简单的当前版本页面。

GitHub Actions 中的 **Publish translation update site** 可以手动选择汉化仓库、提交和 GTNH 版本，随后构建并发布到 GitHub Pages。

## 构建和测试

需要 JDK 21 来运行当前 Gradle，产物仍是 Java 8 字节码，可在 GTNH 支持的 Java 8 及现代 Java 环境中运行。

```text
./gradlew build
python -m unittest discover -s tests -v
```

Windows 使用 `gradlew.bat build`。

协议细节与威胁边界见 [docs/UPDATE_PROTOCOL.md](docs/UPDATE_PROTOCOL.md)。

## 架构

```
CoreMod (SortingIndex 2000)
  ├── injectData() → UpdateBootstrap.run()
  │     └── UpdateService
  │           ├── 下载 manifest.json (schema v2)
  │           ├── 下载 unified translation ZIP
  │           └── NHTranslationResourcePack.load(zip)
  │                 └── 加载到 ConcurrentHashMap<String, byte[]>
  │
  └── getASMTransformerClass() → MinecraftClassTransformer
        └── 在 Minecraft.refreshResources() 中
            在 reloadResources(List) 调用前注入 hook
              → MinecraftHook.insertPack()
                  → 追加 NHTranslationResourcePack 到列表末尾
```

## 许可

模组与发布工具采用 MIT License。下载的汉化内容仍遵守其来源项目自己的许可；例如 `Translation-of-GTNH` 当前声明为 CC-BY-NC-SA，发布者需要保留署名与许可说明。
