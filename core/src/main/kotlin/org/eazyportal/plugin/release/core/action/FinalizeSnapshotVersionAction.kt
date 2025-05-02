package org.eazyportal.plugin.release.core.action

import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FinalizeSnapshotVersionAction<T>(
    private val projectDescriptor: ProjectDescriptor<T>,
    private val scmActions: ScmActions<T>,
) : AbstractReleaseAction<T>(scmActions) {

    override fun execute() {
        LOGGER.info("Finalizing snapshot version...")

        val snapshotVersion = projectDescriptor.rootProject.projectActions.getVersion()

        projectDescriptor.allProjects.forEach {
            scmActions.add(it.dir, *it.projectActions.scmFilesToCommit())
            scmActions.commit(it.dir, "New SNAPSHOT version: $snapshotVersion")
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(FinalizeSnapshotVersionAction::class.java)
    }

}