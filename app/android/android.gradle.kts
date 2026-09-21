import com.android.build.api.dsl.ApkSigningConfig
import java.util.Locale

plugins {
    id("com.android.application")
    id("kotlin-android")
//    id("kotlin-kapt")
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.serialization)
}

val isInstantApp: Boolean = (project.findProperty("isInstantApp") as String?)?.let { it == "true" } ?: false

val getVersionInstant: () -> Int = {
    // The instantApp version must be smaller than the installable app version
    if (isInstantApp) {
        0
    } else {
        1
    }
}
val versionInstant = getVersionInstant()
println("isInstantApp: $isInstantApp")

val getVersionCode: (Int, Int, Int, Int, Int) -> Int = { major, minor, patch, build, instant ->
    major * 10000000 + minor * 100000 + patch * 1000 + build * 10 + instant
}

val getVersionName: (Int, Int, Int, String?, Boolean, Int?) -> String = { major, minor, patch, type, isInstant, versionCode ->
    var versionName = "$major.$minor.$patch"
    if (type != null) {
        versionName = "$versionName-$type"
    }
    if (isInstant) {
        versionName = "$versionName-instant"
    }
    if (versionCode != null) {
        versionName = "$versionName-($versionCode)"
    }
    versionName
}

android {
    namespace = "wallapp.app.android"

    setFlavorDimensions(listOf("client"))

    val versionMajor = 1
    val versionMinor = 3
    val versionPatch = 4
    val versionBuild = 1 // Start at "1" to match iOS
    val type: String? = null//"Alpha"
    val versionCode = getVersionCode(versionMajor, versionMinor, versionPatch, versionBuild, versionInstant)
    val versionName = getVersionName(versionMajor, versionMinor, versionPatch, type, isInstantApp, null)

    productFlavors {
        create("wallApp") {
            this.versionCode = versionCode
            this.versionName = versionName

            applicationId = "com.bkg.superwall"
            dimension = "client"
        }
    }

    signingConfigs {
        getByName("debug") {
            configureSigningConfigDebug()
//            configureSigningConfigReleaseWithDebugFallback()
        }
        create("release") {
            configureSigningConfigReleaseWithDebugFallback()
            // Optional, specify signing versions used
//            enableV1Signing = true
//            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            // Enable to add a suffix to the package name for debug builds
//            applicationIdSuffix = ".debug"
            versionNameSuffix = "-d"
        }

        release {
            signingConfig = signingConfigs.getByName("release")

            isShrinkResources = true
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.gms.play.services.auth)
    implementation(libs.koin.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.material)
    implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.okhttp3.okhttp)
    implementation(libs.timber)

    implementation(libs.androidx.work.runtime)
//    implementation(libs.dagger.android)
//    implementation(libs.dagger.android.support)
//    kapt(libs.dagger.compiler)
//    kapt(libs.dagger.android.processor)
    implementation(libs.profile.installer)

    coreLibraryDesugaring(libs.android.tools.desugar.jdk.libs)

    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    implementation(libs.androidx.lifecycle.process)

    implementation(project(":shared"))
    implementation(project(":shared:app:app-adapter"))
    implementation(project(":shared:app:app-initializer"))
    implementation(project(":shared:core:common"))
    implementation(project(":shared:core:di"))
    implementation(project(":shared:data:account"))
    implementation(project(":shared:data:ads-admob"))
    implementation(project(":shared:data:ads-api"))
    implementation(project(":shared:data:ads-internal"))
    implementation(project(":shared:data:billing:api"))
    implementation(project(":shared:data:cloudmessaging-api"))
    implementation(project(":shared:data:cloudmessaging-firebase"))
    implementation(project(":shared:data:content"))
    implementation(project(":shared:data:content-network"))
    implementation(project(":shared:data:licensing-api"))
    implementation(project(":shared:data:licensing-debug"))
    implementation(project(":shared:data:mediamap"))
    implementation(project(":shared:data:mediamap-network"))
    implementation(project(":shared:data:remoteconfig-firebase"))
    implementation(project(":shared:data:resources"))
    implementation(project(":shared:data:server-firebase"))
    implementation(project(":shared:data:toolkit"))
    implementation(project(":shared:data:wallpaper"))
    implementation(project(":shared:domain:ads-support"))
    implementation(project(":shared:domain:billing-support"))
    implementation(project(":shared:domain:content-state"))
    implementation(project(":shared:domain:license-state"))
    implementation(project(":shared:domain:licensing-billing"))
    implementation(project(":shared:interop:bridge"))
    implementation(project(":shared:interop:initialization"))
    implementation(project(":shared:presentation:pixel-ui"))
    implementation(project(":shared:presentation:wallapp-ui"))
    implementation(project(":shared:system:common"))

    "wallAppImplementation"(project(":shared:di:di-app"))

    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.net.lachlanmckee.timber.junit.rule)
    testImplementation(libs.robolectric)
    testImplementation(project(":shared:test:test-common"))
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.test.espresso.contrib)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.jraska.livedata.testing)
    androidTestImplementation(libs.kotlin.test)
    "baselineProfile"(project(":baselineprofile"))

    debugImplementation(libs.leakcanary.android)
}

fun ApkSigningConfig.configureSigningConfigDebug() {
    val apkSigningConfig: ApkSigningConfig = this
    val androidGradle = this@Android_gradle

    apkSigningConfig.storeFile = androidGradle.file("debug.keystore")
    apkSigningConfig.storePassword = "android"
    apkSigningConfig.keyAlias = "androiddebugkey"
    apkSigningConfig.keyPassword = "android"
}

/**
 * Debug fallback is used so an engineer who does not have access to the release keystore can
 * still build the app in Release. The resulting APK will not be signed with the correct key, and
 * thus cannot be uploaded to the Play Store, but it can be used for testing.
 */
fun ApkSigningConfig.configureSigningConfigReleaseWithDebugFallback() {
    val apkSigningConfig: ApkSigningConfig = this
    val androidGradle = this@Android_gradle

    if (System.getenv("WALL_APP_KEYSTORE_LOCATION") != null) {
        apkSigningConfig.storeFile = androidGradle.file(System.getenv("WALL_APP_KEYSTORE_LOCATION")!!)
        apkSigningConfig.storePassword = System.getenv("WALL_APP_KEYSTORE_PASSWORD")!!
        apkSigningConfig.keyAlias = System.getenv("WALL_APP_KEY_ALIAS")!!
        apkSigningConfig.keyPassword = System.getenv("WALL_APP_KEY_PASSWORD")!!
    } else {
        configureSigningConfigDebug()
    }
}

fun String.capitalizeFirstLetter(): String {
    return replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(
            Locale.getDefault()
        ) else {
            it.toString()
        }
    }
}

project.afterEvaluate {
    android.applicationVariants.forEach { variant ->
        if (variant.buildType.name == "debug") {
            tasks.register<Exec>("run${variant.name.capitalizeFirstLetter()}") {
                dependsOn("install${variant.name.capitalizeFirstLetter()}")
                group = "run"
                commandLine("adb", "shell", "am", "start", "-n", "${variant.applicationId}/wallapp.activity.MainActivity")
                doFirst {
                    println("Executing command: adb shell am start -n ${variant.applicationId}/wallapp.activity.MainActivity")
                }
                doLast {
                    println("Launching ${variant.applicationId}/wallapp.activity.MainActivity")
                }
            }
        }
    }
}

apply(plugin = "com.google.gms.google-services")
apply(plugin = "com.google.firebase.crashlytics")


configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:atomicfu:${libs.versions.kotlinxAtomicFu.get()}")
        force("androidx.compose.ui:ui-test-junit4-android:${libs.versions.composeMultiplatformUi.get()}")
        force("androidx.compose.ui:ui-test-android:${libs.versions.composeMultiplatformUi.get()}")
    }
}
