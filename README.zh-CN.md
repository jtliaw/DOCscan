<p align="center">
  <img src="docs/icon.png" width="128" alt="DocScan 图标">
</p>

<h1 align="center">DocScan 文档扫描</h1>

<p align="center">
  扫描文档，保存为 PDF，并把印刷体或手写文字识别成可编辑的 Excel 表格。
</p>

<p align="center">
  <a href="https://github.com/jtliaw/DOCscan/actions/workflows/build.yml"><img src="https://github.com/jtliaw/DOCscan/actions/workflows/build.yml/badge.svg" alt="Build APK"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84" alt="Android 10+">
</p>

<p align="center"><a href="README.md">English</a> | <b>简体中文</b></p>

---

## 功能

- **文档扫描**：使用 Google ML Kit 文档扫描器，自动检测边缘、矫正透视、增强画质，支持多页（最多 20 页）和从相册导入。
- **文档库**：每次扫描都保存在 App 里，可按标题或识别出的文字搜索、重命名、删除，随时重新打开。
- **手写单 → Excel**：一键流程，扫描手写单，AI 识别，修改后导出 Excel。
- **没有广告、没有追踪、没有后台服务**，并有“退出”按钮可以完全关闭 App。
- **自己选择保存哪一种**：手动拖动四个角框住文件，再预览并选择**原图**、**优化**（纸变白、保留颜色）或**黑白文字**（清晰、可直接打印）。只会保存你选的那一种。
- **PDF 输出**：可以分享给任何 App、保存到“下载”文件夹，或用 Android 自带的打印界面打印。
- **两种识别文字的方式**，扫描前先选择：

  | 方式 | 适合 | 联网 | 费用 |
  |---|---|---|---|
  | **本地**（ML Kit） | 印刷体 | 不需要 | 免费 |
  | **AI 云端**（Google Gemini） | 手写、潦草、多语言混合 | 需要 | 使用你自己的 API 密钥，请查看 Google 的价格和免费额度 |

- **导出前可编辑**：识别结果显示在可编辑的文本框里，先改错字再导出。用 `|` 分隔栏位。
- **导出 Excel**：生成标准的 `.xlsx` 文件，可分享或保存到“下载”。
- **中英文界面**：跟随手机系统语言。

## 系统要求

- Android 10（API 29）或更高版本
- 手机里要有 Google Play 服务（ML Kit 文档扫描器需要）

## 获取 App

### 方式一：用 GitHub Actions 云端构建（不需要在电脑上安装开发环境）

1. 打开本仓库的 **Actions** 页面。
2. 打开最近一次成功的 **Build APK** 运行记录。
3. 下载 **DocScan-apk** 并解压。
4. 把 `app-debug.apk` 传到手机安装（可能需要允许“安装未知来源应用”）。

### 方式二：用 Android Studio 构建

1. 用 Android Studio 打开项目文件夹。
2. 等 Gradle 同步完成后，选择 **Build → Build APK(s)**，或运行 `gradle assembleDebug`。

> `gradle.properties` 里调高了 Gradle 的内存限制。ML Kit 库很大，默认内存可能在构建时出现 `OutOfMemoryError`。

## 使用 AI 模式

1. 在 [Google AI Studio](https://aistudio.google.com/) 创建 Gemini API 密钥。
2. 在 App 里选择 **AI 云端**，粘贴密钥。默认模型是 `gemini-flash-lite-latest`，可以修改。
3. 扫描文档，点击 **用 AI 识别文字**，修改错字，再生成 Excel。

## 隐私

- **本地模式**不会把文档发送到任何地方。
- **AI 模式**会把扫描的图片上传到 Google 的 Gemini 接口。涉及机密的文件请谨慎使用。
- API 密钥只保存在你的手机上（App 的私有设置里），没有加密。请不要分享装有你密钥的 APK 或手机。
- App 只在 AI 模式下才用到联网权限。

## 已知限制

- 本地识别支持**中文和拉丁字母**（例如英文、马来文），其他文字请用 AI 模式。
- 本地识别对手写体效果较差。
- 表格结构是根据文字位置推测的，复杂表格需要在编辑框里检查。
- AI 也可能认错缩写和潦草的字（例如把 `D/O` 认成 `PO`），所以一定要检查结果。

## 技术栈

Kotlin · Jetpack Compose · Google ML Kit（文档扫描、文字识别）· Gemini REST API · 内置的简易 XLSX 生成器（不依赖额外库）

## 项目结构

```
DocScan/
├─ .github/workflows/build.yml        # 云端构建 APK
├─ app/src/main/
│  ├─ java/com/example/docscan/
│  │  ├─ MainActivity.kt              # 界面和主要流程
│  │  ├─ GeminiOcr.kt                 # 调用 Gemini 接口
│  │  └─ XlsxWriter.kt                # 简易 .xlsx 生成器
│  ├─ res/values/strings.xml          # 英文文本
│  ├─ res/values-zh/strings.xml       # 简体中文文本
│  └─ AndroidManifest.xml
├─ build.gradle.kts
├─ settings.gradle.kts
└─ gradle.properties
```

## 计划

- 自选保存位置
- 更多界面语言（例如马来文）
- 可搜索文字的 PDF
- 可选功能：通过电脑上的小型中转程序，打印到共享的网络打印机
