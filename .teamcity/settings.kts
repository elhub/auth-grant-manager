import no.elhub.devxp.build.configuration.pipeline.ElhubProject.Companion.elhubProject
import no.elhub.devxp.build.configuration.pipeline.constants.Group
import no.elhub.devxp.build.configuration.pipeline.constants.KubeCluster
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
            gradleVerify()

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
                    cluster = KubeCluster.TEST9
                    gitOpsRepository = gitOpsRepo
                    autoMerge = true
                }.triggerOnVcsChange()

                gitOps {
                    cluster = KubeCluster.TEST11
                    gitOpsRepository = gitOpsRepo
                }.triggerOnVcsChange()

                gitOps {
                    cluster = KubeCluster.TEST13
                    gitOpsRepository = gitOpsRepo
                }.triggerOnVcsChange()

                gitOps {
                    cluster = KubeCluster.TEST14
                    gitOpsRepository = gitOpsRepo
                }.triggerOnVcsChange()
            }
        }
    }
}
