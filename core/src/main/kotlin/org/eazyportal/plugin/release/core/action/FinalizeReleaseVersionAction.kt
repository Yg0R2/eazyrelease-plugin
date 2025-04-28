package org.eazyportal.plugin.release.core.action

import org.eazyportal.plugin.release.core.action.model.ActionContext
import org.eazyportal.plugin.release.core.project.model.Project
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.eazyportal.plugin.release.core.version.model.Version
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FinalizeReleaseVersionAction<T>(
    private val actionContext: ActionContext,
    private val projectDescriptor: ProjectDescriptor<T>,
    private val scmActions: ScmActions<T>,
) : AbstractReleaseAction<T>(scmActions) {

    override fun execute() {
        LOGGER.info("Finalize release version...")

        val releaseVersion = projectDescriptor.rootProject.projectActions.getVersion()

        projectDescriptor.subProjects
            .asSequence()
            .filter { actionContext.isForceRelease || hasReleasableChanges(it) }
            .forEach { commitReleaseVersion(it, releaseVersion) }

        // Have to include subproject changes in the root project commit
        commitReleaseVersion(projectDescriptor.rootProject, releaseVersion)
    }

    private fun commitReleaseVersion(project: Project<T>, releaseVersion: Version) {
        scmActions.add(project.dir, *project.projectActions.scmFilesToCommit())
        scmActions.commit(project.dir, "$RELEASE_VERSION_COMMIT_PREFIX $releaseVersion")

        scmActions.tag(project.dir, releaseVersion)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(FinalizeReleaseVersionAction::class.java)
    }

}
