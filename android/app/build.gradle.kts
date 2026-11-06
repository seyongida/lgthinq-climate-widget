plugins { id("com.android.application") }
android {
    namespace = "dev.thinqclimate.widget"
    compileSdk = 35
    defaultConfig { applicationId = "dev.thinqclimate.widget"; minSdk = 26; targetSdk = 35; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; versionCode = 1; versionName = "1.0.0" }
    compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { abortOnError = true; warningsAsErrors = false }
}
dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303"); coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4"); androidTestImplementation("androidx.test.ext:junit:1.2.1"); androidTestImplementation("androidx.test:runner:1.6.2"); implementation(project(":core")); implementation("androidx.work:work-runtime:2.10.0") }
