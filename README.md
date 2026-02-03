# Pdf_Reader

一个基于 Kotlin + Jetpack Compose 的 Android PDF 阅读器示例，支持书架导入、连续滚动阅读、缩放、日夜模式、语音朗读与全屏阅读。

## 功能

- 书架：SAF 导入 PDF、封面缩略图、两列网格展示
- 阅读：连续滚动、缩放、白天/黑色模式（PDF 页面反色）
- 语音：朗读当前页（基于 PDFBox-Android 文本提取 + Android TTS）
- 全屏：沉浸阅读，单击切换工具栏显示

## 构建

- Android Studio：Giraffe/或更新版本
- JDK：17
- minSdk：29

打开工程后执行 Gradle Sync，然后运行到真机/模拟器即可。
