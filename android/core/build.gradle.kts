plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.register<JavaExec>("contractTest") { dependsOn("testClasses"); classpath = sourceSets["test"].runtimeClasspath; mainClass.set("dev.thinqclimate.core.CoreTest") }
tasks.check { dependsOn("contractTest") }
