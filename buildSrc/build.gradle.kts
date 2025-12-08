plugins {
    kotlin("jvm") version "2.2.21"
}

repositories {
    maven(url = "https://jfrog.elhub.cloud:443/artifactory/elhub-mvn")
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")
    implementation("com.networknt:json-schema-validator:1.5.9")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")
}
