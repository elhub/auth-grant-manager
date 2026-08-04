plugins {
    kotlin("jvm") version "2.4.0"
}

repositories {
    maven(url = "https://jfrog.elhub.cloud:443/artifactory/elhub-mvn")
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
    implementation("com.github.erosb:json-sKema:0.31.0")

    testImplementation("io.kotest:kotest-runner-junit5:6.2.1")
    testImplementation("io.kotest:kotest-assertions-core:6.2.1")
}

tasks.test {
    useJUnitPlatform()
}
