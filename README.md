# Android 16 悬浮窗应用

## 项目概述
这是一个 Android 16 (API 36) 应用程序，使用 Kotlin 语言开发，实现了悬浮窗功能，可以显示实时时间和电量信息。

## 功能特点
- 检测并请求悬浮窗权限
- 显示半透明黑色背景的悬浮窗
- 实时显示系统时间（HH:mm:ss 格式）
- 显示当前电量，并根据电量级别动态改变颜色
  - 电量 > 60%：绿色
  - 电量 20%-60%：黄色
  - 电量 < 20%：红色
- 长按悬浮窗 3 秒后关闭应用

## 开发环境
- IDE: Android Studio Panda (2024.3+)
- Target SDK: API 36 (Android 16)
- 语言: Kotlin

## 项目结构
```
app/
├── src/
│   └── main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/floatingwindowapp/
│       │   ├── MainActivity.kt
│       │   └── FloatingWindowService.kt
│       └── res/
│           ├── drawable/
│           ├── layout/
│           │   └── floating_window_layout.xml
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── values-v36/
│               └── themes.xml
└── build.gradle.kts
```

## 使用说明
1. 在 Android Studio 中打开项目
2. 连接 Android 设备或启动模拟器
3. 运行应用
4. 首次运行时授予悬浮窗权限
5. 悬浮窗将显示在屏幕上，显示时间和电量信息
6. 长按悬浮窗 3 秒可关闭应用

## 注意事项
- 本应用需要悬浮窗权限才能正常运行
- 在 Android 16 上，悬浮窗权限的请求方式有所变化，需要适配新的 API
- 长按功能需要在悬浮窗上实现
