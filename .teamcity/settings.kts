import no.elhub.devxp.build.configuration.pipeline.ElhubProject.Companion.elhubProject
import no.elhub.devxp.build.configuration.pipeline.constants.Group
import no.elhub.devxp.build.configuration.pipeline.constants.KubeCluster
import no.elhub.devxp.build.configuration.pipeline.extensions.triggerOnVcsChange
import no.elhub.devxp.build.configuration.pipeline.jobs.gitOps
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleJib
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleVerify

val imageRepo = "auth/auth-consent-manager" // TODO: Change this in terraform
val gitOpsRepo = "https://github.com/elhub/auth"

elhubProject(group = Group.AUTH, name = "auth-consent-manager") {
    pipeline {
        sequential {
            gradleVerify()

            gradleJib {
                registrySettings = {
                    repository = imageRepo
                }
            }
        }
    }
}
