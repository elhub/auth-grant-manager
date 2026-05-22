import no.elhub.devxp.build.configuration.pipeline.constants.Group
import no.elhub.devxp.build.configuration.pipeline.constants.KubeCluster
import no.elhub.devxp.build.configuration.pipeline.dsl.elhubProject
import no.elhub.devxp.build.configuration.pipeline.extensions.triggerOnVcsChange
import no.elhub.devxp.build.configuration.pipeline.jobs.dockerBuild
import no.elhub.devxp.build.configuration.pipeline.jobs.gitOps
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleJib
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleVerify
import no.elhub.devxp.build.configuration.pipeline.jobs.liquiBuild
import no.elhub.devxp.build.configuration.pipeline.jobs.common.Source

val imageRepo = "auth/auth-grant-manager"
val dbDirectory = "./db"
val liquiEntryPoint = "db-changelog.yaml"
val gitOpsRepo = "https://github.com/elhub/auth"

elhubProject(group = Group.AUTH, name = "auth-grant-manager") {
    pipeline {
        sequential {
            gradleVerify {
                lintImage = "docker.jfrog.elhub.cloud/oxsecurity/megalinter:v8"
                enablePublishMetrics = true
            }

            gradleJib {
                registrySettings = {
                    repository = imageRepo
                }
            }

            parallel {
                liquiBuild {
                    registrySettings = {
                        repository = imageRepo
                    }
                    changelogDirectory = dbDirectory
                    liquibaseEntrypoint = liquiEntryPoint
                }

                dockerBuild {
                    source = Source.CommitSha
                    dockerfileName = "integration-test/Dockerfile"
                    contextDirectory = "."
                    dockerBuildNameSuffix = "Integration Test"
                    registrySettings = {
                        repository = "$imageRepo-test"
                    }
                    tags = setOf("latest")
                }
            }

            parallel {
                gitOps {
                    clusters = setOf(
                        KubeCluster.TEST9,
                        KubeCluster.TEST8,
                        KubeCluster.TEST11,
                        KubeCluster.TEST13,
                        KubeCluster.TEST14
                    )
                    gitOpsRepository = gitOpsRepo
                    autoMerge = true
                    enableChangelog = true
                }.triggerOnVcsChange()

                gitOps {
                    clusters = setOf(KubeCluster.MARKET_TRIAL_1)
                    gitOpsRepository = gitOpsRepo
                    enableChangelog = true
                }

                gitOps {
                    clusters = setOf(KubeCluster.PROD1)
                    gitOpsRepository = gitOpsRepo
                    enableChangelog = true
                }
            }
        }
    }
}
