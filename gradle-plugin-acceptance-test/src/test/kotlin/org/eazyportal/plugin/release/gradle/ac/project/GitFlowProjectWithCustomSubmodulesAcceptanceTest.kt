package org.eazyportal.plugin.release.gradle.ac.project

import org.assertj.core.api.Assertions.assertThat
import org.eazyportal.plugin.release.core.project.model.FileSystemProjectFile
import org.eazyportal.plugin.release.gradle.EazyReleasePlugin
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.nio.file.Files

@TestMethodOrder(value = OrderAnnotation::class)
internal class GitFlowProjectWithCustomSubmodulesAcceptanceTest : BaseProjectAcceptanceTest() {

    companion object {
        private const val SUBMODULE_PROJECT_NAME = "submodule-project"

        private val PROJECT_ACTIONS_FACTORY = StubProjectActionsFactory()

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
        ORIGIN_PROJECT_DIR.run {
            SCM_ACTIONS.execute(this, "init", "--initial-branch=main")

            initializeGradleProject(PROJECT_NAME)
        }

        ORIGIN_SUBMODULE_PROJECT_DIR.run {
            SCM_ACTIONS.execute(this, "init", "--initial-branch=main")

            copyIntoFromResources("version.json", SUBMODULE_PROJECT_NAME)
        }

        listOf(ORIGIN_PROJECT_DIR, ORIGIN_SUBMODULE_PROJECT_DIR)
            .forEach { projectDir ->
                SCM_ACTIONS.add(projectDir, ".")
                SCM_ACTIONS.commit(projectDir, "initial commit")

                // WHEN
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

        // Checking out to a different branch will fix: "remote: error: refusing to update checked out branch: refs/heads/dev"
        listOf(ORIGIN_PROJECT_DIR, ORIGIN_SUBMODULE_PROJECT_DIR).forEach { projectDir ->
            SCM_ACTIONS.execute(projectDir, "checkout", "-b", "tmp")
        }
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
                "\tmodified:   version.json"
            )
        }

        ALL_PROJECT_DIRS.forEach { projectDir ->
            assertThat(SCM_ACTIONS.getCommits(projectDir).first()).isEqualTo("initial commit")

            assertThat(PROJECT_ACTIONS_FACTORY.create(projectDir).getVersion()).hasToString("0.1.0")

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

            PROJECT_ACTIONS_FACTORY.create(projectDir)
                .getVersion()
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
            "> Task :submodule-project:build",
            "Hello from custom build task!",
            "> Task :submodule-project:publish",
            "Hello from custom publish task!",
            "> Task :releaseBuild",
            "7 actionable tasks: 7 executed"
        )

        PROJECT_DIR.resolve("build/libs/").run {
            assertThat(resolve("$PROJECT_NAME-0.1.0.jar").exists()).isTrue
            assertThat(resolve("$PROJECT_NAME-0.0.1-SNAPSHOT.jar").exists()).isFalse
        }

        SUBMODULE_PROJECT_DIR.resolve("build/libs/").run {
            assertThat(resolve("$SUBMODULE_PROJECT_NAME-0.1.0.jar").exists()).isFalse
            assertThat(resolve("$SUBMODULE_PROJECT_NAME-0.0.1-SNAPSHOT.jar").exists()).isFalse
        }
    }

    @Order(14)
    @Test
    fun test_setSnapshotVersion() {
        // GIVEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.SET_SNAPSHOT_VERSION_TASK_NAME)
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
                "\tmodified:   version.json",
                "Changes not staged for commit:",
                "\tmodified:   version.json"
            )
        }
        assertThat(SCM_ACTIONS.getCommits(SUBMODULE_PROJECT_DIR).first()).isEqualTo("initial commit")

        ALL_PROJECT_DIRS.forEach { projectDir ->
            PROJECT_ACTIONS_FACTORY.create(projectDir)
                .getVersion()
                .run { assertThat(this).hasToString("0.1.1-SNAPSHOT") }

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

            PROJECT_ACTIONS_FACTORY.create(projectDir)
                .getVersion()
                .run { assertThat(this).hasToString("0.1.1-SNAPSHOT") }

            assertThat(SCM_ACTIONS.getLastTag(projectDir)).isEqualTo("0.1.0")
        }
    }

    @Order(16)
    @Test
    fun test_updateScm() {
        // GIVEN
        val buildResult = createGradleRunner(PROJECT_DIR, EazyReleasePlugin.UPDATE_SCM_TASK_NAME)
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
        SUBMODULE_PROJECT_DIR.copyIntoFromResources("README.adoc", SUBMODULE_PROJECT_NAME)

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
            "> Task :submodule-project:build",
            "Hello from custom build task!",
            "> Task :submodule-project:publish",
            "Hello from custom publish task!",
            "> Task :releaseBuild",
            "> Task :setSnapshotVersion",
            "Setting SNAPSHOT version...",
            "> Task :finalizeSnapshotVersion",
            "Finalizing SNAPSHOT version...",
            "> Task :updateScm",
            "Updating scm...",
            "> Task :release",
            "13 actionable tasks: 12 executed, 1 up-to-date"
        )

        ALL_PROJECT_DIRS.forEach { projectDir ->
            assertThat(SCM_ACTIONS.getCommits(projectDir).first()).isEqualTo("New SNAPSHOT version: 0.2.1-SNAPSHOT")

            PROJECT_ACTIONS_FACTORY.create(projectDir)
                .getVersion()
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
            "> Task :submodule-project:build",
            "Hello from custom build task!",
            "> Task :submodule-project:publish",
            "Hello from custom publish task!",
            "> Task :releaseBuild",
            "> Task :setSnapshotVersion",
            "Setting SNAPSHOT version...",
            "> Task :finalizeSnapshotVersion",
            "Finalizing SNAPSHOT version...",
            "> Task :updateScm",
            "Updating scm...",
            "> Task :release",
            "13 actionable tasks: 12 executed, 1 up-to-date"
        )

        ALL_PROJECT_DIRS.forEach { projectDir ->
            assertThat(SCM_ACTIONS.getCommits(projectDir).first()).isEqualTo("New SNAPSHOT version: 0.2.2-SNAPSHOT")

            PROJECT_ACTIONS_FACTORY.create(projectDir)
                .getVersion()
                .run { assertThat(this).hasToString("0.2.2-SNAPSHOT") }

            assertThat(SCM_ACTIONS.getLastTag(projectDir)).isEqualTo("0.2.1")
        }

        listOf(PROJECT_DIR to ORIGIN_PROJECT_DIR, SUBMODULE_PROJECT_DIR to ORIGIN_SUBMODULE_PROJECT_DIR)
            .forEach { it.verifyGitCommitsAndTags() }
    }

}
