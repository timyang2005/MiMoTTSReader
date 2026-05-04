# MiMo TTS Reader

通过 [MiMo TTS v2.5](https://xiaomimimo.com/) API 为开源阅读APP（[Legado](https://github.com/gedoor/legado)）提供朗读引擎的 Android 应用。

## ✨ 功能特性

- 🎙️ **MiMo TTS v2.5 全功能支持** - 预置音色、音色设计、音色复刻三种模型
- 📖 **Legado 阅读APP 集成** - 一键导入朗读规则，无缝对接开源阅读
- 🎵 **9种预置音色** - 冰糖、茉莉、苏打、白桦、Mia、Chloe、Milo、Dean
- 🗣️ **方言支持** - 东北话、四川话、河南话、粤语
- 🎭 **风格控制** - 支持情感标签（开心/悲伤/温柔/高冷/唱歌等）和自然语言指令
- 🔄 **流式/非流式合成** - 支持两种 API 调用模式
- 🛡️ **服务保活** - 前台服务 + WakeLock + 电池优化白名单
- 🚀 **开机自启** - 可选开机自动启动 TTS 服务
- 📱 **纯原生架构** - 无需 Node.js 运行时，轻量高效

## 🏗️ 架构设计

本项目采用 **纯 Android 原生架构**，与 MiMoTTSLite 的 Android + Node.js 混合架构完全不同：

| 对比项 | MiMoTTSLite | MiMoTTSReader |
|--------|-------------|---------------|
| 运行时 | Node.js + Express | NanoHTTPD (纯Java) |
| 音频处理 | FFmpeg + lamejs | 纯 Kotlin 实现 |
| APK 体积 | ~80MB+ (含Node运行时) | ~5MB |
| 启动速度 | 较慢 (需启动Node进程) | 即时启动 |
| 内存占用 | 较高 | 低 |
| noexec 兼容 | 需要 linker64 绕过 | 无此问题 |

```
┌─────────────────────────────────────────┐
│         Android App (Kotlin)            │
│                                         │
│  MainActivity ←→ WebView UI             │
│       ↕ JavascriptInterface             │
│  TtsService (Foreground Service)        │
│       ↕                                 │
│  TtsServer (NanoHTTPD, port 9966)       │
│    ├─ POST /tts      ← Legado 端点      │
│    ├─ GET  /api/status                  │
│    ├─ GET  /api/legado/rule             │
│    └─ POST /api/test                    │
│       ↕                                 │
│  MiMoTtsClient (OkHttp)                 │
│    → https://api.xiaomimimo.com/v1/...  │
│    → Base64解码 → PCM→WAV 转换          │
└─────────────────────────────────────────┘
```

## 📖 使用方法

### 1. 安装应用

从 [Releases](../../releases) 下载最新 APK 安装包，或自行编译。

### 2. 配置 API Key

1. 打开应用
2. 在「基础配置」中填入你的 MiMo API Key
   - 获取方式：访问 [MiMo 开放平台](https://xiaomimimo.com/) 注册并获取 API Key
3. 选择音色、模型和风格
4. 点击「保存配置」

### 3. 启动服务

点击「启动服务」按钮，服务将在 `http://localhost:9966` 上运行。

### 4. 配置阅读APP（Legado）

**方式一：一键导入**
1. 在应用中点击「分享到阅读APP」
2. 选择阅读APP打开

**方式二：手动配置**
1. 复制应用中显示的朗读规则
2. 打开阅读APP → 我的 → 朗读引擎 → 在线朗读
3. 粘贴规则并保存

朗读规则格式：
```
http://localhost:9966/tts,{"method":"POST","body":"tex={{java.encodeURI(java.encodeURI(speakText))}}&spd={{String((speakSpeed+5)/10+4)}}&_res_tag_=audio"}
```

### 5. 开始朗读

在阅读APP的阅读界面，选择该朗读引擎即可使用。

## 🔧 编译

### 环境要求

- Android Studio Hedgehog | 2023.1.1+
- JDK 17
- Android SDK 34
- Min SDK 24 (Android 7.0)

### 编译步骤

```bash
git clone https://github.com/timyang2005/MiMoTTSReader.git
cd MiMoTTSReader
./gradlew assembleDebug
```

编译产物位于 `app/build/outputs/apk/debug/`

## 🎨 支持的音色

### 预置音色 (mimo-v2.5-tts)

| 音色 | Voice ID | 语言 | 性别 |
|------|----------|------|------|
| MiMo-默认 | mimo_default | 中英 | 混合 |
| 冰糖 | 冰糖 | 中文 | 女性 |
| 茉莉 | 茉莉 | 中文 | 女性 |
| 苏打 | 苏打 | 中文 | 男性 |
| 白桦 | 白桦 | 中文 | 男性 |
| Mia | Mia | 英文 | 女性 |
| Chloe | Chloe | 英文 | 女性 |
| Milo | Milo | 英文 | 男性 |
| Dean | Dean | 英文 | 男性 |

### 风格标签

支持在文本前添加风格标签控制语音风格：

- **情绪**: 开心/悲伤/愤怒/恐惧/惊讶/兴奋/委屈/平静/冷漠
- **语调**: 温柔/高冷/活泼/严肃/慵懒/俏皮/深沉/干练/凌厉
- **音色**: 磁性/醇厚/清亮/空灵/稚嫩/苍老/甜美/沙哑
- **方言**: 东北话/四川话/河南话/粤语
- **特殊**: 唱歌/夹子音/御姐音/正太音/大叔音

### 模型选择

| 模型 | 功能 | 音色来源 |
|------|------|----------|
| mimo-v2.5-tts | 预置音色合成 | 内置精品音色 |
| mimo-v2.5-tts-voicedesign | 音色设计 | 文本描述生成音色 |
| mimo-v2.5-tts-voiceclone | 音色复刻 | 音频样本复刻 |

## 📁 项目结构

```
MiMoTTSReader/
├── app/src/main/
│   ├── java/com/mimo/ttsreader/
│   │   ├── App.kt                    # Application 类
│   │   ├── MainActivity.kt           # WebView 主界面
│   │   ├── api/
│   │   │   └── MiMoTtsClient.kt      # MiMo TTS API 客户端
│   │   ├── model/
│   │   │   ├── MiMoTtsModels.kt      # API 数据模型 + 音色注册表
│   │   │   └── VoiceConfig.kt        # 语音配置
│   │   ├── receiver/
│   │   │   └── BootReceiver.kt       # 开机自启
│   │   ├── server/
│   │   │   └── TtsServer.kt          # NanoHTTPD 本地服务器
│   │   ├── service/
│   │   │   └── TtsService.kt         # 前台服务
│   │   └── util/
│   │       ├── AudioUtils.kt         # 音频处理工具
│   │       └── ConfigManager.kt      # 配置管理
│   ├── assets/web/
│   │   ├── index.html                # WebView 主页面
│   │   ├── css/style.css             # 深色主题样式
│   │   └── js/app.js                 # 前端交互逻辑
│   └── res/                          # Android 资源
├── .github/workflows/build.yml       # GitHub Actions 构建
└── build.gradle.kts                  # Gradle 配置
```

## 📄 许可证

MIT License

## 🙏 致谢

- [MiMo TTS](https://xiaomimimo.com/) - 提供语音合成 API
- [Legado](https://github.com/gedoor/legado) - 开源阅读APP
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) - 轻量级嵌入式 HTTP 服务器
