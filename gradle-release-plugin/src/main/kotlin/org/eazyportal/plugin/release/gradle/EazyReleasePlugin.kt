package org.eazyportal.plugin.release.gradle

import org.eazyportal.plugin.release.core.version.model.Version
import org.eazyportal.plugin.release.gradle.tasks.EazyBaseTask
import org.eazyportal.plugin.release.gradle.tasks.SetReleaseVersionTask
import org.eazyportal.plugin.release.gradle.tasks.SetSnapshotVersionTask
import org.eazyportal.plugin.release.gradle.tasks.UpdateScmTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class EazyReleasePlugin : Plugin<Project> {

    companion object {
        const val RELEASE_TASK_NAME = "release"
        const val SET_RELEASE_VERSION_TASK_NAME = "setReleaseVersion"
        const val SET_SNAPSHOT_VERSION_TASK_NAME = "setSnapshotVersion"
        const val UPDATE_SCM_TASK_NAME = "updateScm"
    }

    override fun apply(project: Project) {
        val extension = project.extensions.create("release", EazyReleasePluginExtension::class.java)

        project.tasks.apply {
            register(SET_RELEASE_VERSION_TASK_NAME, SetReleaseVersionTask::class.java) {
                it.releaseVersion = Version(0, 0, 1)
            }

            val buildTask = getByName("build").also {
                it.mustRunAfter(SET_RELEASE_VERSION_TASK_NAME)
            }

            register(RELEASE_TASK_NAME, EazyBaseTask::class.java) {
                it.dependsOn(SET_RELEASE_VERSION_TASK_NAME, buildTask)

                it.finalizedBy(SET_SNAPSHOT_VERSION_TASK_NAME, UPDATE_SCM_TASK_NAME)
            }

            register(SET_SNAPSHOT_VERSION_TASK_NAME, SetSnapshotVersionTask::class.java) {
                it.mustRunAfter(SET_RELEASE_VERSION_TASK_NAME, RELEASE_TASK_NAME)

                it.snapshotVersion = Version(0, 0, 2, "SNAPSHOT")
            }

            register(UPDATE_SCM_TASK_NAME, UpdateScmTask::class.java) {
                it.mustRunAfter(SET_SNAPSHOT_VERSION_TASK_NAME)
            }
        }
    }

}
