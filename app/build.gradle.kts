plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.adastra.focuswallpaper"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.adastra.focuswallpaper"
        minSdk = 26
        targetSdk = 34
        versionCode = 26
        versionName = "1.24"
    }
    signingConfigs.getByName("debug") {
        System.getenv("JIGEUM_DEBUG_KEYSTORE")?.let { storeFile = file(it) }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") }
    }
}
dependencies { implementation("androidx.viewpager2:viewpager2:1.1.0"); testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303"); testImplementation("org.robolectric:robolectric:4.14.1") }
