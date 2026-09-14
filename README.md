# el · 英语学习

面向 Android 平板的英语学习应用：把字幕 / 文本文件当课本读，选中即查、即译，并配一套可点读的 IPA 音标学习卡。

界面分左右两栏，中间竖条可拖动调节宽度：

- **左栏**：字幕阅读器 —— 单击选词 / 长按选句，底部 `A-` `A+` 调节字号
- **右栏上方**：查询输入 + 翻译信息（原文 / 释义 / 英美音标）
- **右栏下方**：网页浏览器（默认打开 etymonline 词源）

## 功能

### 阅读器

- 支持 `.srt` 与 `.txt`（TXT 按空行分段，段内保留原始换行）
- SRT 完整显示条目信息：序号、起止时间码、原文、译文
- 自动拆分双语字幕：同一时间轴上的中 / 日 / 韩文字行判为译文，其余为原文，分行显示
- 自动清理字幕里的非正文内容：ASS 样式覆盖标签 `{\fs16\an2\b0}`、HTML 标签、`\N` 转义换行、BOM 与零宽字符
- 底部 `A-` / `A+` 调节阅读字号（12~44sp），不跟随系统字体缩放
- 单击选词、长按选句；长按后拖动可延长选区（可跨行）

### 选中后的两个动作

选中文本会先弹出「查询 / 翻译」菜单：

- **翻译** → 就地弹出浮动翻译窗，不打断阅读
- **查询** → 右上「查询」面板显示释义与英美音标，同时右下浏览器自动打开该词的 etymonline 词源页

### 右侧面板

- 中间竖条可拖动，左右宽度自由分配（左栏默认占 60%，两栏各不小于 220dp）
- 右上：查询输入框 + 翻译信息面板（可直接输入单词查询）
- 右下：带地址栏、返回、刷新与缩放按钮的网页浏览器；地址栏里既可填完整网址，也可直接填单词（自动转成词源查询）

### IPA 音标学习卡

- 把整串音标（如 `ˌɒpəˈtjuːnəti`）**按最长匹配切成单个音素**，逐个渲染成标签
- 音素词库共 53 条：25 个元音、24 个辅音、`ˈ ˌ ː` 3 个重音 / 长音记号、1 个可选音 `(r)`
- 三色区分：元音蓝 / 辅音绿 / 重音橙；英式、美式音标可点击切换
- 点击音素，下方显示中文发音要领与例词；点例词旁的 ▶ 可单独朗读例词
- 音素发声使用 **36 段真实录音**（`assets/ipa/`）；没有独立录音的双元音等回退为朗读例词
- 工具栏菜单「点击音标发声」为全局开关，关闭后点音素不再发声（持久化，重启仍生效）

## 数据来源

翻译与音标全部使用**免 API Key** 的公开接口，无需任何配置即可运行：

| 用途 | 接口 | 说明 |
| --- | --- | --- |
| 单词释义 | 百度 `fanyi.baidu.com/sug` | 免 Key；**不返回音标**，且对整句返回空数据 |
| 音标 | 有道 `dict.youdao.com/jsonapi` | 仅单个单词查询，返回英、美两套 IPA（约 0.3s） |
| 短语 / 整句 | MyMemory `api.mymemory.translated.net` | 免 Key，匿名调用约 5000 词/天 |

音标结果带内存缓存，同一单词不重复请求；单词查询时释义与音标并行请求，少等一轮网络。

## 环境与构建

- JDK 17
- Android SDK：`compileSdk 35` / `minSdk 24` / `targetSdk 35`
- Kotlin + ViewBinding（View 体系，未使用 Compose）
- 依赖仅 AndroidX / Material，翻译与 JSON 解析用 `HttpURLConnection` + `org.json`，未引入网络库

在 `local.properties` 里配好 `sdk.dir` 后：

```bash
# 编译 Debug 包
./gradlew assembleDebug

# 安装到已连接的设备
./gradlew installDebug
```

## 目录结构

```
app/src/main/
├── assets/
│   ├── ipa/                36 个音素录音（.ogg）
│   └── samples/            内置示例文件，首次启动解压到 Documents
└── java/com/englishstudy/app/
    ├── MainActivity.kt         首页（标题 + 打开阅读器）
    ├── EnglishStudyApp.kt      全局初始化：固定浅色、设置、TTS、示例文件解压
    ├── api/
    │   ├── Translator.kt       翻译 + 音标（百度 / 有道 / MyMemory）
    │   └── AppTtsManager.kt    TTS 朗读
    ├── reader/
    │   ├── ReaderActivity.kt           阅读器装配：分栏、工具栏、文件加载
    │   ├── InteractiveReaderView.kt    自绘选词 / 选句的阅读视图
    │   ├── SubtitleParser.kt           SRT / TXT 解析、双语拆分、样式清理
    │   ├── SubtitleEntry.kt            字幕条目模型
    │   ├── SelectionMenuPopup.kt       选中后的「查询 / 翻译」菜单
    │   ├── TranslationPopup.kt         浮动翻译窗
    │   ├── LookupPanel.kt              右上查询 / 翻译信息面板
    │   ├── EtymologyBrowser.kt         右下网页浏览器
    │   ├── IpaCard.kt                  音标学习卡视图
    │   ├── IpaKnowledge.kt             音素词库 + 音标串切分
    │   ├── IpaAudio.kt                 音素录音播放
    │   └── PopupPosition.kt            浮窗定位计算
    ├── ui/FlowLayout.kt            音素标签自动换行布局
    └── util/
        ├── AppSettings.kt          全局设置（SharedPreferences）
        └── SampleFileExtractor.kt  示例文件解压
```

## 音素音频的来源与授权

`app/src/main/assets/ipa/` 下的 36 个 `.ogg` 是**真实录音**，不是 TTS 合成的——TTS 读不出 `ɒ`、`ʃ`、`θ` 这类 IPA 符号，必须用录音。

录音取自 **Wikimedia Commons** 的 IPA 音素录音，多为 CC BY-SA / CC0 授权，可免费使用。逐个文件与 Commons 原始文件名的对照、以及授权说明见 [`app/src/main/assets/ipa/SOURCES.txt`](app/src/main/assets/ipa/SOURCES.txt)。**若要公开发布，请按各文件页面标注的作者与协议署名。**

## 实现要点

几个不直观但必要的地方，改动前值得一看：

- **系统栏**：`targetSdk 35` 起 Android 15 强制边到边显示，两个 Activity 都自行把状态栏 / 导航栏 / 刘海区域的 insets 作为根布局 padding，否则内容被系统栏压住。
- **固定浅色主题**：`EnglishStudyApp` 里 `MODE_NIGHT_NO`。界面配色是固定的米白底 + 深字，若跟随系统进入深色模式，Material 的弹出菜单 / 对话框会变深色底，而文字仍是自定义的白色，出现「白底白字」看不清。
- **音标来源**：百度 `/sug` 的释义文本里**没有音标**，所以音标改从有道 jsonapi 单独取；免费词典 `api.dictionaryapi.dev` 在国内网络连不通，未采用。
- **音素音频**：`build.gradle.kts` 里对 `ogg` 设置了 `noCompress`，MediaPlayer 才能用 `AssetFileDescriptor` 直接播放 assets 里的音频。
- **WebView 字号**：WebView 的文字大小 ≈ 系统 `fontScale × textZoom/100`，平板的系统字体缩放会把网页整体放大；默认用 `1/fontScale` 抵消，使网页与阅读器字号观感一致。
- **双语拆分**：靠「是否含 CJK 字符」判断译文行，同一时间轴两行时一行原文一行译文；若各行同属一种文字（单语字幕）则全部作为原文。

## 已知限制

- 界面按横屏平板设计（两个 Activity 均锁定 `landscape`），手机上会偏挤
- 翻译依赖公开免 Key 接口，有速率与额度限制，高频查询可能被限流
- 仅支持 SRT / TXT；ASS / SSA 只做了样式标签清理，不支持作为文件格式解析
- 音标只对**单个单词**查询，短语和整句不显示音标
- 部分双元音与美音卷舌元音（`ɝ` `ɚ` 等）没有独立录音，点击时朗读例词替代
- 示例文件在首次启动时解压到 `Android/data/com.englishstudy.app/files/Documents/`，卸载应用会一并删除
