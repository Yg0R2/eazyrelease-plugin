package org.eazyportal.plugin.release.core.action

import org.eazyportal.plugin.release.core.TestFixtures.ACTION_CONTEXT
import org.eazyportal.plugin.release.core.action.AbstractReleaseAction.Companion.SNAPSHOT_VERSION_COMMIT_PREFIX
import org.eazyportal.plugin.release.core.action.model.ActionContext
import org.eazyportal.plugin.release.core.project.ProjectActions
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptorMockBuilder
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.eazyportal.plugin.release.core.scm.ScmFixtures.COMMITS
import org.eazyportal.plugin.release.core.version.model.VersionFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EmptySource
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File

class FinalizeSnapshotVersionActionTest : ReleaseActionBaseTest() {

    @Mock
    private lateinit var scmActions: ScmActions<File>

    private lateinit var underTest: FinalizeSnapshotVersionAction<File>

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun test_execute() {
        // GIVEN
        val projectActions: ProjectActions = mock()

        val projectDescriptor: ProjectDescriptor<File> =
            ProjectDescriptorMockBuilder(projectActions, workingDir).build()

        underTest = createFinalizeSnapshotVersionAction(
            projectDescriptor = projectDescriptor
        )

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.SNAPSHOT_002)
        projectDescriptor.subProjects.forEach {
            whenever(scmActions.getLastTag(it.dir)).thenReturn(VersionFixtures.SNAPSHOT_002.toString())
            whenever(scmActions.getCommits(it.dir, VersionFixtures.SNAPSHOT_002.toString()))
                .thenReturn(COMMITS)
        }
        whenever(projectActions.scmFilesToCommit()).thenReturn(arrayOf(FILE_TO_COMMIT))

        // THEN
        underTest.execute()

        verify(projectActions).getVersion()
        verify(projectActions, times(projectDescriptor.allProjects.size)).scmFilesToCommit()
        projectDescriptor.subProjects.forEach {
            verify(scmActions).getLastTag(it.dir)
            verify(scmActions).getCommits(it.dir, VersionFixtures.SNAPSHOT_002.toString())
        }
        projectDescriptor.allProjects.forEach {
            verify(scmActions).add(it.dir, FILE_TO_COMMIT)
            verify(scmActions).commit(it.dir, "New SNAPSHOT version: ${VersionFixtures.SNAPSHOT_002}")
        }
        verifyNoMoreInteractions(projectActions, scmActions)
    }

    @CsvSource(SNAPSHOT_VERSION_COMMIT_PREFIX)
    @EmptySource
    @ParameterizedTest
    fun test_execute_shouldNotFinalizeSnapshotVersionForSubproject_whenThereAreNoSubprojectChanges(
        subprojectCommits: String
    ) {
        // GIVEN
        val projectActions: ProjectActions = mock()

        val projectDescriptor: ProjectDescriptor<File> =
            ProjectDescriptorMockBuilder(projectActions, workingDir).build()

        underTest = createFinalizeSnapshotVersionAction(
            projectDescriptor = projectDescriptor
        )

        val normalizedCommits = subprojectCommits.split(",")
            .filterNot { it.isBlank() }

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.SNAPSHOT_002)
        projectDescriptor.subProjects.forEach {
            whenever(scmActions.getLastTag(it.dir)).thenReturn(VersionFixtures.SNAPSHOT_002.toString())
            whenever(scmActions.getCommits(it.dir, VersionFixtures.SNAPSHOT_002.toString()))
                .thenReturn(normalizedCommits)
        }
        whenever(projectActions.scmFilesToCommit()).thenReturn(arrayOf(FILE_TO_COMMIT))

        // THEN
        underTest.execute()

        verify(projectActions).getVersion()
        verify(projectActions).scmFilesToCommit()
        projectDescriptor.subProjects.forEach {
            verify(scmActions).getLastTag(it.dir)
            verify(scmActions).getCommits(it.dir, VersionFixtures.SNAPSHOT_002.toString())
        }
        verify(scmActions).add(projectDescriptor.rootProject.dir, FILE_TO_COMMIT)
        verify(scmActions).commit(
            projectDescriptor.rootProject.dir,
            "New SNAPSHOT version: ${VersionFixtures.SNAPSHOT_002}"
        )
        verifyNoMoreInteractions(projectActions, scmActions)
    }

    @Test
    fun test_execute_shouldFinalizeSnapshotVersion_whenIsForceReleaseSet() {
        // GIVEN
        val projectActions: ProjectActions = mock()

        val projectDescriptor: ProjectDescriptor<File> =
            ProjectDescriptorMockBuilder(projectActions, workingDir).build()

        underTest = createFinalizeSnapshotVersionAction(
            actionContext = ACTION_CONTEXT.copy(
                isForceRelease = true,
            ),
            projectDescriptor = projectDescriptor
        )

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.SNAPSHOT_002)
        whenever(projectActions.scmFilesToCommit()).thenReturn(arrayOf(FILE_TO_COMMIT))

        // THEN
        underTest.execute()

        verify(projectActions).getVersion()
        verify(projectActions, times(projectDescriptor.allProjects.size)).scmFilesToCommit()
        projectDescriptor.allProjects.forEach {
            verify(scmActions).add(it.dir, FILE_TO_COMMIT)
            verify(scmActions).commit(it.dir, "New SNAPSHOT version: ${VersionFixtures.SNAPSHOT_002}")
        }
        verifyNoMoreInteractions(projectActions, scmActions)
    }

    private fun createFinalizeSnapshotVersionAction(
        actionContext: ActionContext = ACTION_CONTEXT,
        projectDescriptor: ProjectDescriptor<File>,
    ): FinalizeSnapshotVersionAction<File> =
        FinalizeSnapshotVersionAction(
            actionContext,
            projectDescriptor,
            scmActions
        )

}
