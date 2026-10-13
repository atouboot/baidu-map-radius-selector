import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * 读取百度地图 AK。
 *
 * 优先级：local.properties -> 环境变量 BAIDU_MAP_AK -> 空字符串。
 *
 * 安全说明：AK 属于用户私有凭证，**绝不写入仓库**。
 * - 本地构建：写到 local.properties（已在 .gitignore 中）
 * - CI 构建：通过 GitHub Actions Secret `BAIDU_MAP_AK` 注入
 * - 留空也能用：App 内置设置界面可在运行时填写 AK
 */
val baiduAk: String = run {
    val props = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { props.load(it) }
    }
    val raw = (props.getProperty("BAIDU_MAP_AK") ?: System.getenv("BAIDU_MAP_AK") ?: "")
    // 百度 AK 为字母数字串；过滤掉空白/引号，避免注入 AndroidManifest 时破坏 XML
    raw.trim().filter { it.isLetterOrDigit() }
}

android {
    namespace = "com.atouboot.radiusmap"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.atouboot.radiusmap"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // 通过 manifestPlaceholders 注入，避免 AK 出现在源码里
        manifestPlaceholders["BAIDU_MAP_AK"] = baiduAk

        vectorDrawables.useSupportLibrary = true
    }

    // CI 在仓库内固定路径生成 / 复用调试证书，让签名 SHA1 保持稳定。
    // 原因：百度地图 AK 需要绑定「包名 + SHA1」，SHA1 每次都变会导致 AK 失效。
    // 本地构建时不设置该环境变量，仍使用 Android 默认的 debug 证书。
    val ciKeystoreFile: File? = System.getenv("DEBUG_KEYSTORE_PATH")
        ?.takeIf { it.isNotBlank() }
        ?.let { file(it) }
        ?.takeIf { it.exists() }

    signingConfigs {
        val ksf = ciKeystoreFile
        if (ksf != null) {
            create("ciDebug") {
                storeFile = ksf
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ""
            if (ciKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
        release {
            // MVP 阶段保持不混淆，降低构建风险；
            // 百度 SDK 自带 consumer proguard 规则，混淆开启后一般也无需额外配置。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
        jniLibs {
            // 百度地图 SDK 自带 .so，使用传统打包方式可提升兼容性
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 百度地图 SDK（Maven Central 官方坐标，Apache-2.0）
    implementation("com.baidu.lbsyun:BaiduMapSDK_Map:7.6.4.1")
    // 百度定位 SDK（用于读取手机当前 GPS 位置与定位精度）
    implementation("com.baidu.lbsyun:BaiduMapSDK_Location:9.6.4")
}
