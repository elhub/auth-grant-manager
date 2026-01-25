plugins {
    kotlin("jvm") version "2.3.0"
}

repositories {
    maven(url = "https://jfrog.elhub.cloud:443/artifactory/elhub-mvn")
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")
    implementation("com.networknt:json-schema-validator:3.0.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.0")
}
