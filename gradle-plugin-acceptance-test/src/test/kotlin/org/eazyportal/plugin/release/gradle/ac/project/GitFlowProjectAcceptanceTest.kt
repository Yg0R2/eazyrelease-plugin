package org.eazyportal.plugin.release.gradle.ac.project

import org.assertj.core.api.Assertions.assertThat
import org.eazyportal.plugin.release.core.project.model.FileSystemProjectFile
import org.eazyportal.plugin.release.gradle.EazyReleasePlugin
import org.eazyportal.plugin.release.gradle.project.GradleProjectActions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

@TestMethodOrder(value = OrderAnnotation::class)
internal class GitFlowProjectAcceptanceTest : BaseProjectAcceptanceTest() {

    companion object {
        private lateinit var GRADLE_PROJECT_ACTIONS: GradleProjectActions<File>

        @BeforeAll
        @JvmStatic
        fun initialize() {
            GRADLE_PROJECT_ACTIONS = GradleProjectActions(PROJECT_DIR)
        }
    }

    @Order(0)
    @Test
    fun test_initializeRepository() {
        // GIVEN
        ORIGIN_PROJECT_DIR.run {
            SCM_ACTIONS.execute(this, "init", "--initial-branch=main")

            initializeGradleProject()
        }

        SCM_ACTIONS.add(ORIGIN_PROJECT_DIR, ".")
        SCM_ACTIONS.commit(ORIGIN_PROJECT_DIR, "initial commit")

        // WHEN
        // THEN
        assertThat(SCM_ACTIONS.getCommits(ORIGIN_PROJECT_DIR)).containsExactly("initial commit")

        SCM_ACTIONS.execute(ORIGIN_PROJECT_DIR, "checkout", "-b", "dev")

        SCM_ACTIONS.execute(FileSystemProjectFile(WORKING_DIR), "clone", ORIGIN_PROJECT_DIR.resolve(".git").getFile().path, PROJECT_NAME)

        // Checking out to a different branch will fix: "remote: error: refusing to update checked out branch: refs/heads/feature"
        SCM_ACTIONS.execute(ORIGIN_PROJECT_DIR, "checkout", "-b", "tmp")
    }

    @Order(1)
    @Test
    fun test_release_shouldFail_whenThereAreNoAcceptableCommits() {
        // GIVEN
        // WHEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.RELEASE_TASK_NAME)
            .buildAndFail()

        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "Preparing repository for release...",
            "Setting release version...",
            "Ignoring missing Git tag from release version calculation.",
            "Ignoring invalid commit: initial commit",
            "FAILURE: Build failed with an exception.",
            "* What went wrong:",
            "Execution failed for task ':setReleaseVersion'.",
            "> There are no acceptable commits."
        )
    }

    @Order(10)
    @Test
    fun test_prepareRepositoryForRelease() {
        // GIVEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.PREPARE_REPOSITORY_FOR_RELEASE_TASK_NAME)
            .build()

        // WHEN
        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :prepareRepositoryForRelease",
            "Preparing repository for release...",
            "1 actionable task: 1 executed"
        )
    }

    @Order(11)
    @Test
    fun test_setReleaseVersion() {
        // GIVEN
        PROJECT_DIR.copyIntoFromResources("src/main/java/org/eazyportal/plugin/release/test/dummy/DummyApplication.java")

        SCM_ACTIONS.add(PROJECT_DIR, ".")
        SCM_ACTIONS.commit(PROJECT_DIR, "feature: implement feature")

        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.SET_RELEASE_VERSION_TASK_NAME)
            .build()

        // WHEN
        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :setReleaseVersion",
            "Setting release version...",
            "1 actionable task: 1 executed"
        )

        SCM_ACTIONS.execute(PROJECT_DIR, "status").run {
            assertThat(lines()).containsSubsequence(
                "On branch main",
                "Changes to be committed:",
                "\tnew file:   src/main/java/org/eazyportal/plugin/release/test/dummy/DummyApplication.java",
                "Changes not staged for commit:",
                "\tmodified:   gradle.properties"
            )
        }

        assertThat(SCM_ACTIONS.getCommits(PROJECT_DIR).first())
            .isEqualTo("initial commit")

        assertThat(SCM_ACTIONS.getLastTag(PROJECT_DIR))
            .isNull()

        assertThat(GRADLE_PROJECT_ACTIONS.getVersion())
            .hasToString("0.1.0")
    }

    @Order(12)
    @Test
    fun test_finalizeReleaseVersion() {
        // GIVEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.FINALIZE_RELEASE_VERSION_TASK_NAME)
            .build()

        // WHEN
        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :finalizeReleaseVersion",
            "Finalizing release version...",
            "1 actionable task: 1 executed"
        )

        SCM_ACTIONS.execute(PROJECT_DIR, "status").run {
            assertThat(lines()).containsSubsequence(
                "On branch main",
                "nothing to commit, working tree clean"
            )
        }
        assertThat(SCM_ACTIONS.getCommits(PROJECT_DIR).first()).isEqualTo("Release version: 0.1.0")
        assertThat(SCM_ACTIONS.getLastTag(PROJECT_DIR)).isEqualTo("0.1.0")

        assertThat(GRADLE_PROJECT_ACTIONS.getVersion()).hasToString("0.1.0")
    }

    @Order(13)
    @Test
    fun test_releaseBuild() {
        // GIVEN
        // WHEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.RELEASE_BUILD_TASK_NAME)
            .build()

        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :jar",
            "> Task :build",
            "> Task :publish",
            "> Task :releaseBuild",
            "5 actionable tasks: 5 executed"
        )

        PROJECT_DIR.resolve("build/libs/").run {
            assertThat(resolve("$PROJECT_NAME-0.1.0.jar").exists()).isTrue
            assertThat(resolve("$PROJECT_NAME-0.0.1-SNAPSHOT.jar").exists()).isFalse
        }
    }

    @Order(14)
    @Test
    fun test_setSnapshotVersion() {
        // GIVEN
        // WHEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.SET_SNAPSHOT_VERSION_TASK_NAME)
            .build()

        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :setSnapshotVersion",
            "Setting SNAPSHOT version...",
            "1 actionable task: 1 executed"
        )

        SCM_ACTIONS.execute(PROJECT_DIR, "status").run {
            assertThat(lines()).containsSubsequence(
                "On branch dev",
                "Changes to be committed:",
                "\tmodified:   gradle.properties",
                "Changes not staged for commit:",
                "\tmodified:   gradle.properties"
            )
        }
        assertThat(SCM_ACTIONS.getCommits(PROJECT_DIR).first()).isEqualTo("feature: implement feature")
        assertThat(SCM_ACTIONS.getLastTag(PROJECT_DIR))
            .isNull()

        assertThat(GRADLE_PROJECT_ACTIONS.getVersion()).hasToString("0.1.1-SNAPSHOT")
    }

    @Order(15)
    @Test
    fun test_finalizeSnapshotVersion() {
        // GIVEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.FINALIZE_SNAPSHOT_VERSION_TASK_NAME)
            .build()

        // WHEN
        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :finalizeSnapshotVersion",
            "Finalizing SNAPSHOT version...",
            "1 actionable task: 1 executed"
        )

        SCM_ACTIONS.execute(PROJECT_DIR, "status").run {
            assertThat(lines()).containsSubsequence(
                "On branch dev",
                "nothing to commit, working tree clean"
            )
        }
        assertThat(SCM_ACTIONS.getCommits(PROJECT_DIR).first()).isEqualTo("New SNAPSHOT version: 0.1.1-SNAPSHOT")
        assertThat(SCM_ACTIONS.getLastTag(PROJECT_DIR)).isEqualTo("0.1.0")

        assertThat(GRADLE_PROJECT_ACTIONS.getVersion()).hasToString("0.1.1-SNAPSHOT")
    }

    @Order(16)
    @Test
    fun test_updateScm() {
        // GIVEN
        // WHEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.UPDATE_SCM_TASK_NAME)
            .build()

        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :updateScm",
            "1 actionable task: 1 executed"
        )

        listOf(PROJECT_DIR to ORIGIN_PROJECT_DIR)
            .forEach { it.verifyGitCommitsAndTags() }
    }

    @Order(20)
    @Test
    fun test_release() {
        // GIVEN
        PROJECT_DIR.copyIntoFromResources("src/main/java/org/eazyportal/plugin/release/test/dummy/service/DummyService.java")

        // WHEN
        SCM_ACTIONS.add(PROJECT_DIR, ".")
        SCM_ACTIONS.commit(PROJECT_DIR, "feature: implement service")

        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.RELEASE_TASK_NAME)
            .build()

        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :prepareRepositoryForRelease",
            "Preparing repository for release...",
            "> Task :setReleaseVersion",
            "Setting release version...",
            "> Task :finalizeReleaseVersion",
            "Finalizing release version...",
            "> Task :jar",
            "> Task :build",
            "> Task :publish",
            "> Task :releaseBuild",
            "> Task :setSnapshotVersion",
            "Setting SNAPSHOT version...",
            "> Task :finalizeSnapshotVersion",
            "Finalizing SNAPSHOT version...",
            "> Task :updateScm",
            "Updating scm...",
            "> Task :release",
            "11 actionable tasks: 11 executed"
        )

        assertThat(SCM_ACTIONS.getCommits(PROJECT_DIR).first())
            .isEqualTo("New SNAPSHOT version: 0.2.1-SNAPSHOT")

        assertThat(GRADLE_PROJECT_ACTIONS.getVersion()).hasToString("0.2.1-SNAPSHOT")

        assertThat(SCM_ACTIONS.getLastTag(PROJECT_DIR)).isEqualTo("0.2.0")

        listOf(PROJECT_DIR to ORIGIN_PROJECT_DIR)
            .forEach { it.verifyGitCommitsAndTags() }
    }

    @Order(21)
    @Test
    fun test_release_shouldFail_whenThereAreNoAcceptableCommitsAfterRelease() {
        // GIVEN
        // WHEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.RELEASE_TASK_NAME)
            .buildAndFail()

        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "Preparing repository for release...",
            "Setting release version...",
            "FAILURE: Build failed with an exception.",
            "* What went wrong:",
            "Execution failed for task ':setReleaseVersion'.",
            "> There are no acceptable commits."
        )
    }

    @Order(22)
    @Test
    fun test_release_shouldSucceed_withIsForceRelease() {
        // GIVEN
        // WHEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.RELEASE_TASK_NAME, "-DforceRelease")
            .build()

        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :prepareRepositoryForRelease",
            "Preparing repository for release...",
            "> Task :setReleaseVersion",
            "Setting release version...",
            "> Task :finalizeReleaseVersion",
            "Finalizing release version...",
            "> Task :jar",
            "> Task :build",
            "> Task :publish",
            "> Task :releaseBuild",
            "> Task :setSnapshotVersion",
            "Setting SNAPSHOT version...",
            "> Task :finalizeSnapshotVersion",
            "Finalizing SNAPSHOT version...",
            "> Task :updateScm",
            "Updating scm...",
            "> Task :release",
            "11 actionable tasks: 10 executed, 1 up-to-date"
        )

        assertThat(SCM_ACTIONS.getCommits(PROJECT_DIR).first())
            .isEqualTo("New SNAPSHOT version: 0.2.2-SNAPSHOT")

        assertThat(GRADLE_PROJECT_ACTIONS.getVersion())
            .hasToString("0.2.2-SNAPSHOT")

        assertThat(SCM_ACTIONS.getLastTag(PROJECT_DIR)).isEqualTo("0.2.1")

        listOf(PROJECT_DIR to ORIGIN_PROJECT_DIR)
            .forEach { it.verifyGitCommitsAndTags() }
    }

}
