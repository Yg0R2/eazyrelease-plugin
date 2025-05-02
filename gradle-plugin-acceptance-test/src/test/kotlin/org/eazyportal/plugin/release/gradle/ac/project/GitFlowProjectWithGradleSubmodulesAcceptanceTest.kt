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
import java.nio.file.Files

@TestMethodOrder(value = OrderAnnotation::class)
internal class GitFlowProjectWithGradleSubmodulesAcceptanceTest : BaseProjectAcceptanceTest() {

    companion object {
        private const val SUBMODULE_PROJECT_NAME = "submodule-project"

        private lateinit var ALL_PROJECT_DIRS: List<FileSystemProjectFile>
        private lateinit var ORIGIN_SUBMODULE_PROJECT_DIR: FileSystemProjectFile
        private lateinit var SUBMODULE_PROJECT_DIR: FileSystemProjectFile

        @BeforeAll
        @JvmStatic
        fun initialize() {
            ORIGIN_SUBMODULE_PROJECT_DIR = WORKING_DIR.resolve("origin/$SUBMODULE_PROJECT_NAME")
                .also { Files.createDirectories(it.toPath()) }
                .let { FileSystemProjectFile(it) }
                .also { SCM_ACTIONS.execute(it, "init", "--initial-branch=main") }

            SUBMODULE_PROJECT_DIR = PROJECT_DIR.resolve(SUBMODULE_PROJECT_NAME)

            ALL_PROJECT_DIRS = listOf(PROJECT_DIR, SUBMODULE_PROJECT_DIR)
        }
    }

    @Order(0)
    @Test
    fun test_initializeRepository() {
        // GIVEN
        listOf(ORIGIN_PROJECT_DIR, ORIGIN_SUBMODULE_PROJECT_DIR)
            .forEach { projectDir ->
                SCM_ACTIONS.execute(projectDir, "init", "--initial-branch=main")

                projectDir.initializeGradleProject(projectDir.getFile().name)

                // WHEN
                SCM_ACTIONS.add(projectDir, ".")
                SCM_ACTIONS.commit(projectDir, "initial commit")

                // THEN
                assertThat(SCM_ACTIONS.getCommits(projectDir)).containsExactly("initial commit")

                SCM_ACTIONS.execute(projectDir, "checkout", "-b", "dev")
            }

        val originProjectGitDirPath = ORIGIN_PROJECT_DIR.resolve(".git").getFile().path
        val originSubmoduleGitDirPath = ORIGIN_SUBMODULE_PROJECT_DIR.resolve(".git").getFile().path

        // Fixing Windows folder separator issue
        SCM_ACTIONS.execute(ORIGIN_PROJECT_DIR, "-c", "protocol.file.allow=always", "submodule", "add", originSubmoduleGitDirPath.replace("\\", "/"))

        SCM_ACTIONS.commit(ORIGIN_PROJECT_DIR, "add submodule")

        SCM_ACTIONS.execute(FileSystemProjectFile(WORKING_DIR), "-c", "protocol.file.allow=always", "clone", "--recurse-submodules", originProjectGitDirPath, PROJECT_NAME)

        // Checking out to a different branch will fix: "remote: error: refusing to update checked out branch: refs/heads/feature"
        listOf(ORIGIN_PROJECT_DIR, ORIGIN_SUBMODULE_PROJECT_DIR)
            .forEach { SCM_ACTIONS.execute(it, "checkout", "-b", "tmp") }
    }

    @Order(1)
    @Test
    fun test_release_shouldFail_whenThereAreNoAcceptableCommits() {
        // GIVEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.RELEASE_TASK_NAME)
            .buildAndFail()

        // WHEN
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
        PROJECT_DIR.copyIntoFromResources("src/main/java/org/eazyportal/plugin/release/test/dummy/DummyApplication.java", PROJECT_NAME)

        // WHEN
        SCM_ACTIONS.add(PROJECT_DIR, ".")
        SCM_ACTIONS.commit(PROJECT_DIR, "feature: implement feature")

        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.SET_RELEASE_VERSION_TASK_NAME)
            .build()

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
                "\tnew file:   .gitmodules",
                "\tnew file:   src/main/java/org/eazyportal/plugin/release/test/dummy/DummyApplication.java",
                "\tnew file:   submodule-project",
                "Changes not staged for commit:",
                "\tmodified:   gradle.properties",
                "\tmodified:   submodule-project (modified content)"
            )
        }
        SCM_ACTIONS.execute(SUBMODULE_PROJECT_DIR, "status").run {
            assertThat(lines()).containsSubsequence(
                "On branch main",
                "\tmodified:   gradle.properties"
            )
        }

        ALL_PROJECT_DIRS.forEach { projectDir ->
            assertThat(SCM_ACTIONS.getCommits(projectDir).first()).isEqualTo("initial commit")

            assertThat(GradleProjectActions(projectDir).getVersion()).hasToString("0.1.0")

            assertThat(SCM_ACTIONS.getLastTag(projectDir))
                .isNull()
        }
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

        ALL_PROJECT_DIRS.forEach { projectDir ->
            SCM_ACTIONS.execute(projectDir, "status").run {
                assertThat(lines()).containsSubsequence(
                    "On branch main",
                    "nothing to commit, working tree clean"
                )
            }

            assertThat(SCM_ACTIONS.getCommits(projectDir).first()).isEqualTo("Release version: 0.1.0")

            GradleProjectActions(projectDir).getVersion()
                .run { assertThat(this).hasToString("0.1.0") }

            assertThat(SCM_ACTIONS.getLastTag(projectDir)).isEqualTo("0.1.0")
        }
    }

    @Order(13)
    @Test
    fun test_releaseBuild() {
        // GIVEN
        // WHEN
        val buildResult = createGradleRunner(PROJECT_DIR, "releaseBuild")
            .build()

        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :jar",
            "> Task :build",
            "> Task :publish",
            "> Task :submodule-project:jar",
            "> Task :submodule-project:build",
            "> Task :submodule-project:publish",
            "> Task :releaseBuild",
            "9 actionable tasks: 9 executed"
        )

        PROJECT_DIR.resolve("build/libs/").run {
            assertThat(resolve("$PROJECT_NAME-0.1.0.jar").exists()).isTrue
            assertThat(resolve("$PROJECT_NAME-0.0.1-SNAPSHOT.jar").exists()).isFalse
        }

        SUBMODULE_PROJECT_DIR.resolve("build/libs/").run {
            assertThat(resolve("$SUBMODULE_PROJECT_NAME-0.1.0.jar").exists()).isTrue
            assertThat(resolve("$SUBMODULE_PROJECT_NAME-0.0.1-SNAPSHOT.jar").exists()).isFalse
        }
    }

    @Order(14)
    @Test
    fun test_setSnapshotVersion() {
        // GIVEN
        val buildResult = createGradleRunner(PROJECT_DIR, "setSnapshotVersion")
            .build()

        // WHEN
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
                "\tmodified:   submodule-project",
                "Changes not staged for commit:",
                "\tmodified:   gradle.properties",
                "\tmodified:   submodule-project (new commits, modified content)"
            )
        }
        assertThat(SCM_ACTIONS.getCommits(PROJECT_DIR).first()).isEqualTo("feature: implement feature")

        SCM_ACTIONS.execute(SUBMODULE_PROJECT_DIR, "status").run {
            assertThat(lines()).containsSubsequence(
                "On branch dev",
                "Changes to be committed:",
                "\tmodified:   gradle.properties",
                "Changes not staged for commit:",
                "\tmodified:   gradle.properties"
            )
        }
        assertThat(SCM_ACTIONS.getCommits(SUBMODULE_PROJECT_DIR).first()).isEqualTo("initial commit")

        ALL_PROJECT_DIRS.forEach { projectDir ->
            assertThat(GradleProjectActions(projectDir).getVersion()).hasToString("0.1.1-SNAPSHOT")

            assertThat(SCM_ACTIONS.getLastTag(projectDir))
                .isNull()
        }
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

        ALL_PROJECT_DIRS.forEach { projectDir ->
            SCM_ACTIONS.execute(projectDir, "status").run {
                assertThat(lines()).containsSubsequence(
                    "On branch dev",
                    "nothing to commit, working tree clean"
                )
            }

            assertThat(SCM_ACTIONS.getCommits(projectDir).first()).isEqualTo("New SNAPSHOT version: 0.1.1-SNAPSHOT")

            assertThat(GradleProjectActions(projectDir).getVersion()).hasToString("0.1.1-SNAPSHOT")

            assertThat(SCM_ACTIONS.getLastTag(projectDir)).isEqualTo("0.1.0")
        }
    }

    @Order(16)
    @Test
    fun test_updateScm() {
        // GIVEN
        val buildResult = createGradleRunner(PROJECT_DIR, "updateScm")
            .build()

        // WHEN
        // THEN
        assertThat(buildResult.output.lines()).containsSubsequence(
            "> Task :updateScm",
            "1 actionable task: 1 executed"
        )

        listOf(PROJECT_DIR to ORIGIN_PROJECT_DIR, SUBMODULE_PROJECT_DIR to ORIGIN_SUBMODULE_PROJECT_DIR)
            .forEach { it.verifyGitCommitsAndTags() }
    }

    @Order(20)
    @Test
    fun test_release() {
        // GIVEN
        SUBMODULE_PROJECT_DIR
            .copyIntoFromResources("src/main/java/org/eazyportal/plugin/release/test/dummy/service/DummyService.java", SUBMODULE_PROJECT_NAME)

        // WHEN
        SCM_ACTIONS.add(SUBMODULE_PROJECT_DIR, ".")
        SCM_ACTIONS.commit(SUBMODULE_PROJECT_DIR, "feature: implement service")

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
            "> Task :submodule-project:jar",
            "> Task :submodule-project:build",
            "> Task :submodule-project:publish",
            "> Task :releaseBuild",
            "> Task :setSnapshotVersion",
            "Setting SNAPSHOT version...",
            "> Task :finalizeSnapshotVersion",
            "Finalizing SNAPSHOT version...",
            "> Task :updateScm",
            "Updating scm...",
            "> Task :release",
            "16 actionable tasks: 15 executed, 1 up-to-date"
        )

        ALL_PROJECT_DIRS.forEach { projectDir ->
            assertThat(SCM_ACTIONS.getCommits(projectDir).first()).isEqualTo("New SNAPSHOT version: 0.2.1-SNAPSHOT")

            GradleProjectActions(projectDir).getVersion()
                .run { assertThat(this).hasToString("0.2.1-SNAPSHOT") }

            assertThat(SCM_ACTIONS.getLastTag(projectDir)).isEqualTo("0.2.0")
        }

        listOf(PROJECT_DIR to ORIGIN_PROJECT_DIR, SUBMODULE_PROJECT_DIR to ORIGIN_SUBMODULE_PROJECT_DIR)
            .forEach { it.verifyGitCommitsAndTags() }
    }

    @Order(21)
    @Test
    fun test_release_shouldFail_whenThereAreNoAcceptableCommitsAfterRelease() {
        // GIVEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.RELEASE_TASK_NAME)
            .buildAndFail()

        // WHEN
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
            "> Task :submodule-project:jar",
            "> Task :submodule-project:build",
            "> Task :submodule-project:publish",
            "> Task :releaseBuild",
            "> Task :setSnapshotVersion",
            "Setting SNAPSHOT version...",
            "> Task :finalizeSnapshotVersion",
            "Finalizing SNAPSHOT version...",
            "> Task :updateScm",
            "Updating scm...",
            "> Task :release",
            "16 actionable tasks: 14 executed, 2 up-to-date"
        )

        ALL_PROJECT_DIRS.forEach { projectDir ->
            assertThat(SCM_ACTIONS.getCommits(projectDir).first()).isEqualTo("New SNAPSHOT version: 0.2.2-SNAPSHOT")

            GradleProjectActions(projectDir).getVersion()
                .run { assertThat(this).hasToString("0.2.2-SNAPSHOT") }

            assertThat(SCM_ACTIONS.getLastTag(projectDir)).isEqualTo("0.2.1")
        }

        listOf(PROJECT_DIR to ORIGIN_PROJECT_DIR, SUBMODULE_PROJECT_DIR to ORIGIN_SUBMODULE_PROJECT_DIR)
            .forEach { it.verifyGitCommitsAndTags() }
    }

}
