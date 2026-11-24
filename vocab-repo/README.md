# 储能英语实战 · 远程词库仓库 (`vocab-repo`)

本目录负责将 `docs/储能行业英文词汇表.xlsx` 自动编译为静态 JSON 词库包（`dist/manifest.json` + `dist/packs/*.json`），供 Android 客户端「储能英语实战」在用户同意联网更新后进行增量同步。

## 1. 本地一键生成词库包

```powershell
python vocab-repo/scripts/build_vocab.py
```

## 2. 已绑定的 GitHub 仓库远程地址

App 默认已内置绑定仓库 `fenglin-debug/BESS-English-Practice`：
- **主源（jsDelivr CDN，国内访问更快）**：
  `https://cdn.jsdelivr.net/gh/fenglin-debug/BESS-English-Practice@main/vocab-repo/dist/manifest.json`
- **备用源（GitHub Raw，主源不可达时自动回退）**：
  `https://raw.githubusercontent.com/fenglin-debug/BESS-English-Practice/main/vocab-repo/dist/manifest.json`

直接把整个项目目录推送到 `https://github.com/fenglin-debug/BESS-English-Practice.git` 的 `main` 分支即可生效。

## 3. 本地局域网联调测试

```powershell
python -m http.server 8000 --directory vocab-repo/dist
```

- **Android 模拟器**：在 App「设置」中点「模拟器本地源」（`http://10.0.2.2:8000/manifest.json`）
- **真机（同一 Wi-Fi）**：填写 `http://<电脑局域网IP>:8000/manifest.json`
