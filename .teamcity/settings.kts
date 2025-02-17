import no.elhub.devxp.build.configuration.pipeline.ElhubProject.Companion.elhubProject
import no.elhub.devxp.build.configuration.pipeline.constants.Group
import no.elhub.devxp.build.configuration.pipeline.constants.KubeCluster
import no.elhub.devxp.build.configuration.pipeline.extensions.triggerOnVcsChange
import no.elhub.devxp.build.configuration.pipeline.jobs.gitOps
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleJib
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleVerify

val imageRepo = "devxp/deploy-logger-v2" // TODO: Change this in terraform
val gitOpsRepo = "https://github.com/elhub/devxp"

elhubProject(group = Group.DEVXP, name = "deploy-orchestrator") {
    pipeline {
        sequential {
            gradleVerify()

            gradleJib {
                registrySettings = {
                    repository = imageRepo
                }
            }

            // These can be parallel as they are not the actual deploy,
            // just the PR setup to deploy into the given environment
            parallel {
                gitOps {
                    cluster = KubeCluster.CIRRUS_TEST
                    gitOpsRepository = gitOpsRepo
                }.triggerOnVcsChange()

                gitOps {
                    cluster = KubeCluster.CIRRUS_PROD
                    gitOpsRepository = gitOpsRepo
                }.triggerOnVcsChange()
            }
        }
    }
}
