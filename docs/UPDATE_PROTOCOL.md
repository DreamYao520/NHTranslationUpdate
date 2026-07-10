# Update protocol v1

`manifest.json` 是 UTF-8 JSON，最大 1 MiB。客户端只接受 `schemaVersion: 1` 与 `minecraftVersion: "1.7.10"`。

```json
{
  "schemaVersion": 1,
  "release": "2.8.4-cn.1",
  "minecraftVersion": "1.7.10",
  "packVersions": ["2.8.4"],
  "artifacts": [
    {
      "id": "gtnh-zh-cn-resource-pack",
      "kind": "resource_pack",
      "url": "https://example.org/releases/2.8.4-cn.1/gtnh-zh-cn-resource-pack.zip",
      "sha256": "64 lowercase hexadecimal characters",
      "size": 123456,
      "required": true
    }
  ]
}
```

## Artifact kinds

### `resource_pack`

ZIP 必须在根目录包含 `pack.mcmeta`，并至少包含一个 `assets/` 下的文件。安装目标固定为 `resourcepacks/NHTranslationUpdate.zip`，远端不能修改目标路径。Minecraft 1.7.10 使用 `pack_format: 1`。

### `overlay`

ZIP 内路径相对于游戏实例根目录。所有文件都必须位于本地配置的 `allowedOverlayRoots` 之一；默认不包括 `mods/`、`scripts/`、`saves/` 或实例外路径。

每个覆盖包有独立的 managed-file 索引。升级时：

1. 校验整个下载文件的 SHA-256；
2. 在缓存目录中完整验证和解压；
3. 备份目标位置已有且内容不同的文件；
4. 逐文件原子替换；
5. 仅删除上版归属索引中、当前内容仍等于上版哈希的陈旧文件；
6. 原子更新归属索引。

## Failure behavior

- 清单或下载失败：保留现有汉化，游戏继续启动。
- SHA-256、大小、ZIP 路径或解压上限不符：拒绝该组件。
- `packVersion` 已配置且不在 `packVersions` 中：拒绝整份清单。
- `packVersion` 为空：允许跟随最新版本，但日志会提示无法做精确兼容性检查。

## Trust boundary

SHA-256 防止下载损坏，也把安装内容绑定到清单，但它不证明发布者身份。真正的信任根是 HTTPS 清单地址及其托管账号。长期运营时应保护 GitHub 账号、限制 Actions 写权限、启用分支保护，并在发布工作流中固定第三方 Action 的提交 SHA。
