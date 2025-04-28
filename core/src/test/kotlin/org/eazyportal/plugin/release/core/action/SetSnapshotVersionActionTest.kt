package org.eazyportal.plugin.release.core.action

import org.eazyportal.plugin.release.core.TestFixtures.ACTION_CONTEXT
import org.eazyportal.plugin.release.core.action.model.ActionContext
import org.eazyportal.plugin.release.core.project.ProjectActions
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptorMockBuilder
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.eazyportal.plugin.release.core.scm.ScmFixtures.COMMITS
import org.eazyportal.plugin.release.core.scm.model.ScmConfig
import org.eazyportal.plugin.release.core.version.SnapshotVersionProvider
import org.eazyportal.plugin.release.core.version.model.VersionFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File

class SetSnapshotVersionActionTest : ReleaseActionBaseTest() {

    @Mock
    private lateinit var scmActions: ScmActions<File>

    @Mock
    private lateinit var snapshotVersionProvider: SnapshotVersionProvider

    private lateinit var underTest: SetSnapshotVersionAction<File>

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun test_execute_withGitFlow() {
        // GIVEN
        val projectActions: ProjectActions = mock()
        val projectDescriptor: ProjectDescriptor<File> =
            ProjectDescriptorMockBuilder(projectActions, workingDir).build()

        underTest = createSetSnapshotVersionAction(
            projectDescriptor = projectDescriptor,
            scmConfig = ScmConfig.GIT_FLOW
        )

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.RELEASE_001)
        whenever(snapshotVersionProvider.provide(VersionFixtures.RELEASE_001)).thenReturn(VersionFixtures.SNAPSHOT_002)
        projectDescriptor.subProjects.forEach {
            whenever(scmActions.getLastTag(it.dir)).thenReturn(null)
            whenever(scmActions.getCommits(it.dir, null)).thenReturn(COMMITS)
        }

        // THEN
        underTest.execute()

        verify(projectActions).getVersion()
        verify(snapshotVersionProvider).provide(VersionFixtures.RELEASE_001)
        projectDescriptor.subProjects.forEach {
            verify(scmActions).getLastTag(it.dir)
            verify(scmActions).getCommits(it.dir, null)
        }
        projectDescriptor.allProjects.forEach {
            verify(scmActions).checkout(it.dir, ScmConfig.GIT_FLOW.featureBranch)
            verify(scmActions).mergeNoCommit(it.dir, ScmConfig.GIT_FLOW.releaseBranch)
        }
        verify(projectActions, times(projectDescriptor.allProjects.size)).setVersion(VersionFixtures.SNAPSHOT_002)

        verifyNoMoreInteractions(projectActions, scmActions, snapshotVersionProvider)
    }

    @Test
    fun test_execute_withGitFlow_whenIsForceReleaseSet() {
        // GIVEN
        val projectActions: ProjectActions = mock()
        val projectDescriptor: ProjectDescriptor<File> =
            ProjectDescriptorMockBuilder(projectActions, workingDir).build()

        underTest = createSetSnapshotVersionAction(
            actionContext = ACTION_CONTEXT.copy(
                isForceRelease = true,
            ),
            projectDescriptor = projectDescriptor,
            scmConfig = ScmConfig.GIT_FLOW
        )

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.RELEASE_001)
        whenever(snapshotVersionProvider.provide(VersionFixtures.RELEASE_001)).thenReturn(VersionFixtures.SNAPSHOT_002)

        // THEN
        underTest.execute()

        verify(projectActions).getVersion()
        verify(snapshotVersionProvider).provide(VersionFixtures.RELEASE_001)

        projectDescriptor.allProjects.forEach {
            verify(scmActions).checkout(it.dir, ScmConfig.GIT_FLOW.featureBranch)
            verify(scmActions).mergeNoCommit(it.dir, ScmConfig.GIT_FLOW.releaseBranch)
        }

        verify(projectActions, times(projectDescriptor.allProjects.size)).setVersion(VersionFixtures.SNAPSHOT_002)

        verifyNoMoreInteractions(projectActions, scmActions, snapshotVersionProvider)
    }

    @Test
    fun test_execute_withTrunkBasedFlow() {
        // GIVEN
        val projectActions: ProjectActions = mock()
        val projectDescriptor: ProjectDescriptor<File> =
            ProjectDescriptorMockBuilder(projectActions, workingDir).build()

        underTest = createSetSnapshotVersionAction(
            projectDescriptor = projectDescriptor,
            scmConfig = ScmConfig.TRUNK_BASED_FLOW
        )

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.RELEASE_001)
        whenever(snapshotVersionProvider.provide(VersionFixtures.RELEASE_001)).thenReturn(VersionFixtures.SNAPSHOT_002)

        projectDescriptor.subProjects.forEach {
            whenever(scmActions.getLastTag(it.dir)).thenReturn(null)
            whenever(scmActions.getCommits(it.dir, null)).thenReturn(COMMITS)
        }

        // THEN
        underTest.execute()

        verify(projectActions).getVersion()
        verify(snapshotVersionProvider).provide(VersionFixtures.RELEASE_001)
        projectDescriptor.subProjects.forEach {
            verify(scmActions).getLastTag(it.dir)
            verify(scmActions).getCommits(it.dir, null)
        }
        verify(projectActions, times(projectDescriptor.allProjects.size)).setVersion(VersionFixtures.SNAPSHOT_002)

        verifyNoMoreInteractions(projectActions, scmActions, snapshotVersionProvider)
    }

    @Test
    fun test_execute_withTrunkBasedFlow_whenIsForceReleaseSet() {
        // GIVEN
        val projectActions: ProjectActions = mock()
        val projectDescriptor: ProjectDescriptor<File> =
            ProjectDescriptorMockBuilder(projectActions, workingDir).build()

        underTest = createSetSnapshotVersionAction(
            actionContext = ACTION_CONTEXT.copy(
                isForceRelease = true,
            ),
            projectDescriptor = projectDescriptor,
            scmConfig = ScmConfig.TRUNK_BASED_FLOW
        )

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.RELEASE_001)
        whenever(snapshotVersionProvider.provide(VersionFixtures.RELEASE_001)).thenReturn(VersionFixtures.SNAPSHOT_002)

        // THEN
        underTest.execute()

        verify(projectActions).getVersion()
        verify(snapshotVersionProvider).provide(VersionFixtures.RELEASE_001)
        verify(projectActions, times(projectDescriptor.allProjects.size)).setVersion(VersionFixtures.SNAPSHOT_002)

        verifyNoMoreInteractions(projectActions, scmActions, snapshotVersionProvider)
    }

    private fun createSetSnapshotVersionAction(
        actionContext: ActionContext = ACTION_CONTEXT,
        projectDescriptor: ProjectDescriptor<File>,
        scmConfig: ScmConfig = ScmConfig.GIT_FLOW
    ): SetSnapshotVersionAction<File> =
        SetSnapshotVersionAction(
            actionContext,
            projectDescriptor,
            scmActions,
            scmConfig,
            snapshotVersionProvider
        )

}
