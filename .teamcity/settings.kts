import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.toId
import no.elhub.devxp.build.configuration.pipeline.constants.AgentScope
import no.elhub.devxp.build.configuration.pipeline.constants.Group
import no.elhub.devxp.build.configuration.pipeline.dsl.elhubProject
import no.elhub.devxp.build.configuration.pipeline.jobs.customJob
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleVerify

val imageRepo = "auth/auth-grant-manager"
val dbDirectory = "./db"
val liquiEntryPoint = "db-changelog.yaml"
val gitOpsRepo = "https://github.com/elhub/auth"

elhubProject(group = Group.AUTH, name = "auth-grant-manager") {
    pipeline {

        sequential {
            gradleVerify {
                lintImage = "docker.jfrog.elhub.cloud/oxsecurity/megalinter:v8"
                enablePublishMetrics = false
            }
            customJob(AgentScope.LinuxAgentContext) {
                name = "Publish metrics to Opslevel"
                id(name.toId())
                steps {
                    script {
                        name = "OpsLevel Push"
                        scriptContent = """
                            echo "Do the things!"
                        """.trimIndent()
                    }
                }
            }
        }

    }
}
