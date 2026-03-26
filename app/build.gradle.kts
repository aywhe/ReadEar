plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.readear"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.readear"
        minSdk = 26  // ⭐ 从 24 提升到 26
        targetSdk = 36
        versionCode = 4  // 每次发布递增
        versionName = "1.3.2"  // 语义化版本号

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // ⭐ 保留这个配置（解决方法数超限）
        multiDexEnabled = true
        
        // ⭐ 添加版本信息到清单文件，供代码读取
        manifestPlaceholders["versionName"] = versionName.toString()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // 添加打包选项，排除重复的元数据文件
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"

            // 新增：排除 POI 的重复依赖
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/LICENSE.txt"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Room Database
    implementation("androidx.room:room-runtime:2.7.0-rc01")
    implementation("androidx.room:room-ktx:2.7.0-rc01")
    //implementation(libs.androidx.media3.common.ktx)
    ksp("androidx.room:room-compiler:2.7.0-rc01")

    // PDFBox for PDF files - 使用 Maven Central 的版本
    //implementation("org.apache.pdfbox:pdfbox:2.0.32")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    //implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")
    implementation("sh.calvin.reorderable:reorderable:3.0.0")

    // Apache POI for Word documents
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("commons-io:commons-io:2.15.0")
    implementation("org.apache.commons:commons-compress:1.25.0")
    
    // 添加文件编码检测库
    implementation("com.github.albfernandez:juniversalchardet:2.5.0")

    // 添加 MultiDex 支持
    //implementation("androidx.multidex:multidex:2.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}