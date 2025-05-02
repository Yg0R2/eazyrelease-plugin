package org.eazyportal.plugin.release.core.action

import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FinalizeReleaseVersionAction<T>(
    private val projectDescriptor: ProjectDescriptor<T>,
    private val scmActions: ScmActions<T>,
) : AbstractReleaseAction<T>(scmActions) {

    override fun execute() {
        LOGGER.info("Finalize release version...")

        val releaseVersion = projectDescriptor.rootProject.projectActions.getVersion()

        projectDescriptor.allProjects.forEach {
            scmActions.add(it.dir, *it.projectActions.scmFilesToCommit())
            scmActions.commit(it.dir, "Release version: $releaseVersion")

            scmActions.tag(it.dir, releaseVersion)
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(FinalizeReleaseVersionAction::class.java)
    }

}
