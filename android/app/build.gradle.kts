plugins { id("com.android.application") }

fun readLocalEnv(name: String): Map<String, String> {
    val file = rootProject.file("../$name")
    if (!file.exists()) return emptyMap()
    return file.readLines().mapNotNull { raw ->
        val line = raw.trim().removePrefix("export ")
        if (line.startsWith("#") || !line.contains('=')) null else {
            val (key, value) = line.split('=', limit = 2)
            key.trim() to value.trim().removeSurrounding("\"").removeSurrounding("'")
        }
    }.toMap()
}
val personalEnv = readLocalEnv(".env")
val signingEnv = readLocalEnv(".env.signing")
fun personalValue(key: String, fallback: String = "") = System.getenv(key) ?: personalEnv[key] ?: fallback
fun signingValue(key: String) = System.getenv(key) ?: signingEnv[key] ?: ""
fun javaString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
android {
    namespace = "dev.thinqclimate.widget"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.thinqclimate.widget"; minSdk = 26; targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 8; versionName = "1.6.0"
        manifestPlaceholders["appLabel"] = "Climate Widget"
        // Public builds always start with empty credentials, including builds on a PC with .env.
        buildConfigField("String", "THINQ_PAT", javaString(""))
        buildConfigField("String", "THINQ_COUNTRY", javaString("KR"))
        buildConfigField("String", "THINQ_REGION", javaString("kic"))
    }
    signingConfigs {
        if (signingValue("ANDROID_KEYSTORE_FILE").isNotBlank()) create("distribution") {
            storeFile = rootProject.file(signingValue("ANDROID_KEYSTORE_FILE"))
            storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = signingValue("ANDROID_KEY_ALIAS")
            keyPassword = signingValue("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("debug") {
            // Keep the existing personal app ID so its Keystore and widget settings survive updates.
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "Climate Widget Dev"
            buildConfigField("String", "THINQ_PAT", javaString(personalValue("THINQ_PAT")))
            buildConfigField("String", "THINQ_COUNTRY", javaString(personalValue("THINQ_COUNTRY", "KR")))
            buildConfigField("String", "THINQ_REGION", javaString(personalValue("THINQ_REGION", "kic")))
        }
        getByName("release") {
            // Separate public/personal apps avoid signing conflicts with older personal debug installs.
            applicationIdSuffix = ".release"
            isDebuggable = false
            signingConfig = signingConfigs.findByName("distribution")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { abortOnError = true; warningsAsErrors = false }
}
dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303"); coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4"); androidTestImplementation("androidx.test.ext:junit:1.2.1"); androidTestImplementation("androidx.test:runner:1.6.2"); implementation(project(":core")); implementation("androidx.work:work-runtime:2.10.0") }
