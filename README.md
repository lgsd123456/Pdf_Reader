# Pdf_Reader

一个基于 Kotlin + Jetpack Compose 的 Android PDF 阅读器示例，支持书架导入、封面缩略图、连续滚动阅读、缩放、日夜模式、语音朗读与沉浸式全屏阅读。

## 功能

- 书架
  - SAF 选择/导入 PDF
  - 两列网格展示
  - PDF 首页面渲染为封面缩略图（内存 + 磁盘缓存）
- 阅读
  - 连续滚动阅读（按页渲染）
  - 缩放（横向滚动仅在放大时启用）
  - 白天/黑色模式（黑色模式对页面做反色显示）
- 语音朗读
  - 朗读当前页（PDFBox-Android 提取文本 + Android TTS）
  - 为了首屏速度，PDF 文本解析延迟到首次点击朗读时才加载
- 沉浸式全屏
  - 隐藏状态栏/导航栏，并支持铺满挖孔/刘海区域
  - 单击页面呼出/隐藏底部控制条（自动隐藏）

## 技术栈

- UI：Jetpack Compose + Material3 + Navigation-Compose
- 数据：Room（记录书籍信息与阅读进度）
- PDF 渲染：Android `PdfRenderer`（按页渲染 Bitmap）
- 文本提取：`com.tom-roush:pdfbox-android`
- 语音：Android `TextToSpeech`
- 设置：SharedPreferences（缩放倍率、深色模式、全屏开关）

## 实现说明

### 1) 书架与导入

- 通过 SAF 打开 PDF（`OpenDocument`），对选中文件调用 `takePersistableUriPermission` 持久化读取权限，避免下次打开丢权限。
- 书籍信息持久化到 Room：标题、Uri、总页数、上次阅读页等。
- 导入时读取页数属于 IO 操作，放在后台线程执行，减少卡顿。

相关代码：
- [MainActivity.kt](file:///d:/bytedance/workspace/Pdf_Reader/app/src/main/java/com/example/pdfreader/MainActivity.kt)
- [BooksRepository.kt](file:///d:/bytedance/workspace/Pdf_Reader/app/src/main/java/com/example/pdfreader/data/BooksRepository.kt)

### 2) 封面缩略图（性能优化）

- 缩略图来自第 1 页渲染结果，采用两级缓存：
  - 内存 LRU：滚动书架更顺滑
  - 磁盘缓存：再次进入书架几乎秒开（缓存目录位于 app cache）
- 缩略图生成带并发限流，避免同时渲染大量 PDF 导致卡顿。

相关代码：
- [PdfThumbnailCache.kt](file:///d:/bytedance/workspace/Pdf_Reader/app/src/main/java/com/example/pdfreader/ui/PdfThumbnailCache.kt)
- [BookshelfScreen.kt](file:///d:/bytedance/workspace/Pdf_Reader/app/src/main/java/com/example/pdfreader/ui/BookshelfScreen.kt)

### 3) 阅读渲染与缩放

- 使用 `PdfRenderer` 按页渲染 Bitmap 并在 Compose 中显示。
- `PdfRenderer` 同时只能打开一个 `Page`：渲染时用互斥锁串行化 `openPage/render/close`，避免崩溃（Current page not closed）。
- 缩放时通过调整内容宽度来放大页面，只有在 `zoom > 1` 时启用横向滚动，避免出现“内容超出/露白边”的问题。

相关代码：
- [ReaderScreen.kt](file:///d:/bytedance/workspace/Pdf_Reader/app/src/main/java/com/example/pdfreader/ui/ReaderScreen.kt)

### 4) 日夜模式与全屏

- 日夜模式：
  - Compose 侧切换 `lightColorScheme/darkColorScheme`
  - PDF 页面使用颜色矩阵做反色，获得黑底效果
- 全屏：
  - 隐藏系统栏并允许内容延伸到挖孔/刘海区域
  - 全屏下不显示顶部标题栏，单击页面显示底部控制条

相关代码：
- [SettingsRepository.kt](file:///d:/bytedance/workspace/Pdf_Reader/app/src/main/java/com/example/pdfreader/data/SettingsRepository.kt)
- [ReaderScreen.kt](file:///d:/bytedance/workspace/Pdf_Reader/app/src/main/java/com/example/pdfreader/ui/ReaderScreen.kt)
- [themes.xml](file:///d:/bytedance/workspace/Pdf_Reader/app/src/main/res/values/themes.xml)

## 构建

- Android Studio：Giraffe/或更新版本
- JDK：17
- minSdk：29

打开工程后执行 Gradle Sync，然后运行到真机/模拟器即可。

## 已知说明

- 不同厂商 ROM 对沉浸式手势条/系统栏行为可能略有差异，已使用推荐方式尽可能做到“全屏铺满”。
