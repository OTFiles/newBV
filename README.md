<div align="center">

<img src="app/src/main/res/drawable/ic_banner.webp" style="border-radius: 24px; margin-top: 32px;"/>

# newBV（AI 改写版）

**BV 的架构重构版 · 本仓库为 AI 改写分支**

[![Android Sdk Require](https://img.shields.io/badge/Android-5.0%2B-informational?logo=android)](https://apilevels.com/#:~:text=Jetpack%20Compose%20requires%20a%20minSdk%20of%2021%20or%20higher)
[![License](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)
[![Release](https://img.shields.io/github/v/release/OTFiles/newBV?label=release&logo=github)](../../releases)

**newBV 不支持在中国大陆地区内使用，如有相关使用需求请使用 [云视听小电视](https://app.bilibili.com)**

</div>

---

> ## ⚠️ 关于本仓库
>
> **本仓库是 [Frost819/newBV](https://github.com/Frost819/newBV) 的 fork，代码由 AI（大语言模型）改写/补充。**
>
> - 上游原作者：[aaa1115910](https://github.com/aaa1115910)（BV）、[Frost819](https://github.com/Frost819)（newBV 重构）
> - 本仓库的二次修改（发评论、边看边刷评论、日志一键复制等）**由 AI 生成**，未经原作者的代码审查
> - 使用前请自行评估风险，尤其涉及账号登录态与发评论等**会写服务端数据**的操作
> - 遇到问题请先对照 [上游仓库](https://github.com/Frost819/newBV)，**不要**向上游作者报 issue
> - 本项目仅供学习与技术交流，请勿用于商业用途

## 本 fork 的额外改动（AI 改写）

以下功能由 AI 在上游 v1.2.0 基础上新增：

| 功能 | 说明 |
|---|---|
| **发表评论 / 回复评论** | 评论弹窗底部新增输入栏，支持发一级评论与楼中楼回复（`/x/v2/reply/add`，Web 通道 + CSRF）。每条评论卡片带「回复」按钮，回复目标以提示条形式展示，可随时取消 |
| **边看边刷评论** | 设置 → 界面 → 「边看边刷评论」。开启后打开评论面板不暂停视频与弹幕；**默认关闭**，因为在低端电视上叠加解码 + 弹幕 + 评论列表容易掉帧 |
| **日志一键复制** | 设置 → 日志管理：点按日志条目直接复制**日志内容**（含下载地址），其下方条目只复制**下载地址**；另有「一键复制访问地址」复制日志服务器地址。单次复制上限 10 万字，超长自动截断并保留完整下载链接 |

## 上游说明

newBV 是基于 [BV](https://github.com/aaa1115910/bv) 重构的 [哔哩哔哩](https://www.bilibili.com) 第三方 `Android TV`
客户端，使用 `Jetpack Compose` 开发，支持 `Android 5.0+`（minSdk 21）。

**这不是 BV 的 1:1 复刻，而是架构重构 + 功能增强。保留了原版 BV 核心使用体验的同时，新增了许多呼声很高的功能，并重构了软件架构。**

> 原版 BV 的代码仍保留在上游仓库的 `bv-feature` 分支。

## 基于 BV 的升级点

### 功能升级

- **直播观看**：直播首页（推荐 / 分区 / 关注 / 直播排行榜）、直播播放（HLS/FLV，多线路手动切换）、HDR 档位（按设备解码能力过滤与展示）、WebSocket 实时弹幕、实时人气
- **评论**：详情页评论预览、独立评论弹窗（全部 / 热门、无限滚动、楼中楼、评论点赞）、根评论楼中楼预览、评论相对时间、回复手动分页、播放器内评论、**发表评论与楼中楼回复（AI 改写新增）**
- **缓存自动清理**：缓存阈值可调（50/100/200/500MB / 不限制）、LRU 自动清理、手动清理、日志占用查看及清理
- **触屏适配**：D-pad / 触屏运行时双模式，播放器手势（快进 / 亮度 / 音量）
- **主题**：黑夜 / 白天 / 跟随系统三档，全新品牌配色
- **视频卡片已播进度条**：历史 / 稍后再看 / UP 主页卡片显示观看进度
- **播放器增强**：点赞·投币·收藏快捷键（长按一键三连）、同时观看人数、自定义快捷键触发提示、起播自动降档与线路切换提示、打开详情 / UP 主空间返回后续播、弹幕分段加载（基于AkDanmaku自研 `danmaku-engine`）
- **诊断与崩溃**：关键操作诊断日志 + 局域网日志网页端；本地崩溃日志 + 可选自建端点上报

### 架构

- 单 Activity + Navigation-Compose
- 完全基于 StateFlow 重构，单向数据流（UDF）
- Hilt 依赖注入
- 仅 Media3 播放器，VOD + Live 双兼容
- 完全删除代理逻辑（ProxyArea / Ali CDN 等）
- 模块化拆分：`app` / `core` / `data` / `bili-api` / `player` / `danmaku` / `danmaku-engine` / `bili-subtitle`

### 性能

在 TV 1080p 模拟器（x86 / API 28）、release 混淆构建下与原版 BV 对比：

| 指标 | newBV | 原版 BV | 提升 |
|---|---|---|---|
| 冷启动 → 首页首帧 | 576 ms | 1076 ms | **快 46.5%**（约 1.87×） |
| Release APK 体积 | 7.20 MiB | 11.97 MiB | **小 39.8%**（约 1.66×） |

### 测试

- 补全单元测试
- 补全接口集成测试

## 构建

### 环境要求

- JDK 17
- Android SDK（compileSdk 36）

### GitHub Actions 构建（推荐）

本仓库自带 `.github/workflows/build-apk.yml`，**无需本地搭 Android 环境**：

1. 打开仓库 **Actions** → **Build APK** → **Run workflow**
2. 可选填版本前缀（默认 `0.0.0`），点击运行
3. 构建完成后在运行详情页底部的 **Artifacts** 下载 `newBV-apk`

产物为 **debug 签名**（`applicationIdSuffix=.debug`，包名 `dev.frost819.newbv.debug`），
可与官方 release 版共存安装。首次构建约 10–15 分钟，之后有 Gradle 缓存会更快。

## 开发文档

- [AGENTS.md](AGENTS.md) — AI 协作开发规范（架构决策、代码规范、测试策略、踩坑经验）
- [更新日志](../../releases) — 各版本发布说明与 APK 下载

## 致谢

- [aaa1115910/bv](https://github.com/aaa1115910/bv) — 本项目源自 BV 的重构
- [cat3399/blbl](https://github.com/cat3399/blbl) — 部分实现（弹幕分段加载等）参考 / 移植自该项目
- [akdanmaku](https://github.com/Frost819/AkDanmaku) — 弹幕渲染引擎

## License

[MIT](LICENSE) © aaa1115910, Frost819
