plugins {
    kotlin("jvm") version "2.2.10"
}

repositories {
    maven(url = "https://jfrog.elhub.cloud:443/artifactory/elhub-mvn")
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81")
    implementation("com.networknt:json-schema-validator:1.5.8")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.2")
}
