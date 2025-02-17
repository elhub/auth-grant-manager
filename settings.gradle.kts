rootProject.name = "devxp-ktor-service-template"

pluginManagement {
    repositories {
        maven(url = "https://jfrog.elhub.cloud:443/artifactory/elhub-mvn")
    }
}

dependencyResolutionManagement {
    repositories {
        maven(url = "https://jfrog.elhub.cloud:443/artifactory/elhub-mvn")
    }
    versionCatalogs {
        create("libs") {
            from("no.elhub.devxp:devxp-version-catalog:0.6.0")
        }
    }
}
