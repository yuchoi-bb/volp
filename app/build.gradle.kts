import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// CI가 넘겨주는 버전. 로컬 빌드에서는 기본값을 쓴다.
val buildVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
val buildVersionName = System.getenv("VERSION_NAME") ?: "1.0.$buildVersionCode"

// 서명 정보는 CI에서 환경 변수로, 로컬에서는 keystore.properties로 받는다. 둘 다 없으면 디버그 키로 빌드한다.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}
val releaseStoreFile: String? = System.getenv("KEYSTORE_FILE") ?: keystoreProperties.getProperty("storeFile")
val releaseStorePassword: String? = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword")
val releaseKeyAlias: String? = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
val releaseKeyPassword: String? = System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() && file(releaseStoreFile).exists()

val mapsApiKey: String =
    System.getenv("MAPS_API_KEY") ?: keystoreProperties.getProperty("mapsApiKey") ?: ""

// 영수증을 읽는 데 쓰는 키. 없으면 그 기능만 꺼진 채로 빌드된다.
val geminiApiKey: String =
    System.getenv("GEMINI_API_KEY") ?: keystoreProperties.getProperty("geminiApiKey") ?: ""

// 기기끼리 기록을 맞추는 Firestore 설정. 저장소에 두지 않으므로 없으면 없는 대로 빌드된다.
val googleServicesFile = file("google-services.json")
val hasFirebase = googleServicesFile.exists()
if (hasFirebase) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.volp.travelbudget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.volp.travelbudget"
        minSdk = 29
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 앱이 최신 릴리스를 확인할 저장소.
        buildConfigField("String", "UPDATE_REPO", "\"yuchoi-bb/volp\"")

        // 지도 키는 저장소에 두지 않는다. CI는 시크릿으로, 로컬은 keystore.properties로 넣는다.
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
        buildConfigField("boolean", "HAS_MAPS_KEY", mapsApiKey.isNotBlank().toString())
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebase.toString())
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("boolean", "HAS_GEMINI_KEY", geminiApiKey.isNotBlank().toString())
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
