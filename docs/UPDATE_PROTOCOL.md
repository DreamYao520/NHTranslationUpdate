# Update protocol v3

`manifest.json` 是 UTF-8 JSON，最大 1 MiB。客户端只接受 `schemaVersion: 3` 与 `minecraftVersion: "1.7.10"`。

```json
{
  "schemaVersion": 3,
  "minecraftVersion": "1.7.10",
  "packs": {
    "2.8.4": {
      "release": "2.8.4-cn.1",
      "artifacts": [
        {
          "id": "gtnh-zh-cn-translation",
          "kind": "translation",
          "url": "https://example.org/releases/2.8.4-cn.1/gtnh-zh-cn-translation.zip",
          "sha256": "64 lowercase hexadecimal characters",
          "size": 123456,
          "required": true
        }
      ]
    }
  }
}
```

客户端检测当前 GTNH 版本后只做精确匹配。没有对应键时不安装其他版本的翻译。

## Translation ZIP

```text
assets/{domain}/{path}
install/config/GregTech_zh_CN.lang  # 可选、唯一允许写入实例的特殊文件
```

发布器在构建时把来源按以下顺序合并到 `assets` 命名空间：

1. `resources/[domain]/...`
2. `config/txloader/load/{domain}/...`
3. `config/txloader/forceload/{domain}/...`

同一路径的 `.lang` 文件按顺序拼接，因此后面的重复键覆盖前面的值，同时保留只存在于低优先级层的键。非语言文件由更高优先级层替换。

根目录的 `GregTech.lang` 会被发布为 `install/config/GregTech_zh_CN.lang`，客户端只允许把这个固定条目原子写入 `config/GregTech_zh_CN.lang`，不会通用解压 `install/` 目录。

## 加载与语言门控

ASM 变换的排序值为 2000，高于 TX Loader 的 1001。Hook 位于 `Minecraft.reloadResources(List)` 调用之前，所以 NHTranslationUpdate 的资源包最后加入列表，覆盖 TX Loader `forceload`。

Hook 每次刷新资源时读取 `gameSettings.language`：只有 `zh_CN` 才插入资源包。客户端不修改 `options.txt`。专用服务端直接跳过更新流程。

## 失败与回退

- 新包必须依次通过下载大小、SHA-256、ZIP 路径、条目数量和解压总量检查，全部成功后才替换内存实例。
- 状态文件记录最后一次成功包的 GTNH 版本、发布名、组件 ID 和哈希。
- 启动时先加载与当前 GTNH 版本相同的最后可用包，再尝试联网更新。
- 版本不同的缓存永远不会作为回退包加载。
- 清单、下载或新 ZIP 失败不会清空已加载的最后可用包。

## 信任边界

SHA-256 防止下载损坏并把内容绑定到清单，但发布者身份仍依赖 HTTPS 地址及托管账号。发布流程应保护 GitHub 账号、限制 Actions 权限、启用分支保护，并固定第三方 Action 的提交 SHA。
