# 搜题王 v0.1

本项目是一个离线 Android 刷题/搜题工具，支持本地导入 `.xlsx` 题库、预览异常行、保存多个题库、按题干或答案搜索，并一键复制答案。

## 功能

- Android Kotlin + Jetpack Compose
- Room 本地数据库，无服务器依赖
- Android 系统文件选择器导入 `.xlsx`
- 自动识别题干、答案、选项 A-F
- 导入前展示题库名称、总行数、可导入数量、异常行和题目预览
- 自动推断选择题、判断题、填空题
- 搜索全部题库或指定题库，标注匹配题干/答案
- 搜索结果展示题干、选项、答案、题型、所属题库
- 支持复制答案到剪贴板

## 构建

本仓库已提供标准 Gradle Android 工程文件。安装 Android Studio 后打开项目，等待依赖同步，然后通过 Android Studio 运行或打包。

如果本机已安装 Gradle、JDK 和 Android SDK，也可以运行：

```powershell
gradle assembleDebug
```

当前工作环境没有可用的 Gradle、Android SDK 和 JDK 环境变量，因此本机未执行 APK 编译验证，也未生成 Gradle Wrapper。

## 示例题库

示例文件位于：

```text
samples/sample_questions.xlsx
```

如果需要重新生成示例文件，可以运行：

```powershell
python tools/create_sample_xlsx.py
```

## v0.1 不包含

- OCR
- 悬浮窗
- 截屏识别
- 登录注册
- 云同步
- 付费或激活码
- AI 搜题
