<p align="center">
 <img width="256" height="256" alt="icon" src="https://github.com/user-attachments/assets/3fad6211-ecfd-4e22-b8b9-e4bdeecf639b" />

</p>

<h1 align="center">DocScan 文档扫描</h1>

<p align="center">
  没有广告的 Android 文档扫描 App。扫描、清理画质、保存成 PDF 或 JPG，并把印刷体或手写文字识别成 Excel 表格。
</p>

<p align="center">
  <a href="https://github.com/jtliaw/DOCscan/actions/workflows/build.yml"><img src="https://github.com/jtliaw/DOCscan/actions/workflows/build.yml/badge.svg" alt="Build APK"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84" alt="Android 10+">
</p>

<p align="center"><a href="README.md">English</a> | <b>简体中文</b></p>

---

## 功能

### 拍摄与导入
- **相机**：有两种模式，在“设置 → 相机模式”里选择：
  - **Google 自动扫描**：实时检测边缘、自动拍摄、支持多页（最多 20 页）。
  - **手机相机＋自己框选**：用手机相机拍照，App 自动找出文件位置，再由你调整选择框。
- **图片**：从相册选一张或多张照片（最多 20 张），自动检测文件位置，再由你微调。
- **手动选择框**：可以拖动四个角、边上的点，或拖动中间的点整体移动选择框。拖动时有 4 倍**放大镜**，显示手指下面被挡住的地方。**自动裁剪**会重新检测（每按一次换一种方法），**不裁剪**则使用整张图。
- 首页右上角的 ⋮ 菜单里有**导入 PDF**和**导入图像（不裁剪）**（最多 50 页）。
- **手写单 → Excel**：扫描手写单，AI 识别，修改后导出 Excel。

### 自己选择保存哪一种
保存之前可以预览三种效果，选一种保存，只会保存你选的那一种。

| 版本 | 效果 |
|---|---|
| **原图** | 拍摄的原样，只做了拉直。 |
| **优化** | 纸变白、去掉阴影，颜色会保留（印章、彩色笔迹）。 |
| **黑白** | 白底黑字，清晰，可直接打印。 |

在同一页，还可以对任何一页**重新调整选择框**、**继续添加页面**（+ 相机 / + 图片），或直接**打印**预览。

### 文档库
- 按标题或识别出的文字搜索。
- 列表或网格显示，可排序（最新、最早、名称、页数多的在前）。
- 每份文档右边的 ⋮ 菜单可以重命名、删除。
- **选择**多份文档（⋮ 菜单，或长按其中一份），一次删除。
- 文档以**页面缩略图**显示，点一页可以放大查看，也可以随时往已保存的文档里**添加页面**。

### 分享、保存、打印
- **分享**和**保存**进入同一个页面，在这里选择**格式**（PDF 或 JPG）、**文件名称**和**文件大小**（实际大小、大、中、小）。
- 分享时会列出手机里能接收这种文件的应用；保存则存到“下载”文件夹。
- 多页文档存成 JPG 时，每一页是一张图片。
- **打印**使用 Android 自带的打印界面。

### 文字与 Excel
- 只有点“**文字转 Excel**”才会显示文字，文档页面平时保持干净。
- 两种识别方式（“设置 → 识别文字的方式”）：

  | 方式 | 适合 | 联网 | 费用 |
  |---|---|---|---|
  | **本地**（ML Kit） | 印刷体 | 不需要 | 免费 |
  | **AI 云端**（Google Gemini） | 手写、潦草、多语言混合 | 需要 | 使用你自己的 API 密钥，请查看 Google 的价格和免费额度 |

- 识别结果可以编辑，用 `|` 分隔栏位，再导出标准的 `.xlsx` 文件。
- **选择模型**按钮会列出 Google 目前提供的 Gemini 模型，某个模型停用时可以换一个。

### 其他
- 黑色主题，中英文界面（跟随手机系统语言）。
- **没有广告、没有追踪、没有后台服务**，并有“退出”按钮可以完全关闭 App。

## 系统要求

- Android 10（API 29）或更高版本
- 手机里要有 Google Play 服务（ML Kit 文档扫描器需要）
- ARM 手机（arm64-v8a 或 armeabi-v7a）

## 获取 App

### 方式一：用 GitHub Actions 云端构建（不需要在电脑上安装开发环境）

1. 打开本仓库的 **Actions** 页面。
2. 打开最近一次成功的 **Build APK** 运行记录。
3. 下载 **DocScan-apk** 并解压。
4. 把 `app-debug.apk` 传到手机安装（可能需要允许“安装未知来源应用”）。

### 方式二：用 Android Studio 构建

1. 用 Android Studio 打开项目文件夹。
2. 等 Gradle 同步完成后，选择 **Build → Build APK(s)**，或运行 `gradle assembleDebug`。

> `gradle.properties` 里调高了 Gradle 的内存限制。ML Kit 和 OpenCV 库都很大，默认内存可能在构建时出现 `OutOfMemoryError`。OpenCV 也会让 APK 明显变大。

## 使用 AI 模式

1. 在 [Google AI Studio](https://aistudio.google.com/) 创建 Gemini API 密钥。
2. 在 App 里打开**设置**，选择 **AI 云端**并粘贴密钥。默认模型是 `gemini-flash-lite-latest`，点“**选择模型**”可以换别的。
3. 扫描文档，点“**文字转 Excel**”，修改错字，再生成 Excel。

> 名称以 `-latest` 结尾的模型，会一直指向 Google 该类型的最新模型，旧版本停用后也能继续用。

## 隐私

- **本地模式**不会把文档发送到任何地方。
- **AI 模式**会把扫描的图片上传到 Google 的 Gemini 接口。涉及机密的文件请谨慎使用。
- API 密钥只保存在你的手机上（App 的私有设置里），**没有加密**。请不要分享装有你密钥的 APK 或手机，也不要把密钥提交到本仓库。
- App 只在 AI 模式和查询模型列表时才用到联网权限。
- 文档保存在 App 内部，**卸载 App 会一起删除**，重要的文件请另外保存成 PDF 或 JPG。

## 已知限制

- 本地识别支持**中文和拉丁字母**（例如英文、马来文），其他文字请用 AI 模式。
- 本地识别对手写体效果较差。
- 表格结构是根据文字位置推测的，复杂表格需要在编辑框里检查。
- AI 也可能认错缩写和潦草的字（例如把 `D/O` 认成 `PO`），所以一定要检查结果。
- 背景杂乱时，自动检测文件位置可能失败，这时请自己拖动选择框。
- Google 自动扫描自带的选择框只能拖动四个角。
- 目前还没有回收站，也不能单独删除或调整某一页的顺序。
- Android 的打印界面只能找到手机能发现的打印机，只有 USB 接口的打印机不会出现。

## 技术栈

Kotlin · Jetpack Compose（Material 3）· Google ML Kit（文档扫描、文字识别）· OpenCV（边缘检测、GrabCut）· Gemini REST API · 内置的简易 PDF 和 XLSX 生成器（不依赖额外库）

## 项目结构

```
DocScan/
├─ .github/workflows/build.yml        # 云端构建 APK
├─ app/src/main/
│  ├─ java/com/example/docscan/
│  │  ├─ MainActivity.kt              # 各个界面和主要流程
│  │  ├─ DocStore.kt                  # 已保存的文档
│  │  ├─ DocDetect.kt                 # 自动检测文件位置（OpenCV）
│  │  ├─ Enhance.kt                   # 优化 / 黑白版本、透视拉直
│  │  ├─ GeminiOcr.kt                 # 调用 Gemini 接口、查询模型列表
│  │  ├─ PdfWriter.kt                 # PDF 生成器（直接嵌入 JPEG 页面）
│  │  └─ XlsxWriter.kt                # .xlsx 生成器
│  ├─ res/values/                     # 英文文本、主题、颜色
│  ├─ res/values-zh/                  # 简体中文文本
│  └─ AndroidManifest.xml
├─ build.gradle.kts
├─ settings.gradle.kts
└─ gradle.properties
```

## 第三方组件

本 App 使用了 Google ML Kit、OpenCV 和 Gemini 接口，请遵守它们各自的授权和条款。


<p align="center">
 <img width="256" height="256" alt="icon" src="https://github.com/user-attachments/assets/ad7fe250-154f-4919-ae2d-947c7bb83b4d" />

</p>

<h1 align="center">DocScan</h1>

<p align="center">
  An ad-free Android document scanner. Scan, clean up, save as PDF or JPG, and turn printed or handwritten text into an Excel sheet.
</p>

<p align="center">
  <a href="https://github.com/jtliaw/DOCscan/actions/workflows/build.yml"><img src="https://github.com/jtliaw/DOCscan/actions/workflows/build.yml/badge.svg" alt="Build APK"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84" alt="Android 10+">
</p>

<p align="center"><b>English</b> | <a href="README.zh-CN.md">简体中文</a></p>

---

## Features

### Capture
- **Camera** – two modes, chosen in *Settings → Camera mode*:
  - **Google auto scanner** – live edge detection, auto-capture and multiple pages (up to 20).
  - **Phone camera + my frame** – take the photo with your camera, then the app finds the page and lets you adjust the frame yourself.
- **Image** – pick one or more photos from the gallery (up to 20). The page is detected automatically, then you can fine-tune it.
- **Manual frame** – drag the four corners, the edge dots, or the centre dot to move the whole frame. A 4× **magnifier** shows what is under your finger. **Auto crop** searches again (each tap tries a different method) and **No crop** uses the whole image.
- **Import PDF** and **Import images (no crop)** from the ⋮ menu on the home screen (up to 50 pages).
- **Handwritten slip → Excel** – scan a handwritten slip, AI reads it, you correct it, then export to Excel.

### Choose how each scan is saved
Before anything is saved you can preview three versions and pick one. Only the version you choose is kept.

| Version | What it does |
|---|---|
| **Original** | The photo as taken, only straightened. |
| **Enhanced** | White paper and no shadows, but colours are kept (stamps, coloured ink). |
| **B&W** | Black text on pure white. Sharp and print-ready. |

On the same page you can **adjust the frame again** for any page, **add more pages** (+ Camera / + Image), or **print** the preview.

### Library
- Search by title or by recognized text.
- List or grid view, and sorting (newest, oldest, name, most pages).
- Rename or delete from the ⋮ menu on each document.
- **Select** several documents (⋮ menu, or long-press one) and delete them in one go.
- A document opens as **page thumbnails**; tap a page to view it. You can **add pages** to a saved document at any time.

### Share, save and print
- **Share** and **Save** open one page where you choose the **format** (PDF or JPG), the **file name** and the **file size** (actual, large, medium, small).
- Sharing shows the apps on your phone that can receive the file; saving puts it in your Downloads folder.
- JPG of a multi-page document is exported as one image per page.
- **Print** uses Android's built-in print dialog.

### Text and Excel
- **Text → Excel** only appears when you tap it, so the document screen stays clean.
- Two engines (*Settings → Recognition mode*):

  | Mode | Best for | Internet | Cost |
  |---|---|---|---|
  | **On-device** (ML Kit) | Printed text | Not needed | Free |
  | **AI** (Google Gemini) | Handwriting, messy or mixed-language text | Required | Uses your own API key; see Google's pricing and free-tier limits |

- Recognized text is editable. Use `|` to separate columns, then export a standard `.xlsx` file.
- A **model picker** lists the Gemini models Google currently offers, so you can switch if one is retired.

### Other
- Black (dark) theme, English and Simplified Chinese interface (follows the phone language).
- **No ads, no tracking, no background service**, and a **Quit** button that closes the app completely.

## Requirements

- Android 10 (API 29) or newer
- Google Play services (required by the ML Kit Document Scanner)
- 64-bit or 32-bit ARM phone (arm64-v8a, armeabi-v7a)

## Get the app

### Option 1: build with GitHub Actions (no computer setup needed)

1. Open the **Actions** tab of this repository.
2. Open the latest successful **Build APK** run.
3. Download the **DocScan-apk** artifact and unzip it.
4. Copy `app-debug.apk` to your phone and install it (you may need to allow installs from unknown sources).

### Option 2: build with Android Studio

1. Open the project folder in Android Studio.
2. Let Gradle sync, then run **Build → Build APK(s)**, or run `gradle assembleDebug`.

> `gradle.properties` raises the Gradle memory limit. The ML Kit and OpenCV libraries are large, and the default limit can cause an `OutOfMemoryError` during the build. OpenCV also makes the APK noticeably bigger.

## Using AI mode

1. Create a Gemini API key in [Google AI Studio](https://aistudio.google.com/).
2. In the app open **Settings**, choose **AI (cloud)** and paste the key. The default model is `gemini-flash-lite-latest`; tap **Choose a model** to pick another one.
3. Scan a document, tap **Text → Excel**, correct any mistakes, then create the Excel file.

> Names ending in `-latest` always follow Google's newest model of that kind, so they keep working when older versions are retired.

## Privacy

- **On-device mode** never sends your documents anywhere.
- **AI mode** uploads the scanned images to Google's Gemini API. Do not use it for confidential documents unless you are comfortable with that.
- The API key is stored only on your phone, in the app's private preferences. It is **not encrypted**, so do not share an APK or phone that contains your key, and never commit a key to this repository.
- The app asks for the internet permission only for AI mode and the model list.
- Documents are stored inside the app. **Uninstalling the app deletes them**, so save important files as PDF or JPG.

## Known limitations

- On-device recognition handles **Chinese and Latin letters** (for example English and Malay). Other scripts need AI mode.
- On-device recognition is weak on handwriting.
- Table structure is guessed from text positions. Complex tables need checking in the editor.
- AI can still misread abbreviations and untidy handwriting (for example `D/O` read as `PO`), so always review the result.
- Automatic page detection can fail on cluttered backgrounds. Drag the frame yourself in that case.
- The Google auto scanner's own frame can only be adjusted by the four corners.
- There is no recycle bin yet, and single pages cannot be deleted or reordered yet.
- Android's print dialog only lists printers the phone can find. USB-only printers will not appear.

## Built with

Kotlin · Jetpack Compose (Material 3) · Google ML Kit (Document Scanner, Text Recognition) · OpenCV (edge detection, GrabCut) · Gemini REST API · small built-in PDF and XLSX writers (no extra libraries)

## Project structure

```
DocScan/
├─ .github/workflows/build.yml        # cloud APK build
├─ app/src/main/
│  ├─ java/com/example/docscan/
│  │  ├─ MainActivity.kt              # screens and app flow
│  │  ├─ DocStore.kt                  # saved documents
│  │  ├─ DocDetect.kt                 # automatic page detection (OpenCV)
│  │  ├─ Enhance.kt                   # Enhanced / B&W versions, perspective straightening
│  │  ├─ GeminiOcr.kt                 # Gemini requests and model list
│  │  ├─ PdfWriter.kt                 # PDF writer (embeds the JPEG pages)
│  │  └─ XlsxWriter.kt                # .xlsx writer
│  ├─ res/values/                     # English text, theme, colours
│  ├─ res/values-zh/                  # Simplified Chinese text
│  └─ AndroidManifest.xml
├─ build.gradle.kts
├─ settings.gradle.kts
└─ gradle.properties
```


## Third-party components

This app uses Google ML Kit, OpenCV and the Gemini API. Please follow their own licences and terms.
