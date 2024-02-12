package org.eazyportal.plugin.release.gradle.tasks

import org.eazyportal.plugin.release.core.action.FinalizeReleaseVersionAction
import org.eazyportal.plugin.release.gradle.action.ReleaseActionFactory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

open class FinalizeReleaseVersionTask @Inject constructor(
    private val releaseActionFactory: ReleaseActionFactory
) : EazyReleaseBaseTask() {

    @TaskAction
    fun run() {
        logger.quiet("Finalizing release version...")

        releaseActionFactory.create<FinalizeReleaseVersionAction<File>>(project)
            .execute()
    }

}
