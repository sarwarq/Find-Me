import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Firebase config comes from the Firebase console. Until the file is added the app
// still builds; sign-in just reports that it is not set up.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Release version, e.g. -Pfindme.version=1.2.3 (the release workflow takes it from the tag v1.2.3).
// versionCode = 1.2.3 → 10203, so every release is newer than the one before. Local builds: 1.0 / 1.
val releaseVersion: String? = providers.gradleProperty("findme.version").orNull
val appVersionCode: Int = releaseVersion?.let { v ->
    val parts = Regex("""(\d+)\.(\d{1,2})\.(\d{1,2})""").matchEntire(v)?.destructured?.toList()?.map(String::toInt)
        ?: error("findme.version must look like 1.2.3, got \"$v\"")
    parts[0] * 10_000 + parts[1] * 100 + parts[2]
} ?: 1

// GitHub "owner/repo" whose Releases the app updates from (update/UpdateGate.kt); empty: no self-updates.
val updateRepo: String = providers.gradleProperty("findme.updateRepo").getOrElse("")

// Yandex MapKit key: MAPKIT_API_KEY in local.properties (never commit it), or the environment
// variable of the same name (the release workflow sets it from a repository secret).
val mapkitApiKey: String = Properties()
    .apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
    .getProperty("MAPKIT_API_KEY")
    ?.takeIf { it.isNotBlank() }
    ?: providers.environmentVariable("MAPKIT_API_KEY").orNull?.takeIf { it.isNotBlank() }
    ?: error("MAPKIT_API_KEY is missing: add MAPKIT_API_KEY=<key> to local.properties")

android {
    namespace = "com.example.findme"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "bsb.findme.app"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = releaseVersion ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "MAPKIT_API_KEY", "\"$mapkitApiKey\"")
    }

    // Upload key that signs every published release, from keystore.properties (never commit it or the .jks file):
    //   storeFile=../findme-upload.jks  storePassword=...  keyAlias=upload  keyPassword=...
    val keystoreFile = rootProject.file("keystore.properties")
    signingConfigs {
        if (keystoreFile.exists()) {
            val p = Properties().apply { keystoreFile.inputStream().use { load(it) } }
            create("upload") {
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: removes unused code and resources and renames classes, so the APK is
            // smaller and harder to read. Our keep rules are in src/main/keepRules/*.keep.
            optimization {
                enable = true
            }
            // Phones only: MapKit's native library is ~40 MB per ABI, and x86 is for emulators
            // (debug builds keep it). Every update downloads the whole APK.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            // Without an upload key the release build is signed with the debug key, only to try it
            // on a phone (Google sign-in accepts the debug SHA-1). Every published release must use
            // the upload key: Android installs an update only if it is signed with the same key.
            signingConfig = signingConfigs.findByName("upload")
                ?: if (releaseVersion != null) {
                    error("findme.version is set but keystore.properties is missing: a release must be signed with the upload key")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Compressed native libraries: the APK (and so each update download) is about half the size;
    // Android unpacks them once on install.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.android.yandex.map)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.config)
    // Play services libraries ask for an old Fragment; the Activity Result API needs 1.3+.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity.googleid)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    // Unit tests run on the JVM, where Android's org.json is only stubs.
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}