import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 与主应用共用发布签名凭据（存在时），便于把测试包直接分发到真机。
val keystorePropsFile = rootProject.file("app/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.iccyuan.notifytest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iccyuan.notifytest"
        minSdk = 26
        targetSdk = 33
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                // storeFile 路径相对 app/ 目录（与主应用一致）。
                val path = keystoreProps.getProperty("storeFile")
                storeFile = File(path).takeIf { it.isAbsolute } ?: File(rootProject.file("app"), path)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 测试工具不做混淆，保持产物可读、构建快速。
            isMinifyEnabled = false
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

    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "NotifyTest-${variant.versionName}-${variant.buildType.name}.apk"
        }
    }
}
