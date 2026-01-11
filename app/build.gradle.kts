import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.txapp.musicplayer"
    compileSdk = 34

    val versionPropsFile = file("../version.properties")
    val versionProps = Properties()
    if (versionPropsFile.exists()) {
        versionProps.load(versionPropsFile.inputStream())
    }

    signingConfigs {
        create("release") {
            storeFile = file("txa.jks")
            storePassword = "23112006"
            keyAlias = "txa"
            keyPassword = "23112006"
        }
    }

    defaultConfig {
        applicationId = "com.txapp.musicplayer"
        minSdk = 28
        targetSdk = 34
        versionCode = versionProps.getProperty("versionCode", "100").toInt()
        versionName = versionProps.getProperty("versionName", "1.0.0_txa")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }


    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
    }
    
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("io.coil-kt:coil-compose:2.6.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1") // Switched to KSP
    kapt("org.xerial:sqlite-jdbc:3.34.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Palette
    implementation("androidx.palette:palette:1.0.0")

    // Work Manager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // UI Extras
    implementation("me.tankery.lib:circularSeekBar:1.4.2")
    implementation("jp.wasabeef:glide-transformations:4.3.0")
    implementation("com.h6ah4i.android.widget.advrecyclerview:advrecyclerview:1.0.0")

    // Konfetti
    implementation("nl.dionsegijn:konfetti-xml:2.0.4")
    implementation("nl.dionsegijn:konfetti-compose:2.0.4")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // UI Helpers
    implementation("dev.chrisbanes.insetter:insetter:0.6.1")
    implementation("com.github.bosphere.android-fadingedgelayout:fadingedgelayout:1.0.0")
    implementation("androidx.gridlayout:gridlayout:1.1.0")
    implementation("com.github.Adonai:jaudiotagger:2.3.15")

    // Koin
    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")

    // Floating Bubble View - Premium floating window library (Maven Central)
    implementation("io.github.torrydo:floating-bubble-view:0.6.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}
