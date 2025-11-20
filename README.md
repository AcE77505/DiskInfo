# DiskInfo

<div align="center">

📱 **DiskInfo** - Android 设备分区信息查看工具

*在 Android 设备上轻松查看分区详细信息*

</div>

## 📖 应用简介

DiskInfo 是一款借助 AI 开发的 Android 应用，专门用于查看设备上的分区信息。无需 Root 权限即可查看基本的分区数据，帮助开发者和技术爱好者了解设备存储结构。

## ✨ 功能特性

- 🔍 **分区信息查看**：显示设备所有存储分区
- 📊 **详细信息展示**：
  - 分区名称
  - 设备路径
  - 文件系统类型
  - 空间占用情况
- 📱 **广泛兼容**：支持 Android 5.0+ 系统
- 🎯 **简洁界面**：直观的信息展示方式

## ⚠️ 已知限制

### 版本兼容性问题
- **Android 9 及以下版本**：可能存在无法解析分区名和占用情况的问题
- **SELinux 限制**：当前版本需要在 SELinux Permissive 模式下运行(后续版本会通过root获取信息解决问题)

### 当前版本限制
- 仅支持在 **SELinux Permissive** 模式下获取完整信息
- 在 **SELinux Enforcing** 设备上可能遇到：
  - 分区列表不显示
  - 分区名称无法解析
  - 占用情况获取失败

## 🚀 下载安装

### 最新版本下载
前往 [Releases 页面](https://github.com/AcE77505/DiskInfo/releases) 下载最新的 APK 文件。

### 系统要求
- **Android 版本**: 5.0 (API 21) 及以上
- **架构支持**: 不限
