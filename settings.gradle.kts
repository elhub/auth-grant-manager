rootProject.name = "auth-grant-manager"

pluginManagement {

    repositories {
        mavenCentral()
        maven {
            url = uri("https://jfrog.elhub.cloud:443/artifactory/elhub-mvn")
            mavenContent {
                releasesOnly()
            }
        }
    }
}
