import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

// Signing credentials come only from local.properties — no env var fallback.
fun signingProperty(localKey: String): String? =
    localProperties.getProperty(localKey)?.takeIf { it.isNotBlank() }

android {
    namespace = "dev.fitiavana.accounting"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "dev.fitiavana.accounting"
        minSdk = 19
        targetSdk = 36
        versionCode = 55
        versionName = "1.55"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingProperty("android.keystore.path")
            val storePwd = signingProperty("android.keystore.password")
            val keyAliasEnv = signingProperty("android.key.alias")
            val keyPwd = signingProperty("android.key.password")
            if (storeFilePath != null && storePwd != null && keyAliasEnv != null && keyPwd != null) {
                storeFile = file(storeFilePath)
                storePassword = storePwd
                keyAlias = keyAliasEnv
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    lint {
        // minSdk is 19: no SDK_INT checks that API 19 already satisfies, no deprecated resources (docs/guardrails.md).
        error += setOf("ObsoleteSdkInt", "Deprecated")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// The release notes screen reads the repo's CHANGELOG.md: ship it as an asset instead of keeping a second copy.
val changelogAssetsDir = layout.buildDirectory.dir("generated/changelog-assets")
val copyChangelogAsset by tasks.registering(Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    into(changelogAssetsDir)
}
android.sourceSets.getByName("main").assets.directories.add(changelogAssetsDir.get().asFile.path)
// Lint also scans the asset source dirs, so it needs the copy done first.
tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) || it.name.contains("lint", ignoreCase = true)
}.configureEach {
    dependsOn(copyChangelogAsset)
}

kotlin {
    compilerOptions {
        // A deprecated API must be replaced by its recommended alternative, so every warning fails the build.
        allWarningsAsErrors.set(true)
    }
}

// The Android Conscrypt AAR's native library can't load on the JVM, and Robolectric
// supplies its own Conscrypt; keep the AAR out of the unit-test runtime.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "org.conscrypt", module = "conscrypt-android")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.okhttp)
    implementation(libs.conscrypt)
    implementation(libs.androidx.multidex)
    implementation(libs.mpandroidchart)
    implementation(libs.bouncycastle)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
}