package org.eazyportal.plugin.release.core.action

import org.eazyportal.plugin.release.core.action.model.ActionContext
import org.eazyportal.plugin.release.core.project.model.Project
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.project.model.ProjectFile
import org.eazyportal.plugin.release.core.scm.ConventionalCommitType
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.eazyportal.plugin.release.core.scm.exception.ScmActionException
import org.eazyportal.plugin.release.core.scm.model.ScmConfig
import org.eazyportal.plugin.release.core.version.ReleaseVersionProvider
import org.eazyportal.plugin.release.core.version.VersionIncrementProvider
import org.eazyportal.plugin.release.core.version.model.Version
import org.eazyportal.plugin.release.core.version.model.VersionComparator
import org.eazyportal.plugin.release.core.version.model.VersionIncrement
import org.eazyportal.plugin.release.core.version.model.VersionIncrement.NONE
import org.eazyportal.plugin.release.core.version.model.VersionIncrement.PATCH
import org.slf4j.LoggerFactory

open class SetReleaseVersionAction<T>(
    private val actionContext: ActionContext,
    private val conventionalCommitTypes: List<ConventionalCommitType>,
    private val releaseVersionProvider: ReleaseVersionProvider,
    private val projectDescriptor: ProjectDescriptor<T>,
    private val scmActions: ScmActions<T>,
    private val scmConfig: ScmConfig,
    private val versionIncrementProvider: VersionIncrementProvider
) : ReleaseAction {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(SetReleaseVersionAction::class.java)
    }

    override fun execute() {
        LOGGER.info("Setting release version...")

        projectDescriptor.allProjects.run {
            val releaseVersion = getReleaseVersion(this, actionContext.isForceRelease)

            forEach {
                if (scmConfig.releaseBranch != scmConfig.featureBranch) {
                    scmActions.checkout(it.dir, scmConfig.releaseBranch)

                    scmActions.mergeNoCommit(it.dir, scmConfig.featureBranch)
                }

                it.projectActions.setVersion(releaseVersion)
            }
        }
    }

    private fun getReleaseVersion(projects: List<Project<T>>, isForceRelease: Boolean): Version =
        projects.mapNotNull {
            val currentVersion = it.projectActions.getVersion()
            val versionIncrement = getVersionIncrement(it.dir, isForceRelease)

            if ((versionIncrement == null) || (versionIncrement == NONE)) {
                null
            } else {
                releaseVersionProvider.provide(currentVersion, versionIncrement)
            }
        }
        .maxWithOrNull(VersionComparator())
        ?: throw IllegalArgumentException("There are no acceptable commits.")

    private fun getVersionIncrement(projectDir: ProjectFile<T>, isForceRelease: Boolean): VersionIncrement? {
        val lastTag = scmActions.getLastTag(projectDir)
            .also {
                if (it == null) {
                    LOGGER.warn("Ignoring missing Git tag from release version calculation.")
                }
            }

        val commitBasedVersionIncrement = scmActions.getCommits(projectDir, lastTag)
            .let { versionIncrementProvider.provide(it, conventionalCommitTypes) }

        return if (isForceRelease && ((commitBasedVersionIncrement == null) || (commitBasedVersionIncrement == NONE))) {
            PATCH
        } else {
            commitBasedVersionIncrement
        }
    }

}
