plugins {
    kotlin("jvm")
    `java-library`
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation("com.hp.jipp:jipp-core:0.7.18")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
