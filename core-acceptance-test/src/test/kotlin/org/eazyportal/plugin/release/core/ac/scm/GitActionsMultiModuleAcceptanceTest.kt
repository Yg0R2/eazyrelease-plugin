package org.eazyportal.plugin.release.core.ac.scm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eazyportal.plugin.release.core.ac.BaseMultiModuleAcceptanceTest
import org.eazyportal.plugin.release.core.ac.scm.GitRepositoryUtils.assertThatBranches
import org.eazyportal.plugin.release.core.ac.scm.GitRepositoryUtils.assertThatStatusContains
import org.eazyportal.plugin.release.core.executor.CliCommandExecutor
import org.eazyportal.plugin.release.core.project.model.FileSystemProjectFile
import org.eazyportal.plugin.release.core.scm.GitActions
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.eazyportal.plugin.release.core.scm.exception.ScmActionException
import org.eazyportal.plugin.release.core.version.model.VersionFixtures.Companion.RELEASE_001
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

@TestMethodOrder(value = MethodOrderer.OrderAnnotation::class)
internal class GitActionsMultiModuleAcceptanceTest : BaseMultiModuleAcceptanceTest() {

    // It is used for not implemented command execution
    private val gitActions = GitActions(CliCommandExecutor())

    private lateinit var underTest: ScmActions<File>

    @BeforeAll
    fun initialize() {
        underTest = GitActions(CliCommandExecutor())
    }

    @Order(0)
    @Test
    fun test_initializeRepository() {
        allOriginProject.values.forEach {
            gitActions.execute(it, "init", "--initial-branch=$BRANCH_MAIN")

            assertThatStatusContains(
                it,
                "On branch $BRANCH_MAIN",
                "No commits yet",
                "nothing to commit"
            )

            assertThatThrownBy { underTest.getCommits(it) }
                .isInstanceOf(ScmActionException::class.java)

            assertThatBranches(it)
                .isEmpty()

            assertThatThrownBy { underTest.getTags(it) }
                .isInstanceOf(ScmActionException::class.java)

            assertThat(underTest.getLastTag(it))
                .isNull()
        }
    }

    @Order(1)
    @Test
    fun test_initializeRepository_addContent() {
        allOriginProject.forEach { (projectName, projectDir) ->
            projectDir.copyResource(".gitkeep", projectName)

            assertThatStatusContains(
                projectDir,
                "On branch $BRANCH_MAIN",
                "No commits yet",
                "Untracked files:",
                ".gitkeep"
            )

            underTest.add(projectDir, ".")

            assertThatStatusContains(
                projectDir,
                "On branch $BRANCH_MAIN",
                "No commits yet",
                "Changes to be committed:",
                "new file:   .gitkeep"
            )
        }
    }

    @Order(2)
    @Test
    fun test_initializeRepository_initialCommit() {
        allOriginProject.values.forEach {
            underTest.commit(it, "initial commit")

            assertThatStatusContains(
                it,
                "On branch $BRANCH_MAIN",
                "nothing to commit, working tree clean"
            )

            assertThat(underTest.getCommits(it))
                .containsExactly("initial commit")

            assertThatBranches(it)
                .containsExactly("* $BRANCH_MAIN")
        }
    }

    @Order(3)
    @Test
    fun test_initializeRepository_addSubmodule() {
        gitActions.execute(
            originProjectDir,
            "-c",
            "protocol.file.allow=always",
            "submodule",
            "add",
            originSubProjectDir.resolve(".git").getFile().path
        )

        assertThatStatusContains(
            originProjectDir,
            "On branch $BRANCH_MAIN",
            "Changes to be committed:",
            "new file:   .gitmodules",
            "new file:   dummy-sub-project"
        )

        underTest.commit(originProjectDir, "add submodule")

        assertThatStatusContains(
            originProjectDir,
            "On branch main",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(originProjectDir))
            .containsExactly(
                "add submodule",
                "initial commit"
            )
    }

    @Order(4)
    @Test
    fun test_initializeRepository_createFeatureBranch() {
        allOriginProject.values.forEach {
            gitActions.execute(it, "branch", BRANCH_FEATURE)

            assertThatBranches(it)
                .containsExactly(
                    BRANCH_FEATURE,
                    "* $BRANCH_MAIN"
                )
        }
    }

    @Order(5)
    @Test
    fun test_cloneRepository() {
        gitActions.execute(
            FileSystemProjectFile(workingDir),
            "-c",
            "protocol.file.allow=always",
            "clone",
            "--recurse-submodules",
            originProjectDir.resolve(".git").getFile().path,
            PROJECT_NAME
        )

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(projectDir))
            .containsExactly(
                "add submodule",
                "initial commit"
            )

        assertThatBranches(projectDir)
            .containsExactly(
                "* $BRANCH_MAIN",
                "remotes/$REMOTE/HEAD -> $REMOTE/$BRANCH_MAIN",
                "remotes/$REMOTE/$BRANCH_FEATURE",
                "remotes/$REMOTE/$BRANCH_MAIN"
            )

        assertThatStatusContains(
            subProjectDir,
            "HEAD detached at",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(subProjectDir))
            .containsExactly("initial commit")

        val sortHash = gitActions.execute(subProjectDir, "rev-parse", "--short", "HEAD")
        assertThatBranches(subProjectDir)
            .containsExactly(
                "* (HEAD detached at $sortHash)",
                BRANCH_MAIN,
                "remotes/$REMOTE/HEAD -> $REMOTE/$BRANCH_MAIN",
                "remotes/$REMOTE/$BRANCH_FEATURE",
                "remotes/$REMOTE/$BRANCH_MAIN"
            )

        // cloned submodule remains in detached head
        underTest.checkout(subProjectDir, BRANCH_MAIN)

        assertThatBranches(subProjectDir)
            .containsExactly(
                "* $BRANCH_MAIN",
                "remotes/$REMOTE/HEAD -> $REMOTE/$BRANCH_MAIN",
                "remotes/$REMOTE/$BRANCH_FEATURE",
                "remotes/$REMOTE/$BRANCH_MAIN"
            )

        // Checking out to a different branch for fixing:
        // "remote: error: refusing to update checked out branch: refs/heads/dev"
        allOriginProject.values.forEach {
            gitActions.execute(it, "checkout", "-b", "tmp")

            assertThatBranches(it)
                .containsExactly(
                    BRANCH_FEATURE,
                    BRANCH_MAIN,
                    "* tmp"
                )
        }
    }

    @Order(10)
    @Test
    fun test_prepareRepositoryForRelease() {
        underTest.fetch(projectDir, REMOTE)

        allProject.values.forEach {
            assertThatStatusContains(
                it,
                "On branch $BRANCH_MAIN",
                "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
                "nothing to commit, working tree clean"
            )

            assertThatBranches(it)
                .containsExactly(
                    "* $BRANCH_MAIN",
                    "remotes/$REMOTE/HEAD -> origin/$BRANCH_MAIN",
                    "remotes/$REMOTE/$BRANCH_FEATURE",
                    "remotes/$REMOTE/$BRANCH_MAIN",
                    "remotes/$REMOTE/tmp"
                )
        }
    }

    @Order(11)
    @Test
    fun test_prepareRepositoryForRelease_checkoutToFeatureBranch() {
        allProject.values.forEach {
            underTest.checkout(it, BRANCH_FEATURE)

            assertThatStatusContains(
                it,
                "On branch $BRANCH_FEATURE",
                "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
                "nothing to commit, working tree clean"
            )

            assertThatBranches(it)
                .containsExactly(
                    "* $BRANCH_FEATURE",
                    BRANCH_MAIN,
                    "remotes/$REMOTE/HEAD -> $REMOTE/$BRANCH_MAIN",
                    "remotes/$REMOTE/$BRANCH_FEATURE",
                    "remotes/$REMOTE/$BRANCH_MAIN",
                    "remotes/$REMOTE/tmp"
                )
        }
    }

    @Order(20)
    @Test
    fun test_setReleaseVersion_addNewFile_subProject() {
        subProjectDir.copyResource("README.adoc", SUB_PROJECT_NAME)

        assertThatStatusContains(
            subProjectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
            "Untracked files:",
            "README.adoc",
            "nothing added to commit but untracked files present"
        )

        underTest.add(subProjectDir, "README.adoc")

        assertThatStatusContains(
            subProjectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
            "Changes to be committed:",
            "new file:   README.adoc"
        )
    }

    @Order(21)
    @Test
    fun test_setReleaseVersion_addNewFile_project() {
        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
            "Changes not staged for commit:",
            "modified:   $SUB_PROJECT_NAME (modified content)",
            "no changes added to commit"
        )

        projectDir.copyResource("README.adoc", PROJECT_NAME)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
            "Changes not staged for commit:",
            "modified:   $SUB_PROJECT_NAME (modified content)",
            "Untracked files:",
            "README.adoc",
            "no changes added to commit"
        )

        underTest.add(projectDir, "README.adoc", SUB_PROJECT_NAME)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
            "Changes to be committed:",
            "new file:   README.adoc",
            "Changes not staged for commit:",
            "modified:   $SUB_PROJECT_NAME (modified content)"
        )
    }

    @Order(22)
    @Test
    fun test_setReleaseVersion_commit_subProject() {
        underTest.commit(subProjectDir, "add README.adoc")

        assertThatStatusContains(
            subProjectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 1 commit.",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(subProjectDir))
            .containsExactly(
                "add README.adoc",
                "initial commit"
            )
    }

    @Order(23)
    @Test
    fun test_setReleaseVersion_addSubProjectChanges() {
        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
            "Changes to be committed:",
            "new file:   README.adoc",
            "Changes not staged for commit:",
            "modified:   $SUB_PROJECT_NAME (new commits)"
        )

        underTest.add(projectDir, SUB_PROJECT_NAME)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
            "Changes to be committed:",
            "new file:   README.adoc",
            "modified:   $SUB_PROJECT_NAME"
        )
    }

    @Order(24)
    @Test
    fun test_setReleaseVersion_commit_project() {
        underTest.commit(projectDir, "add README.adoc")

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 1 commit.",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(projectDir))
            .containsExactly(
                "add README.adoc",
                "add submodule",
                "initial commit"
            )
    }


    @Order(25)
    @Test
    fun test_setReleaseVersion_checkoutToMainBranch_subProject() {
        underTest.checkout(subProjectDir, BRANCH_MAIN)

        assertThatStatusContains(
            subProjectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
            "nothing to commit, working tree clean"
        )
    }

    @Order(25)
    @Test
    fun test_setReleaseVersion_mergeNoCommit_subProject() {
        underTest.mergeNoCommit(subProjectDir, BRANCH_FEATURE)

        assertThatStatusContains(
            subProjectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
            "All conflicts fixed but you are still merging.",
            "Changes to be committed:",
            "new file:   README.adoc"
        )

        assertThat(underTest.getCommits(subProjectDir))
            .containsExactly("initial commit")
    }

    @Order(26)
    @Test
    fun test_setReleaseVersion_checkoutToMainBranch_project() {
        underTest.checkout(projectDir, BRANCH_MAIN)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
            "Changes not staged for commit:",
            "modified:   $SUB_PROJECT_NAME (modified content)",
            "no changes added to commit"
        )
    }

    @Order(27)
    @Test
    fun test_setReleaseVersion_mergeNoCommit_project() {
        underTest.mergeNoCommit(projectDir, BRANCH_FEATURE)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
            "All conflicts fixed but you are still merging.",
            "Changes to be committed:",
            "new file:   README.adoc",
            "modified:   $SUB_PROJECT_NAME"
        )

        assertThat(underTest.getCommits(projectDir))
            .containsExactly(
                "add submodule",
                "initial commit"
            )
    }

    @Order(30)
    @Test
    fun test_finalizeReleaseVersion_subProject() {
        underTest.commit(subProjectDir, "release commit")

        assertThatStatusContains(
            subProjectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is ahead of '$REMOTE/$BRANCH_MAIN' by 2 commits.",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(subProjectDir))
            .containsExactlyInAnyOrder(
                "release commit",
                "add README.adoc",
                "initial commit"
            )
    }

    @Order(31)
    @Test
    fun test_finalizeReleaseVersion_addSubProjectChanges() {
        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
            "All conflicts fixed but you are still merging.",
            "Changes to be committed:",
            "new file:   README.adoc",
            "Changes not staged for commit:",
            "modified:   $SUB_PROJECT_NAME (new commits)"
        )

        underTest.add(projectDir, SUB_PROJECT_NAME)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
            "All conflicts fixed but you are still merging.",
            "Changes to be committed:",
            "new file:   README.adoc",
            "modified:   $SUB_PROJECT_NAME"
        )
    }

    @Order(32)
    @Test
    fun test_finalizeReleaseVersion_project() {
        underTest.commit(projectDir, "release commit")

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is ahead of '$REMOTE/$BRANCH_MAIN' by 2 commits.",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(projectDir))
            .containsExactlyInAnyOrder(
                "release commit",
                "add README.adoc",
                "add submodule",
                "initial commit"
            )
    }

    @Order(33)
    @Test
    fun test_finalizeReleaseVersion_tag() {
        allProject.values.forEach {
            assertThat(underTest.getTags(it))
                .isEmpty()

            assertThat(underTest.getLastTag(it))
                .isNull()

            underTest.tag(it, RELEASE_001)

            assertThat(underTest.getTags(it))
                .containsExactly(RELEASE_001.toString())

            assertThat(underTest.getLastTag(it))
                .isEqualTo(RELEASE_001.toString())
        }
    }

    @Order(40)
    @Test
    fun test_setSnapshotVersion_checkout_subProject() {
        underTest.checkout(subProjectDir, BRANCH_FEATURE)

        assertThatStatusContains(
            subProjectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 1 commit.",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(subProjectDir))
            .containsExactly(
                "add README.adoc",
                "initial commit"
            )

    }

    @Order(41)
    @Test
    fun test_setSnapshotVersion_checkout_project() {
        underTest.checkout(projectDir, BRANCH_FEATURE)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 1 commit.",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(projectDir))
            .containsExactly(
                "add README.adoc",
                "add submodule",
                "initial commit"
            )
    }

    @Order(42)
    @Test
    fun test_setSnapshotVersion_mergeNoCommit_subProject() {
        underTest.mergeNoCommit(subProjectDir, BRANCH_MAIN)

        assertThatStatusContains(
            subProjectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 1 commit.",
            "All conflicts fixed but you are still merging."
        )

        assertThat(underTest.getCommits(subProjectDir))
            .containsExactly(
                "add README.adoc",
                "initial commit"
            )
    }

    @Order(43)
    @Test
    fun test_setSnapshotVersion_mergeNoCommit_project() {
        underTest.mergeNoCommit(projectDir, BRANCH_MAIN)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 1 commit.",
            "All conflicts fixed but you are still merging.",
            "Changes to be committed:",
            "modified:   $SUB_PROJECT_NAME",
            "Changes not staged for commit:",
            "modified:   $SUB_PROJECT_NAME (new commits)"
        )

        assertThat(underTest.getCommits(projectDir))
            .containsExactly(
                "add README.adoc",
                "add submodule",
                "initial commit"
            )
    }

    @Order(50)
    @Test
    fun test_finalizeSnapshotVersion_subProject() {
        underTest.commit(subProjectDir, "snapshot commit")

        assertThatStatusContains(
            subProjectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 3 commits.",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(subProjectDir))
            .containsExactlyInAnyOrder(
                "snapshot commit",
                "release commit",
                "add README.adoc",
                "initial commit"
            )
    }

    @Order(51)
    @Test
    fun test_finalizeSnapshotVersion_addSubProjectChanges() {
        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 1 commit.",
            "All conflicts fixed but you are still merging.",
            "Changes to be committed:",
            "modified:   $SUB_PROJECT_NAME",
            "Changes not staged for commit:",
            "modified:   $SUB_PROJECT_NAME (new commits)"
        )

        underTest.add(projectDir, SUB_PROJECT_NAME)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 1 commit.",
            "All conflicts fixed but you are still merging.",
            "Changes to be committed:",
            "modified:   $SUB_PROJECT_NAME"
        )
    }

    @Order(52)
    @Test
    fun test_finalizeSnapshotVersion_project() {
        underTest.commit(projectDir, "snapshot commit")

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 3 commits.",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(projectDir))
            .containsExactlyInAnyOrder(
                "snapshot commit",
                "release commit",
                "add README.adoc",
                "add submodule",
                "initial commit"
            )
    }

    @Order(60)
    @Test
    fun test_updateScm() {
        assertThat(underTest.getCommits(originSubProjectDir, toRef = BRANCH_MAIN))
            .containsExactly("initial commit")

        assertThat(underTest.getCommits(originSubProjectDir, toRef = BRANCH_FEATURE))
            .containsExactly("initial commit")

        assertThat(underTest.getTags(originSubProjectDir))
            .isEmpty()

        assertThat(underTest.getCommits(originProjectDir, toRef = BRANCH_MAIN))
            .containsExactly(
                "add submodule",
                "initial commit"
            )

        assertThat(underTest.getCommits(originProjectDir, toRef = BRANCH_FEATURE))
            .containsExactly(
                "add submodule",
                "initial commit"
            )

        assertThat(underTest.getTags(originProjectDir))
            .isEmpty()

        underTest.push(projectDir, REMOTE, BRANCH_MAIN, BRANCH_FEATURE)

        assertThat(underTest.getCommits(subProjectDir, toRef = BRANCH_MAIN))
            .containsExactlyInAnyOrder(
                "release commit",
                "add README.adoc",
                "initial commit"
            )

        assertThat(underTest.getCommits(subProjectDir, toRef = BRANCH_FEATURE))
            .containsExactlyInAnyOrder(
                "snapshot commit",
                "release commit",
                "add README.adoc",
                "initial commit"
            )

        assertThat(underTest.getCommits(originProjectDir, toRef = BRANCH_MAIN))
            .containsExactlyInAnyOrder(
                "release commit",
                "add README.adoc",
                "add submodule",
                "initial commit"
            )

        assertThat(underTest.getCommits(originProjectDir, toRef = BRANCH_FEATURE))
            .containsExactlyInAnyOrder(
                "snapshot commit",
                "release commit",
                "add README.adoc",
                "add submodule",
                "initial commit"
            )

        assertThat(underTest.getTags(originProjectDir))
            .containsExactly(RELEASE_001.toString())
    }

}
