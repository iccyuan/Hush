import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 读取发布签名凭据 (keystore.properties 不纳入版本控制)
val keystorePropsFile = rootProject.file("app/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

// 高德地图 API Key：本地从 local.properties 读取（不入库）；CI 等环境通过
// -PAMAP_KEY=... 或环境变量 AMAP_KEY 注入（见 .github/workflows/release.yml）。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val amapKey: String = localProps.getProperty("AMAP_KEY")
    ?: (project.findProperty("AMAP_KEY") as String?)
    ?: System.getenv("AMAP_KEY")
    ?: ""

android {
    namespace = "com.iccyuan.hush"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iccyuan.hush"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        // 默认与当前迭代一致; CI 可通过 -PversionName=1.2.3 (由 git tag 推导) 覆盖
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.11"
        vectorDrawables { useSupportLibrary = true }

        // 高德 SDK 要求的 apikey 以 manifest meta-data 提供；用占位符注入，key 不写进版本控制。
        manifestPlaceholders["AMAP_KEY"] = amapKey

        // 导出 Room schema，使后续版本的数据库迁移可被校验。
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 启用 R8 代码压缩/混淆；保留资源不压缩以规避漏删风险（见 proguard-rules.pro 的 keep 规则）。
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 仅当存在凭据时启用发布签名, 否则回退到默认 (避免 CI 等环境构建失败)
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        compose = true
        buildConfig = true   // 暴露 BuildConfig.VERSION_NAME 供应用内显示版本/检查更新
    }

    // 产物命名为 Hush-<版本>-<构建类型>.apk，而非默认的 app-release.apk。
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Hush-${variant.versionName}-${variant.buildType.name}.apk"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.splashscreen)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    // 高德地图（选点）+ 定位/地理围栏（运行时监控，不依赖 Google Play 服务）。
    // 3dmap 已内置定位与围栏类，无需再单独引入 location（否则类重复）。
    implementation("com.amap.api:3dmap:10.0.600")
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
