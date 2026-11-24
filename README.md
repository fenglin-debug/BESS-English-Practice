# 储能英语实战 (BESS English Practice)

一款**轻框架、零内置语料、离线优先**的 Android 储能行业英语词汇学习 App，基于 Google *Now in Android* 现代架构（Kotlin + Jetpack Compose + Material 3 + Room + Hilt）与开源 **FSRS-5** 科学间隔重复算法构建。

## 核心特点

- **安装包本身不带语料**：APK 内无任何硬编码词条或静态词库文件；首次安装默认保持纯离线状态，**仅在用户明确点击同意联网更新后**，才从远程 GitHub 仓库拉取词库并缓存在本地。
- **零服务器成本热更新**：词库托管于本仓库 `vocab-repo/dist/`，客户端通过 `ETag` 与 `SHA-256` 按分类增量拉取，更新词库时**不覆盖、不丢失用户已有的 FSRS 学习进度**。
- **FSRS-5 闪卡 + 双轨发音**：实时预测 `忘记 / 困难 / 记得 / 简单` 下次复习间隔，支持在线音频缓存与离线 Android TTS 朗读（含 0.7x 慢速跟读）。

## 目录结构

```text
├── docs/储能行业英文词汇表.xlsx       # 原始 Excel 词库（8 大分类、162 条术语）
├── vocab-repo/
│   ├── scripts/build_vocab.py         # Excel -> 静态 JSON 词包一键构建脚本
│   └── dist/                          # 客户端联网同步的静态词库产物
│       ├── manifest.json
│       └── packs/*.json
└── android-app/                       # Android 原生工程（Kotlin + Jetpack Compose）
```

## 默认远程词库源

- **主源（jsDelivr CDN）**：`https://cdn.jsdelivr.net/gh/fenglin-debug/BESS-English-Practice@main/vocab-repo/dist/manifest.json`
- **备用源（GitHub Raw，主源异常时自动回退）**：`https://raw.githubusercontent.com/fenglin-debug/BESS-English-Practice/main/vocab-repo/dist/manifest.json`

## 如何更新词库并推送给所有 App

1. 编辑 `docs/储能行业英文词汇表.xlsx`（增删词条、修正翻译或新增 `▌分类名`）。
2. 运行脚本重新生成静态词包：
   ```powershell
   python vocab-repo/scripts/build_vocab.py
   ```
3. 提交并推送至 `main` 分支：
   ```powershell
   git add docs/ vocab-repo/dist/
   git commit -m "feat: update BESS vocabulary"
   git push
   ```
4. 已授权联网更新的手机客户端点击「检查更新」即可增量同步最新词汇。
