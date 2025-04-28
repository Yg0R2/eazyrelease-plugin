package org.eazyportal.plugin.release.gradle.action

import org.eazyportal.plugin.release.core.action.FinalizeReleaseVersionAction
import org.eazyportal.plugin.release.core.action.FinalizeSnapshotVersionAction
import org.eazyportal.plugin.release.core.action.PrepareRepositoryForReleaseAction
import org.eazyportal.plugin.release.core.action.ReleaseAction
import org.eazyportal.plugin.release.core.action.SetReleaseVersionAction
import org.eazyportal.plugin.release.core.action.SetSnapshotVersionAction
import org.eazyportal.plugin.release.core.action.UpdateScmAction
import org.eazyportal.plugin.release.core.project.ProjectDescriptorFactory
import org.eazyportal.plugin.release.core.project.model.FileSystemProjectFile
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.version.ReleaseVersionProvider
import org.eazyportal.plugin.release.core.version.SnapshotVersionProvider
import org.eazyportal.plugin.release.core.version.VersionIncrementProvider
import org.eazyportal.plugin.release.gradle.model.EazyReleasePluginExtension
import org.gradle.api.Project
import java.io.File

class ReleaseActionFactory(
    val actionContextFactory: ActionContextFactory,
    val projectDescriptorFactory: ProjectDescriptorFactory<File>
) {

    inline fun <reified T : ReleaseAction> create(project: Project): T {
        val actionContext = actionContextFactory.create(project.providers)

        val extension = project.extensions.getByType(EazyReleasePluginExtension::class.java)

        val projectDescriptor: ProjectDescriptor<File> = projectDescriptorFactory.create(
            extension.projectActionsFactory,
            extension.scmActions,
            FileSystemProjectFile(project.projectDir)
        )

        return when (T::class) {
            FinalizeReleaseVersionAction::class -> FinalizeReleaseVersionAction(
                actionContext,
                projectDescriptor,
                extension.scmActions
            )

            FinalizeSnapshotVersionAction::class -> FinalizeSnapshotVersionAction(
                actionContext,
                projectDescriptor,
                extension.scmActions
            )

            PrepareRepositoryForReleaseAction::class -> PrepareRepositoryForReleaseAction(
                projectDescriptor,
                extension.scmActions,
                extension.scmConfig
            )

            SetReleaseVersionAction::class -> SetReleaseVersionAction(
                actionContext,
                extension.conventionalCommitTypes,
                ReleaseVersionProvider(),
                projectDescriptor,
                extension.scmActions,
                extension.scmConfig,
                VersionIncrementProvider()
            )

            SetSnapshotVersionAction::class -> SetSnapshotVersionAction(
                actionContext,
                projectDescriptor,
                extension.scmActions,
                extension.scmConfig,
                SnapshotVersionProvider()
            )

            UpdateScmAction::class -> UpdateScmAction(
                projectDescriptor,
                extension.scmActions,
                extension.scmConfig
            )

            else -> throw IllegalArgumentException("Invalid ReleaseAction implementation: ${T::class}")
        } as T
    }

}
