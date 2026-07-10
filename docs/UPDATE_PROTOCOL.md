# Update protocol v2

`manifest.json` 是 UTF-8 JSON，最大 1 MiB。客户端只接受 `schemaVersion: 2` 与 `minecraftVersion: "1.7.10"`。

```json
{
  "schemaVersion": 2,
  "release": "2.8.4-cn.1",
  "minecraftVersion": "1.7.10",
  "packVersions": ["2.8.4"],
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
```

## Artifact kind: `translation`

唯一支持的 artifact 类型。一个统一的 ZIP 包，包含所有翻译资源，由 `NHTranslationResourcePack`（自定义 `IResourcePack`）在内存中加载并提供给 Minecraft。

### ZIP 内部结构

```
assets/{domain}/{path}   — 标准 Minecraft 资源（lang 文件等）
txloader/{domain}/{path}  — TX Loader 资源路径
```

`NHTranslationResourcePack` 对每个 `ResourceLocation` 先查找 `assets/domain/path`，再回退到 `txloader/domain/path`。

### 注入机制

通过 ASM 字节码变换（`@SortingIndex(2000)`），在 TX Loader（1001）之后、`Minecraft.reloadResources()` 调用之前，将翻译资源包插入到资源包链的**最末尾**。这意味着 TX Loader 的 `forceload` 资源包已被加入列表后，我们的包才追加进去，因此拥有最高优先级。

### 服务端

CoreMod 检测到服务端时不注入 ASM transformer（ASM 变换仅对客户端生效）。`UpdateBootstrap` 仍会在服务端运行并下载更新，但不会加载资源包。

## Failure behavior

- 清单或下载失败：保留现有汉化（`NHTranslationResourcePack.INSTANCE` 仍然指向上一版），游戏继续启动。
- SHA-256、大小或 ZIP 解压上限不符：拒绝该组件。
- `packVersion` 已配置且不在 `packVersions` 中：拒绝整份清单。
- `packVersion` 为空：允许跟随最新版本，但日志会提示无法做精确兼容性检查。

## Trust boundary

SHA-256 防止下载损坏，也把安装内容绑定到清单，但它不证明发布者身份。真正的信任根是 HTTPS 清单地址及其托管账号。长期运营时应保护 GitHub 账号、限制 Actions 写权限、启用分支保护，并在发布工作流中固定第三方 Action 的提交 SHA。
