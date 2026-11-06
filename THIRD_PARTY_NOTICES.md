# Third-party components

## Gradle Wrapper 8.9

`android/gradlew`, `android/gradlew.bat`, and `android/gradle/wrapper/gradle-wrapper.jar` originate from [Gradle v8.9.0](https://github.com/gradle/gradle/tree/v8.9.0), copyright Gradle contributors, licensed under Apache License 2.0. Original script headers are preserved. The complete license is also inside the wrapper JAR at `META-INF/LICENSE`.

Wrapper JAR SHA-256: `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`.

The Gradle 8.9 binary distribution is checked against SHA-256 `d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab` in wrapper properties.

## Build dependencies

AndroidX WorkManager, AndroidX Test, JUnit, the Android build plugin, desugar_jdk_libs, and test-only JSON-java are resolved by Gradle under their respective licenses. They are not copied into this source tree. LG's public SDK API contract and public client identifier key are referenced in `docs/ARCHITECTURE.md`; user PAT credentials are never included.
