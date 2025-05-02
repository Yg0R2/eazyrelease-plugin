package org.eazyportal.plugin.release.core.ac.scm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eazyportal.plugin.release.core.ac.BaseAcceptanceTest
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
internal class GitActionsAcceptanceTest : BaseAcceptanceTest() {

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
        gitActions.execute(originProjectDir, "init", "--initial-branch=$BRANCH_MAIN")

        assertThatStatusContains(
            originProjectDir,
            "On branch $BRANCH_MAIN",
            "No commits yet",
            "nothing to commit"
        )

        assertThatThrownBy { underTest.getCommits(originProjectDir) }
            .isInstanceOf(ScmActionException::class.java)

        assertThatBranches(originProjectDir)
            .isEmpty()

        assertThatThrownBy { underTest.getTags(originProjectDir) }
            .isInstanceOf(ScmActionException::class.java)

        assertThat(underTest.getLastTag(originProjectDir))
            .isNull()
    }

    @Order(1)
    @Test
    fun test_initializeRepository_addContent() {
        originProjectDir.copyResource(".gitkeep", PROJECT_NAME)

        assertThatStatusContains(
            originProjectDir,
            "On branch $BRANCH_MAIN",
            "No commits yet",
            "Untracked files:",
            ".gitkeep"
        )

        underTest.add(originProjectDir, ".")

        assertThatStatusContains(
            originProjectDir,
            "On branch $BRANCH_MAIN",
            "No commits yet",
            "Changes to be committed:",
            "new file:   .gitkeep"
        )
    }

    @Order(2)
    @Test
    fun test_initializeRepository_initialCommit() {
        underTest.commit(originProjectDir, "initial commit")

        assertThatStatusContains(
            originProjectDir,
            "On branch $BRANCH_MAIN",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(originProjectDir))
            .containsExactly("initial commit")

        assertThatBranches(originProjectDir)
            .containsExactly("* $BRANCH_MAIN")
    }

    @Order(3)
    @Test
    fun test_initializeRepository_createFeatureBranch() {
        gitActions.execute(originProjectDir, "branch", BRANCH_FEATURE)

        assertThatBranches(originProjectDir)
            .containsExactly(
                BRANCH_FEATURE,
                "* $BRANCH_MAIN"
            )
    }

    @Order(4)
    @Test
    fun test_cloneRepository() {
        gitActions.execute(
            FileSystemProjectFile(workingDir),
            "-c",
            "protocol.file.allow=always",
            "clone",
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
            .containsExactly("initial commit")

        assertThatBranches(projectDir)
            .containsExactly(
                "* $BRANCH_MAIN",
                "remotes/$REMOTE/HEAD -> $REMOTE/$BRANCH_MAIN",
                "remotes/$REMOTE/$BRANCH_FEATURE",
                "remotes/$REMOTE/$BRANCH_MAIN"
            )

        // Checking out to a different branch for fixing:
        // "remote: error: refusing to update checked out branch: refs/heads/dev"
        gitActions.execute(originProjectDir, "checkout", "-b", "tmp")

        assertThatBranches(originProjectDir)
            .containsExactly(
                BRANCH_FEATURE,
                BRANCH_MAIN,
                "* tmp"
            )
    }

    @Order(10)
    @Test
    fun test_prepareRepositoryForRelease_fetchRemote() {
        underTest.fetch(projectDir, REMOTE)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
            "nothing to commit, working tree clean"
        )

        assertThatBranches(projectDir)
            .containsExactly(
                "* $BRANCH_MAIN",
                "remotes/$REMOTE/HEAD -> $REMOTE/$BRANCH_MAIN",
                "remotes/$REMOTE/$BRANCH_FEATURE",
                "remotes/$REMOTE/$BRANCH_MAIN",
                "remotes/$REMOTE/tmp"
            )
    }

    @Order(11)
    @Test
    fun test_prepareRepositoryForRelease_checkoutToFeatureBranch() {
        underTest.checkout(projectDir, BRANCH_FEATURE)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
            "nothing to commit, working tree clean"
        )

        assertThat(underTest.getCommits(projectDir))
            .containsExactly("initial commit")

        assertThatBranches(projectDir)
            .containsExactly(
                "* $BRANCH_FEATURE",
                BRANCH_MAIN,
                "remotes/$REMOTE/HEAD -> $REMOTE/$BRANCH_MAIN",
                "remotes/$REMOTE/$BRANCH_FEATURE",
                "remotes/$REMOTE/$BRANCH_MAIN",
                "remotes/$REMOTE/tmp"
            )
    }

    @Order(20)
    @Test
    fun test_setReleaseVersion_addNewFile() {
        projectDir.copyResource("README.adoc", PROJECT_NAME)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
            "Untracked files:",
            "README.adoc",
            "nothing added to commit but untracked files present"
        )

        underTest.add(projectDir, "README.adoc")

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is up to date with '$REMOTE/$BRANCH_FEATURE'.",
            "Changes to be committed:",
            "new file:   README.adoc"
        )
    }

    @Order(21)
    @Test
    fun test_setReleaseVersion_commit() {
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
                "initial commit"
            )
    }

    @Order(22)
    @Test
    fun test_setReleaseVersion_checkoutToMainBranch() {
        underTest.checkout(projectDir, BRANCH_MAIN)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
            "nothing to commit, working tree clean"
        )
    }

    @Order(23)
    @Test
    fun test_setReleaseVersion_mergeNoCommit() {
        underTest.mergeNoCommit(projectDir, BRANCH_FEATURE)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_MAIN",
            "Your branch is up to date with '$REMOTE/$BRANCH_MAIN'.",
            "All conflicts fixed but you are still merging.",
            "Changes to be committed:",
            "new file:   README.adoc"
        )

        assertThat(underTest.getCommits(projectDir))
            .containsExactly("initial commit")
    }

    @Order(30)
    @Test
    fun test_finalizeReleaseVersion_commit() {
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
                "initial commit"
            )
    }

    @Order(31)
    @Test
    fun test_finalizeReleaseVersion_tag() {
        assertThat(underTest.getTags(projectDir))
            .isEmpty()

        assertThat(underTest.getLastTag(projectDir))
            .isNull()

        underTest.tag(projectDir, RELEASE_001)

        assertThat(underTest.getTags(projectDir))
            .containsExactly(RELEASE_001.toString())

        assertThat(underTest.getLastTag(projectDir))
            .isEqualTo(RELEASE_001.toString())
    }

    @Order(40)
    @Test
    fun test_setSnapshotVersion_checkout() {
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
                "initial commit"
            )
    }

    @Order(41)
    @Test
    fun test_setSnapshotVersion_mergeNoCommit() {
        underTest.mergeNoCommit(projectDir, BRANCH_MAIN)

        assertThatStatusContains(
            projectDir,
            "On branch $BRANCH_FEATURE",
            "Your branch is ahead of '$REMOTE/$BRANCH_FEATURE' by 1 commit.",
            "All conflicts fixed but you are still merging."
        )

        assertThat(underTest.getCommits(projectDir))
            .containsExactly(
                "add README.adoc",
                "initial commit"
            )
    }

    @Order(50)
    @Test
    fun test_finalizeSnapshotVersion() {
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
                "initial commit"
            )
    }

    @Order(60)
    @Test
    fun test_updateScm() {
        assertThat(underTest.getCommits(originProjectDir, toRef = BRANCH_MAIN))
            .containsExactly("initial commit")

        assertThat(underTest.getCommits(originProjectDir, toRef = BRANCH_FEATURE))
            .containsExactly("initial commit")

        assertThat(underTest.getTags(originProjectDir))
            .isEmpty()

        underTest.push(projectDir, REMOTE, BRANCH_MAIN, BRANCH_FEATURE)

        assertThat(underTest.getCommits(originProjectDir, toRef = BRANCH_MAIN))
            .containsExactlyInAnyOrder(
                "release commit",
                "add README.adoc",
                "initial commit"
            )

        assertThat(underTest.getCommits(originProjectDir, toRef = BRANCH_FEATURE))
            .containsExactlyInAnyOrder(
                "snapshot commit",
                "release commit",
                "add README.adoc",
                "initial commit"
            )

        assertThat(underTest.getTags(originProjectDir))
            .containsExactly(RELEASE_001.toString())
    }

}
