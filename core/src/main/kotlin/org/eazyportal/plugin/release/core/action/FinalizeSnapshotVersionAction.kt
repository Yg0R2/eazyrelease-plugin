package org.eazyportal.plugin.release.core.action

import org.eazyportal.plugin.release.core.action.model.ActionContext
import org.eazyportal.plugin.release.core.project.model.Project
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.eazyportal.plugin.release.core.version.model.Version
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FinalizeSnapshotVersionAction<T>(
    private val actionContext: ActionContext,
    private val projectDescriptor: ProjectDescriptor<T>,
    private val scmActions: ScmActions<T>,
) : AbstractReleaseAction<T>(scmActions) {

    override fun execute() {
        LOGGER.info("Finalizing snapshot version...")

        val snapshotVersion = projectDescriptor.rootProject.projectActions.getVersion()

        projectDescriptor.subProjects
            .asSequence()
            .filter { actionContext.isForceRelease || hasReleasableChanges(it) }
            .forEach { commitSnapshotVersion(it, snapshotVersion) }

        // Have to include subproject changes in the root project commit
        commitSnapshotVersion(projectDescriptor.rootProject, snapshotVersion)
    }

    private fun commitSnapshotVersion(project: Project<T>, snapshotVersion: Version) {
        scmActions.add(project.dir, *project.projectActions.scmFilesToCommit())
        scmActions.commit(project.dir, "$SNAPSHOT_VERSION_COMMIT_PREFIX $snapshotVersion")
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(FinalizeSnapshotVersionAction::class.java)
    }

}