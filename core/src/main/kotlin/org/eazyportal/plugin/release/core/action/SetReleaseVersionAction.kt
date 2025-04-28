package org.eazyportal.plugin.release.core.action

import org.eazyportal.plugin.release.core.action.model.ActionContext
import org.eazyportal.plugin.release.core.project.model.Project
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.project.model.ProjectFile
import org.eazyportal.plugin.release.core.scm.ConventionalCommitType
import org.eazyportal.plugin.release.core.scm.ScmActions
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
) : AbstractReleaseAction<T>(scmActions) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(SetReleaseVersionAction::class.java)
    }

    override fun execute() {
        LOGGER.info("Setting release version...")

        // Get the release version from the current branch
        val releaseVersion = getReleaseVersion(projectDescriptor.allProjects)

        projectDescriptor.subProjects
            .asSequence()
            .filter { actionContext.isForceRelease || hasReleasableChanges(it) }
            .forEach {
                checkoutToReleaseBranch(it)

                it.projectActions.setVersion(releaseVersion)
            }

        projectDescriptor.rootProject.run {
            checkoutToReleaseBranch(this)

            projectActions.setVersion(releaseVersion)
        }
    }

    private fun checkoutToReleaseBranch(project: Project<T>) {
        if (scmConfig.releaseBranch != scmConfig.featureBranch) {
            scmActions.checkout(project.dir, scmConfig.releaseBranch)

            scmActions.mergeNoCommit(project.dir, scmConfig.featureBranch)
        }
    }

    private fun getReleaseVersion(projects: List<Project<T>>): Version {
        val (currentVersions, versionIncrements) = projects.map {
            val currentVersion = it.projectActions.getVersion().also { version -> println("$it - $version") }
            val versionIncrement = getVersionIncrement(it.dir).also { versionIncrement -> println("$it - $versionIncrement") }

            currentVersion to versionIncrement
        }.unzip()

        val highestCurrentVersion = currentVersions.maxWith(VersionComparator())
        val highestVersionIncrement = versionIncrements.filter { (it != null) && (it != NONE) }
            .maxByOrNull { it!!.priority }
            ?: throw IllegalArgumentException("There are no acceptable commits.")

        return releaseVersionProvider.provide(highestCurrentVersion, highestVersionIncrement)
    }

    private fun getVersionIncrement(projectDir: ProjectFile<T>): VersionIncrement? {
        val lastTag = scmActions.getLastTag(projectDir)
            .also {
                if (it == null) {
                    LOGGER.warn("Ignoring missing Git tag from release version calculation.")
                }
            }

        val commitBasedVersionIncrement = scmActions.getCommits(projectDir, lastTag)
            .let { versionIncrementProvider.provide(it, conventionalCommitTypes) }

        return if (actionContext.isForceRelease && ((commitBasedVersionIncrement == null) || (commitBasedVersionIncrement == NONE))) {
            PATCH
        } else {
            commitBasedVersionIncrement
        }
    }

}
