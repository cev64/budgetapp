import com.android.build.api.variant.impl.VariantOutputImpl
import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Version: versionName from android/version.properties, versionCode = git commit count.
// The commit count only ever grows on the main branch, so every build made from a newer
// commit installs as an update. CI checks out with fetch-depth: 0 so the count is complete.
// ---------------------------------------------------------------------------
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("versionName", "1.0.0")

val gitCommitCount: Int = try {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir = rootProject.projectDir
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull() ?: 1
} catch (e: Exception) {
    1
}
val appVersionCode: Int = maxOf(1, gitCommitCount)

// ---------------------------------------------------------------------------
// Supabase client config: ../config/supabase.json, overridable by env vars.
// Only the publishable key ever goes here (row-level security protects the data).
// ---------------------------------------------------------------------------
val supabaseJson: Map<*, *> = rootProject.file("../config/supabase.json").let { f ->
    if (f.exists()) (JsonSlurper().parseText(f.readText()) as? Map<*, *>) ?: emptyMap<String, Any>() else emptyMap<String, Any>()
}
val supabaseUrl: String = providers.environmentVariable("SUPABASE_URL").orNull?.takeIf { it.isNotBlank() }
    ?: (supabaseJson["url"] as? String).orEmpty()
val supabaseKey: String = providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY").orNull?.takeIf { it.isNotBlank() }
    ?: (supabaseJson["publishableKey"] as? String).orEmpty()

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// ---------------------------------------------------------------------------
// Release signing: env vars (CI) or android/keystore.properties (local, git-ignored).
// Without them, release builds fall back to the debug key and the APK is renamed
// *-UNSIGNED-DEBUGKEY.apk so it is obviously NOT an upgrade-compatible build.
// ---------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(prop)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null } &&
    rootProject.file(releaseStoreFile!!).exists()

android {
    namespace = "com.personal.budget"
    compileSdk = 37

    defaultConfig {
        // PERMANENT. Changing this makes Android treat the build as a different app.
        applicationId = "com.personal.budget"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "SUPABASE_URL", supabaseUrl.trimEnd('/').asBuildConfigString())
        buildConfigField("String", "SUPABASE_KEY", supabaseKey.asBuildConfigString())
        buildConfigField("boolean", "RELEASE_SIGNED", hasReleaseSigning.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            // Same applicationId as release on purpose: see docs/ANDROID.md "Debug vs release".
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        // Shared fixtures (docs/fixtures/*.json) are read directly by the JVM unit tests.
        getByName("test").resources.directories.add("../../docs/fixtures")
        // Room schema JSONs, for MigrationTestHelper (Robolectric reads the debug variant's assets;
        // release APKs do not contain them).
        getByName("debug").assets.directories.add("$projectDir/schemas")
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("budget.fixtures.dir", rootProject.file("../docs/fixtures").absolutePath)
                // Screenshot rendering is opt-in: ./gradlew testDebugUnitTest -Pscreenshots=/abs/dir
                providers.gradleProperty("screenshots").orNull?.let { dir ->
                    it.systemProperty("budget.screenshots.dir", dir)
                }
                it.maxHeapSize = "3g"
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// Name APKs so the file says what it is: budget-<versionName>-<versionCode>.apk, and
// budget-<versionName>-<versionCode>-UNSIGNED-DEBUGKEY.apk when release signing is missing.
androidComponents {
    onVariants { variant ->
        val suffix = when {
            variant.buildType == "debug" -> "-debug"
            !hasReleaseSigning -> "-UNSIGNED-DEBUGKEY"
            else -> ""
        }
        variant.outputs.forEach { output ->
            (output as? VariantOutputImpl)?.outputFileName?.set("budget-$appVersionName-$appVersionCode$suffix.apk")
        }
    }
}

tasks.register("printVersion") {
    val name = appVersionName
    val code = appVersionCode
    val signed = hasReleaseSigning
    doLast { println("versionName=$name\nversionCode=$code\nreleaseSigned=$signed") }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.window)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
