import no.elhub.devxp.build.configuration.pipeline.constants.Group
import no.elhub.devxp.build.configuration.pipeline.constants.KubeCluster
import no.elhub.devxp.build.configuration.pipeline.dsl.elhubProject
import no.elhub.devxp.build.configuration.pipeline.extensions.triggerOnVcsChange
import no.elhub.devxp.build.configuration.pipeline.jobs.gitOps
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleJib
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleVerify
import no.elhub.devxp.build.configuration.pipeline.jobs.liquiBuild

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

            liquiBuild {
                registrySettings = {
                    repository = imageRepo
                }
                changelogDirectory = dbDirectory
                liquibaseEntrypoint = liquiEntryPoint
            }

            parallel {
                gitOps {
                    clusters = setOf(KubeCluster.TEST9)
                    gitOpsRepository = gitOpsRepo
                    autoMerge = true
                    enableChangelog = true
                }.triggerOnVcsChange()

                gitOps {
                    clusters = setOf(KubeCluster.TEST8)
                    gitOpsRepository = gitOpsRepo
                    autoMerge = true
                    enableChangelog = true
                }.triggerOnVcsChange()

                gitOps {
                    clusters = setOf(KubeCluster.TEST11)
                    gitOpsRepository = gitOpsRepo
                    enableChangelog = true
                }.triggerOnVcsChange()

                gitOps {
                    clusters = setOf(KubeCluster.MARKET_TRIAL_1)
                    gitOpsRepository = gitOpsRepo
                    enableChangelog = true
                }

                gitOps {
                    clusters = setOf(KubeCluster.TEST13)
                    gitOpsRepository = gitOpsRepo
                    enableChangelog = true
                }

                gitOps {
                    clusters = setOf(KubeCluster.TEST14)
                    gitOpsRepository = gitOpsRepo
                    enableChangelog = true
                }
            }
        }
    }
}
