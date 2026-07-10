plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDirs(
            "../app/src/main/java/com/codro/listenstudy/domain/text",
            "../app/src/main/java/com/codro/listenstudy/domain/player",
        )
    }
    test {
        kotlin.srcDirs(
            "../app/src/test/java/com/codro/listenstudy/domain/text",
            "../app/src/test/java/com/codro/listenstudy/domain/player",
        )
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}
