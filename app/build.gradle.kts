plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ru.gft.decentralizedmessenger"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ru.gft.decentralizedmessenger"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.recyclerview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")
}
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiName = output.filters
                .find { it.filterType  == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                .let { it?.identifier ?: "universal"}

            val baseName = android.defaultConfig.getName()
            val buildType = variant.buildType ?: "release"
            val versionName = android.defaultConfig.versionName ?: "0.1"

            output.outputFileName.set("${baseName}-${buildType}-${abiName}-v${versionName}.apk")
        }
    }
}