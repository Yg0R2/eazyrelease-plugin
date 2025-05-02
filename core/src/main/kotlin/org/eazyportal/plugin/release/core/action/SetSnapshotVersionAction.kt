package org.eazyportal.plugin.release.core.action

import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.eazyportal.plugin.release.core.scm.model.ScmConfig
import org.eazyportal.plugin.release.core.version.SnapshotVersionProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SetSnapshotVersionAction<T>(
    private val projectDescriptor: ProjectDescriptor<T>,
    private val scmActions: ScmActions<T>,
    private val scmConfig: ScmConfig,
    private val snapshotVersionProvider: SnapshotVersionProvider
) : AbstractReleaseAction<T>(scmActions) {

    private companion object {
        @JvmStatic
        val LOGGER: Logger = LoggerFactory.getLogger(SetSnapshotVersionAction::class.java)
    }

    override fun execute() {
        LOGGER.info("Setting snapshot version...")

        val snapshotVersion = projectDescriptor.rootProject.projectActions.getVersion()
            .let { snapshotVersionProvider.provide(it) }

        projectDescriptor.allProjects.forEach {
            if (scmConfig.releaseBranch != scmConfig.featureBranch) {
                scmActions.checkout(it.dir, scmConfig.featureBranch)

                scmActions.mergeNoCommit(it.dir, scmConfig.releaseBranch)
            }

            it.projectActions.setVersion(snapshotVersion)
        }
    }

}
