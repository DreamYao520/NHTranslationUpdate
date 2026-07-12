# Update protocol v3

`manifest.json` 是 UTF-8 JSON，最大 1 MiB。`languages` 是 schema v3 的向后兼容扩展；旧清单没有该字段时客户端按仅支持 `zh_CN` 处理。

```json
{
  "schemaVersion": 3,
  "minecraftVersion": "1.7.10",
  "packs": {
    "2.9.0-beta-2": {
      "release": "2.9.0-beta-2-multilang-abcdef123456",
      "languages": ["de_DE", "es_ES", "fr_FR", "ja_JP", "ko_KR", "pl_PL", "pt_BR", "ru_RU", "tr_TR", "zh_CN"],
      "artifacts": [
        {
          "id": "gtnh-multilingual-translation",
          "kind": "translation",
          "url": "https://example.org/releases/2.9.0-beta-2-multilang-abcdef123456/gtnh-multilingual-translation.zip",
          "sha256": "64 lowercase hexadecimal characters",
          "size": 123456,
          "required": true
        }
      ]
    }
  }
}
```

客户端只精确匹配当前 GTNH 版本。资源刷新时只在当前语言出现在 `languages` 中时插入虚拟资源包。

## Translation ZIP

```text
assets/{domain}/{path}
install/config/GregTech_{locale}.lang
install/config/Betterloadingscreen/tips/{locale}.txt
install/config/amazingtrophies/lang/{locale}.lang
install/config/InGameInfoXML/InGameInfo_{locale}.xml
```

发布器读取官方 `GTNH-Translations/{locale}` 目录。对于每种语言，同一资源按 `txloader/load → txloader/forceload` 合并到标准 `assets` 命名空间；`.lang` 文件按顺序拼接，其他文件由高优先级层替换。

`install/` 不是通用解压入口。客户端只接受上面四种路径，并要求其中 locale 已在清单 `languages` 中。所有目标都被限制在游戏目录内并逐文件原子写入。

## 虚拟资源包

ZIP 经完整路径、重复条目、条目数量和实际解压总量验证后，以 `ZipFile` 从磁盘按需读取。它直接追加到 `Minecraft.refreshResources()` 的临时列表末尾，不加入资源包仓库，因此不会出现在资源包选择菜单。

ASM 排序值为 2000，高于 TX Loader 的 1001，确保合并后的官方翻译覆盖 TX Loader 原有资源。切换到英语等不受支持语言时，下一次资源刷新不会插入该包。

GregTech 等模组会在初始化阶段缓存部分本地化名称；语言专用配置文件虽然会提前安装，但游戏内切换语言后仍可能需要重启才能让这些模组完全刷新。

## 失败与回退

- 下载必须符合清单大小与 SHA-256。
- 新 ZIP 全部验证且白名单文件写入成功后才替换当前实例。
- 状态保存 GTNH 版本、发布名、组件 ID、哈希和语言列表。
- 离线时只加载与当前 GTNH 版本一致的最后可用包。
- 专用服务端跳过整个客户端更新流程。
