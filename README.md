# 圆形半径选点 · Baidu Map Radius Selector

[![Build APK](https://github.com/atouboot/baidu-map-radius-selector/actions/workflows/build.yml/badge.svg)](https://github.com/atouboot/baidu-map-radius-selector/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-24-green.svg)](https://developer.android.com/about/versions/nougat)

一个开源 Android 应用：在百度地图上**选点**并绘制**指定半径的圆形区域**。

点击地图任意位置或读取手机当前 GPS 位置作为圆心，输入半径（单位 km，精确到 0.01 km），
地图上实时绘制半透明圆形覆盖区域，并显示圆心经纬度、定位精度与覆盖面积。

> 纯自用工具向，界面为简洁中文。

---

## 功能

| 功能 | 状态 |
|---|---|
| 点击地图选点作为圆心 | ✅ |
| 读取手机当前 GPS 位置（百度定位 SDK） | ✅ |
| 一键「以定位点为圆心」 | ✅ |
| 半径输入，单位 km，精确到 0.01 | ✅ |
| 实时绘制半透明圆形区域（可随输入即时变化） | ✅ |
| 显示圆心经纬度（6 位小数） | ✅ |
| 显示定位精度（±x.x m） | ✅ |
| 显示覆盖面积（km²） | ✅ |
| 运行时配置百度地图 AK（不写进仓库） | ✅ |
| 保存 / 管理 / 清除多个圆形区域 | ✅ |
| 多个圆形**同时常驻显示**在地图上，可直观看到重叠与相交 | ✅ |

---

## 开源项目调研结论

按需求要求，开发前先用 GitHub 检索了可复用的成熟项目，结论如下：

| 项目 | Star | 结论 |
|---|---|---|
| [lovebing/react-native-baidu-map](https://github.com/lovebing/react-native-baidu-map) | 866 | React Native 桥接层，非原生 Android App，不适用 |
| [chenyufeng1991/BaiduMap-TrafficAssistant](https://github.com/chenyufeng1991/BaiduMap-TrafficAssistant) | 191 | 交通助手 App，功能目标不同，SDK 版本老旧 |
| [zhuhaoHappig/BaiduMapApi](https://github.com/zhuhaoHappig/BaiduMapApi) | 173 | 教学 Demo（轨迹/图层），非产品级工程 |
| [zhuhaoHappig/BaiduMapFence](https://github.com/zhuhaoHappig/BaiduMapFence) | 33 | 基于**鹰眼 SDK** 的轨迹围栏，与「地图选点 + 半径圆」不是一回事 |
| [Soursoupxi/Android_Lite_Map_Based_on_BaiduMapSDK_v7.6.3](https://github.com/Soursoupxi/Android_Lite_Map_Based_on_BaiduMapSDK_v7.6.3) | 10 | 精简地图 App（检索/路线规划），不含半径圆形能力 |
| [lixxvsky/BaiduMapUI](https://github.com/lixxvsky/BaiduMapUI) | 16 | 只是百度地图 UI 美化，2023 年后无更新 |

**结论：没有找到「地图选点 + 圆形半径定位 + 多圆管理」这一功能组合的成熟且持续维护的开源项目。**
因此本项目基于**百度地图官方 SDK** 从零开发，只复用其官方 Maven 依赖，避免引入停止维护的第三方封装。

技术选型以「维护活跃 + 兼容现代 Android」为准：

- 百度地图 SDK `com.baidu.lbsyun:BaiduMapSDK_Map`（Apache-2.0，官方 Maven Central 坐标，持续更新）
- 百度定位 SDK `com.baidu.lbsyun:BaiduMapSDK_Location`（同上）
- Kotlin + AndroidX + Material 3，`minSdk 24 / targetSdk 34`，JDK 17

---

## 下载与安装

### 方式一：直接下载 CI 构建好的 APK（推荐，无需本地环境）

每次向 `main` 分支推送代码，GitHub Actions 都会自动编译并发布：

**下载地址（固定）：**

```
https://github.com/atouboot/baidu-map-radius-selector/releases/download/ci-latest/baidu-map-radius-selector-debug.apk
```

也可以在仓库 **Releases** 页面找到最新构建，或在该次 Actions 运行页面的
`Artifacts` 中下载 `baidu-map-radius-selector-debug`（后者需要登录 GitHub）。

安装：把 APK 传到手机后点击安装，按系统提示允许「安装未知来源应用」。
最低支持 Android 7.0（API 24）。

### 方式二：本地编译

```bash
git clone https://github.com/atouboot/baidu-map-radius-selector.git
cd baidu-map-radius-selector

# 生成 Gradle Wrapper（仓库未提交二进制 wrapper，首次需要执行一次）
gradle wrapper --gradle-version 8.7

# 可选：配置 AK（见下一节）
cp local.properties.example local.properties
# 编辑 local.properties，填写 BAIDU_MAP_AK=你的AK

./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开该目录构建。

---

## 配置百度地图 AK

百度地图 SDK **必须**配置 AK 才能显示地图和定位。本项目提供两种方式，**都不会把 AK 写进仓库**。

### 第一步：申请 AK

1. 打开 [百度地图开放平台控制台](https://lbsyun.baidu.com/apiconsole/key)，登录后创建应用。
2. 应用类型选择 **Android SDK**。
3. 填写 **包名（PackageName）**：

   ```
   com.atouboot.radiusmap
   ```

4. 填写 **SHA1**（获取方法见下方「获取 SHA1」）。
5. 提交后会得到一个形如 `abcdEFGH1234567890...` 的 AK。

> ⚠️ 包名和 SHA1 必须与 APK 完全一致，否则地图会显示空白或报鉴权失败。

### 获取 SHA1

**A. 针对 CI 构建的 APK（推荐）**

CI 会**复用同一份调试签名证书**（通过 Actions cache 持久化），所以 SHA1 跨构建稳定。
获取方式任选其一：

1. 打开仓库 **Releases** 页面 → `ci-latest` → 发布说明里直接写着 SHA1；
2. 打开任意一次成功构建的 Actions 运行页 → 页面底部的 **Summary** 里也有 SHA1。

拿到 SHA1 后去申请 AK 即可。

**B. 针对本地构建的 APK**

```bash
# macOS / Linux
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

Windows 默认路径为 `C:\Users\<你>\.android\debug.keystore`。

> 同一台机器 / 同一份 debug.keystore 的 SHA1 固定不变。
> 如果你换了电脑或删除了 debug.keystore，SHA1 会变，需要重新申请 AK。

### 第二步：把 AK 填进 App

**方式 1 —— 运行时填写（最简单，推荐给普通用户）**

安装后打开 App，若尚未配置 AK，会自动弹出配置窗口；
也可以随时点击右上角 **「AK 设置」** 填写。

AK 只保存在本机 `SharedPreferences`，不会上传、不会写入仓库。
填写后**重启 App** 生效。

**方式 2 —— 编译期注入（适合自建构建）**

本地构建：在 `local.properties` 中写入

```properties
BAIDU_MAP_AK=你的AK
```

CI 构建：在仓库 `Settings → Secrets and variables → Actions` 中新增 Secret
`BAIDU_MAP_AK`，工作流会自动注入。**Secrets 不会出现在日志或仓库中。**

> 优先级：App 内填写的 AK > 编译期注入的 AK。
> 仓库源码、`AndroidManifest.xml`、`build.gradle.kts` 中**始终不包含任何真实 AK**。

---

## 使用方法

1. 打开 App，等待地图加载（需联网）。
2. **选圆心**，二选一：
   - 直接**点击地图**任意位置；
   - 点击 **「定位到我的位置」** 移动镜头到当前 GPS 位置，再点 **「以定位点为圆心」** 把圆心设为当前位置。
3. 在底部 **「半径（km）」** 输入框里输入半径，例如 `0.50`、`1.00`、`12.75`。
   地图上的半透明圆形会**实时**跟着变化。
4. 顶部信息卡实时显示：
   - `中心点：39.908823, 116.397470`
   - `定位精度：±8.0 m`
   - `半径：1.00 km ｜ 覆盖面积：3.14 km²`
5. 点 **「保存当前圆形」** 把当前「圆心 + 半径」存入底部「已保存区域」。
6. **管理已保存区域**：保存后的圆形会**常驻显示在地图上**（不是只显示当前那一个），
   所以多个圆的重叠 / 相交关系可以一眼看出来。
   - 每个区域分配一个颜色：地图上的**圆形填充与描边、圆心点、名称标签**，
     以及列表卡片上的**色点和描边**，用的是同一个颜色，便于一一对应；
   - 点击列表卡片 → 视角跳到该区域，并把它的半径回填到输入框；
   - 点击卡片右上角 **✕** → 删除该区域（地图上的圆形同时消失）；
   - 点击 **「全部清除」** → 二次确认后清空全部。
7. 点 **「清除圆形」** 只移除地图上**正在编辑的那个草稿圆**（虚线描边那个），
   **不影响**已保存的区域。

> 已保存区域存放在本机 `SharedPreferences`（JSON），卸载应用会一并清除。

> 首次使用定位功能时，系统会请求位置权限，请选择「允许」。

---

## 云端编译（GitHub Actions）

工作流文件：[`.github/workflows/build.yml`](.github/workflows/build.yml)

触发条件：

- 推送到 `main` / `master`
- 对 `main` / `master` 发起 Pull Request
- 手动 `workflow_dispatch`

工作流做了什么：

1. JDK 17 + Android SDK（`platforms;android-34`、`build-tools;34.0.0`）
2. 准备签名证书：优先使用 Secret `SIGNING_KEYSTORE_BASE64`，否则生成一份并缓存
   （保证 SHA1 跨构建稳定，避免 AK 失效）
3. Gradle 8.7 执行 `assembleDebug`
4. 把签名证书 SHA1 写入 Actions Summary 与 Release 说明（申请 AK 用）
5. 上传 APK 为 Actions artifact（保留 30 天）
6. 发布到固定 Release：tag `ci-latest`，可直接匿名下载

可选 Secrets（都不是必需的）：

| Secret | 作用 |
|---|---|
| `BAIDU_MAP_AK` | 把 AK 编译进 APK；不配置则在 App 内运行时填写 |
| `SIGNING_KEYSTORE_BASE64` | 使用你自己的签名证书（base64 编码的 keystore），SHA1 完全可控 |

> 若 GitHub Actions 缓存被清理（超过 7 天无构建），证书会重新生成、SHA1 随之变化，
> 此时需要重新申请一次 AK，或改用 `SIGNING_KEYSTORE_BASE64` 彻底固定。

---

## 项目结构

```
.
├── .github/workflows/build.yml      # 云端 CI：编译 + 发布 APK
├── app/
│   ├── build.gradle.kts             # 模块配置（AK 从 local.properties / 环境变量读取）
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml      # 权限、AK 占位符、定位服务
│       ├── java/com/atouboot/radiusmap/
│       │   ├── RadiusMapApp.kt          # Application
│       │   ├── MainActivity.kt          # 主界面：选点 / 定位 / 画圆 / 管理
│       │   ├── data/AppPrefs.kt         # 本地存储（AK + 区域 JSON）
│       │   ├── data/CircleZone.kt       # 区域数据模型 + JSON 序列化
│       │   ├── data/CircleStore.kt      # 区域增删查仓库
│       │   ├── ui/CircleZoneAdapter.kt  # 已保存区域列表适配器
│       │   └── util/GeoUtils.kt         # 距离 / 面积 / 格式化
│       └── res/                         # 中文界面资源
├── local.properties.example         # AK 配置模板
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 技术栈

| 项 | 版本 |
|---|---|
| 语言 | Kotlin 1.9.24 |
| 构建 | Gradle 8.7 + Android Gradle Plugin 8.5.2 |
| JDK | 17 |
| minSdk / targetSdk / compileSdk | 24 / 34 / 34 |
| 百度地图 SDK | `com.baidu.lbsyun:BaiduMapSDK_Map:7.6.4.1` |
| 百度定位 SDK | `com.baidu.lbsyun:BaiduMapSDK_Location:9.6.4` |
| UI | AndroidX + Material Components 1.12（Material 3） |

---

## 已知限制与 Roadmap

- [x] **阶段 1（MVP）**：地图选点 / GPS 定位 / 半径输入 / 实时画圆 / 信息展示 / AK 配置
- [x] **阶段 2**：保存、管理、清除**多个**圆形区域（本地持久化 + 横向列表管理）
- [ ] 圆心反向地理编码显示地址（需引入 `BaiduMapSDK_Search`）
- [ ] 一键「缩放到合适级别」按钮
- [ ] 圆形导出为 GeoJSON / 分享
- [ ] release 签名构建（通过 GitHub Secrets 提供 keystore）

其他说明：

- 未配置 AK 时地图为空白，属正常现象，不是崩溃。
- 定位依赖系统定位开关、网络与位置权限；室内精度可能较差。
- 圆形半径按**球面近似**绘制（百度 SDK 内部实现），超大半径（数千公里）下会有视觉畸变。

---

## 常见问题排错

### 一打开就闪退

已在 v0.1.0 修复。历史上出现过两类原因，供二次开发参考：

1. `BDMapSDKException: context must be an ApplicationContext`
   → `SDKInitializer.setAgreePrivacy()` / `SDKInitializer.initialize()` **必须传 ApplicationContext**，
   传 Activity 会直接抛异常。
2. `The location function has been stopped because you do not agree with the privacy compliance policy`
   → 定位 SDK 的隐私开关和地图 SDK 是**两个独立开关**，必须额外调用
   `LocationClient.setAgreePrivacy(true)`，否则 `new LocationClient(...)` 会抛异常。

### 地图空白 / 定位一直失败

先看日志里的 `鉴权错误信息`，百度 SDK 会打印出它认为的 SHA1 与包名：

```bash
adb logcat | grep -A6 "鉴权错误信息"
```

| errorcode | 含义 | 处理 |
|---|---|---|
| `101` | 在 AndroidManifest 中取不到 API_KEY | 检查 manifest 里的 meta-data；注意地图与定位 SDK 读的是**两个不同的 key 名**：`com.baidu.lbsapi.API_KEY`（地图）与 `com.baidu.android.lbs.API_KEY`（定位），两个都要声明 |
| `200` | APP 不存在 / AK 有误 | AK 没注册成功，或 **SHA1 / 包名与申请时不一致** |
| `230` | 签名校验失败 | 换过签名证书（例如重新构建导致 debug 证书变化），需重新申请 AK |

日志里的 `sha1;package:xx:xx:...;com.atouboot.radiusmap` 就是百度 SDK 实际拿到的
签名与包名，**照抄这两个值去申请 AK** 即可，不要手敲。

其他检查项：

- 是否在 App 内「AK 设置」填过 AK 并**重启**（运行时填写的 AK 需重启生效）
- 系统定位开关是否打开；是否已授予位置权限
- 设备是否联网（百度定位需要网络辅助）

---

## 安全与隐私

- 仓库中**不包含**百度地图 AK、签名密钥或任何秘密。
- AK 通过 `local.properties`（已 gitignore）、GitHub Secrets 或 App 运行时输入提供。
- AK 在本机仅存于 `SharedPreferences`，不上传服务器。
- App 申请的权限：网络、网络状态、WiFi 状态、粗略/精确定位、旧系统外部存储（`maxSdkVersion=28`）。

---

## 许可证

本项目源码采用 **Apache License 2.0**，见 [LICENSE](LICENSE)。

使用的百度地图 / 定位 SDK 由百度提供，同样以 Apache-2.0 发布，
使用前请遵守[百度地图开放平台服务条款](https://lbsyun.baidu.com/index.php?title=open/law)。
