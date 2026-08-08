import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
    // id("org.jetbrains.kotlin.kapt")
    id("com.google.devtools.ksp")
    alias(libs.plugins.compose.compiler)
}

// Release signing credentials live in keystore.properties (gitignored, never committed --
// this is a public repo). Loaded only if present, so a fresh clone without the keystore can
// still build debug variants; a release build without it simply won't be signed.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.peeyupatel.phototextsearch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.peeyupatel.phototextsearch"
        minSdk = 30
        targetSdk = 36

        // Versioning policy (standard Android/Play Store practice):
        // - versionCode: plain integer, increment by exactly 1 for every single build
        //   uploaded to Play Console (release OR internal/beta testing tracks), regardless of
        //   whether versionName changes. Play Console rejects any upload whose versionCode
        //   isn't strictly higher than the last one it has seen for this app. Play doesn't
        //   actually require starting from 1 -- it only enforces that each upload's code is
        //   higher than the previous one -- so this deliberately continues past the inherited
        //   Tulsi-fork numbering (103) rather than resetting to 1. A reset to 1 was tried first
        //   but reverted: Android's own install-time downgrade protection then refused to
        //   install any new debug build over the test phone's existing versionCode 103 install,
        //   and the only way past that is a full uninstall, which would wipe the OCR index
        //   already built up on that device. Continuing the existing numbering avoids that
        //   entirely and is just as valid for Play Console's first-ever upload of this app.
        // - versionName: semantic versioning (MAJOR.MINOR.PATCH), what users actually see.
        //   Bump PATCH for bug fixes, MINOR for new user-facing features, MAJOR for a big/
        //   breaking change in how the app works. Reset to 1.0.0 here (versionName has no
        //   monotonic-increase constraint, unlike versionCode, so this reset is safe) since it
        //   is PhotoLex's own first real release, not meaningfully related to the inherited
        //   v1.1.0 label.
        versionCode = 104
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Optimize ML Kit for size - exclude unused architectures
        ndk {
            // Only include ARM architectures (most common on mobile devices)
            // Exclude x86 and x86_64 to reduce size significantly (~10-15MB reduction)
            abiFilters += setOf("armeabi-v7a", "arm64-v8a")
        }

        ksp {
       		arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }

            // Additional size optimizations
            isDebuggable = false
            isJniDebuggable = false
            isRenderscriptDebuggable = false
            renderscriptOptimLevel = 3

            // Enable automatic image optimization
            isCrunchPngs = true
            isPseudoLocalesEnabled = false
        }

        debug {
            // Apply some optimizations to debug builds too for testing
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Exclude additional unnecessary files to reduce APK size
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "**/kotlin/**"
            excludes += "**/*.txt"
            excludes += "**/*.xml"
        }

        // JNI libraries optimization
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // Enable App Bundle optimizations for size reduction
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
}

//noinspection UseTomlInstead
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui:1.8.1")
    implementation("androidx.compose.ui:ui-graphics:1.8.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.animation:animation:1.8.1")
    implementation("androidx.compose.animation:animation-graphics:1.8.1")
    implementation("androidx.compose.runtime:runtime-livedata:1.8.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.foundation:foundation:1.8.1")
    implementation("androidx.graphics:graphics-shapes-android:1.0.1")
    implementation("androidx.test:monitor:1.7.2")
    implementation("androidx.test.ext:junit-ktx:1.2.1")
	implementation("com.github.bumptech.glide:glide:4.16.0")
	implementation("com.github.bumptech.glide:compose:1.0.0-beta01")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("com.github.bumptech.glide:ktx:1.0.0-beta01")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.6")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha07")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-json:2.3.1")
	implementation("com.github.kaii-lb:Lavender-Snackbars:0.1.7")

    // ML Kit for multi-language OCR text recognition
    implementation("com.google.mlkit:text-recognition:16.0.1") // Latin script (existing)
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1") // Hindi/Devanagari support

    // Additional language support (can be added later if needed)
    // implementation("com.google.mlkit:text-recognition-chinese:16.0.1") // Chinese support
    // implementation("com.google.mlkit:text-recognition-japanese:16.0.1") // Japanese support
    // implementation("com.google.mlkit:text-recognition-korean:16.0.1") // Korean support

    // ML Kit Image Labeling -- fast on-device pre-classification (smart albums feature)
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // ML Kit Translation -- on-device query translation for cross-language search
    implementation("com.google.mlkit:translate:17.0.3")

    // ML Kit Entity Extraction -- pulls structured entities (dates, money, phone numbers,
    // addresses, URLs) out of already-extracted OCR text to sharpen Smart Albums classification
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta6")

    // ML Kit Language Identification -- detects Devanagari/Hindi content so the opt-in
    // Devanagari indexing pass can skip confidently non-Hindi photos
    implementation("com.google.mlkit:language-id:17.0.6")

    // ML Kit Barcode Scanning -- cheap, fast pass alongside OCR pre-scan for receipts/tickets
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // ALTERNATIVE 2: Tesseract4Android (8MB impact - recommended for size)
    // implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")

    // ALTERNATIVE 3: TensorFlow Lite (5MB impact - custom model)
    // implementation("org.tensorflow:tensorflow-lite:2.13.0")
    // implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Coroutines support for Google Play Services (needed for ML Kit)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // WorkManager for background processing
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // For content observer and media monitoring
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    val roomVersion = "2.7.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

	testImplementation("junit:junit:4.13.2")
    // Search-correctness regression suite: real Room/SQLite behavior via Robolectric (fast,
    // pure-JVM, no emulator needed) instead of mocking the database layer, since the bugs
    // worth catching here (FTS4 index going out of sync, MATCH query syntax edge cases) only
    // show up against real SQLite behavior, not a mocked DAO.
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.8.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.1")

    ksp("com.github.bumptech.glide:compiler:4.16.0")
    ksp("androidx.room:room-compiler:$roomVersion")
}

