package org.eazyportal.plugin.release.core.action

import org.eazyportal.plugin.release.core.project.model.Project
import org.eazyportal.plugin.release.core.scm.ScmActions

abstract class AbstractReleaseAction<T>(
    private val scmActions: ScmActions<T>,
) : ReleaseAction {

    protected fun hasReleasableChanges(project: Project<T>): Boolean {
        val lastTag = scmActions.getLastTag(project.dir)
        val commits = scmActions.getCommits(project.dir, lastTag)
            .filter {
                !it.startsWith(SNAPSHOT_VERSION_COMMIT_PREFIX) && !it.startsWith(RELEASE_VERSION_COMMIT_PREFIX)
            }

        return commits.isNotEmpty()
    }

    companion object {
        const val RELEASE_VERSION_COMMIT_PREFIX = "Release version:"
        const val SNAPSHOT_VERSION_COMMIT_PREFIX = "New SNAPSHOT version:"
    }

}
