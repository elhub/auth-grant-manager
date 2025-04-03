import no.elhub.devxp.build.configuration.pipeline.ElhubProject.Companion.elhubProject
import no.elhub.devxp.build.configuration.pipeline.constants.Group
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleJib
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleVerify

val imageRepo = "auth/auth-grant-manager" // TODO: Change this in terraform
val gitOpsRepo = "https://github.com/elhub/auth"

elhubProject(group = Group.AUTH, name = "auth-grant-manager") {
    pipeline {
        sequential {
            gradleVerify {
            }

            gradleJib {
                registrySettings = {
                    repository = imageRepo
                }
            }
        }
    }
}
