package org.eazyportal.plugin.release.core.action

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eazyportal.plugin.release.core.TestFixtures.ACTION_CONTEXT
import org.eazyportal.plugin.release.core.TestFixtures.CONVENTIONAL_COMMIT_TYPES
import org.eazyportal.plugin.release.core.action.model.ActionContext
import org.eazyportal.plugin.release.core.project.ProjectActions
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptor
import org.eazyportal.plugin.release.core.project.model.ProjectDescriptorMockBuilder
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.eazyportal.plugin.release.core.scm.model.ScmConfig
import org.eazyportal.plugin.release.core.version.ReleaseVersionProvider
import org.eazyportal.plugin.release.core.version.VersionIncrementProvider
import org.eazyportal.plugin.release.core.version.model.VersionFixtures
import org.eazyportal.plugin.release.core.version.model.VersionIncrement
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File

internal class SetReleaseVersionActionTest : ReleaseActionBaseTest() {

    @Mock
    private lateinit var releaseVersionProvider: ReleaseVersionProvider

    @Mock
    private lateinit var scmActions: ScmActions<File>

    @Mock
    private lateinit var versionIncrementProvider: VersionIncrementProvider

    private lateinit var underTest: SetReleaseVersionAction<File>

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun test_execute_withGitFlow() {
        // GIVEN
        val projectActions: ProjectActions = mock()
        val projectDescriptor: ProjectDescriptor<File> = ProjectDescriptorMockBuilder(projectActions, workingDir)
            .build()

        underTest = createSetReleaseVersionAction(
            projectDescriptor = projectDescriptor,
            scmConfig = ScmConfig.GIT_FLOW
        )

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.SNAPSHOT_001)

        projectDescriptor.subProjects.forEach {
            whenever(scmActions.getLastTag(it.dir)).thenReturn(null)
            whenever(scmActions.getCommits(it.dir, null)).thenReturn(COMMITS)
        }

        whenever(scmActions.getLastTag(projectDescriptor.rootProject.dir)).thenReturn(GIT_TAG)
        whenever(scmActions.getCommits(projectDescriptor.rootProject.dir, GIT_TAG)).thenReturn(COMMITS)

        whenever(versionIncrementProvider.provide(COMMITS, CONVENTIONAL_COMMIT_TYPES)).thenReturn(VERSION_INCREMENT)
        whenever(releaseVersionProvider.provide(VersionFixtures.SNAPSHOT_001, VERSION_INCREMENT)).thenReturn(
            VersionFixtures.RELEASE_001
        )

        // THEN
        underTest.execute()

        projectDescriptor.subProjects.forEach {
            verify(scmActions).getLastTag(it.dir)
            verify(scmActions).getCommits(it.dir, null)
        }

        verify(scmActions).getLastTag(projectDescriptor.rootProject.dir)
        verify(scmActions).getCommits(projectDescriptor.rootProject.dir, GIT_TAG)

        verify(projectActions, times(2)).getVersion()
        verify(versionIncrementProvider, times(2)).provide(COMMITS, CONVENTIONAL_COMMIT_TYPES)

        projectDescriptor.allProjects.forEach {
            verify(scmActions).checkout(it.dir, ScmConfig.GIT_FLOW.releaseBranch)
            verify(scmActions).mergeNoCommit(it.dir, ScmConfig.GIT_FLOW.featureBranch)
        }

        verify(releaseVersionProvider, times(2)).provide(VersionFixtures.SNAPSHOT_001, VERSION_INCREMENT)
        verify(projectActions, times(2)).setVersion(VersionFixtures.RELEASE_001)

        verifyNoMoreInteractions(projectActions, releaseVersionProvider, scmActions, versionIncrementProvider)
    }

    @Test
    fun test_execute_withTrunkBasedFlow() {
        // GIVEN
        val projectActions: ProjectActions = mock()
        val projectDescriptor: ProjectDescriptor<File> = ProjectDescriptorMockBuilder(projectActions, workingDir)
            .build()

        underTest = createSetReleaseVersionAction(
            projectDescriptor = projectDescriptor,
            scmConfig = ScmConfig.TRUNK_BASED_FLOW
        )

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.SNAPSHOT_001)

        projectDescriptor.subProjects.forEach {
            whenever(scmActions.getLastTag(it.dir)).thenReturn(null)
            whenever(scmActions.getCommits(it.dir, null)).thenReturn(COMMITS)
        }

        whenever(scmActions.getLastTag(projectDescriptor.rootProject.dir)).thenReturn(GIT_TAG)
        whenever(scmActions.getCommits(projectDescriptor.rootProject.dir, GIT_TAG)).thenReturn(COMMITS)

        whenever(versionIncrementProvider.provide(COMMITS, CONVENTIONAL_COMMIT_TYPES)).thenReturn(VERSION_INCREMENT)
        whenever(releaseVersionProvider.provide(VersionFixtures.SNAPSHOT_001, VERSION_INCREMENT)).thenReturn(
            VersionFixtures.RELEASE_001
        )

        // THEN
        underTest.execute()

        projectDescriptor.subProjects.forEach {
            verify(scmActions).getLastTag(it.dir)
            verify(scmActions).getCommits(it.dir, null)
        }

        verify(scmActions).getLastTag(projectDescriptor.rootProject.dir)
        verify(scmActions).getCommits(projectDescriptor.rootProject.dir, GIT_TAG)

        verify(projectActions, times(2)).getVersion()
        verify(versionIncrementProvider, times(2)).provide(COMMITS, CONVENTIONAL_COMMIT_TYPES)

        verify(releaseVersionProvider, times(2)).provide(VersionFixtures.SNAPSHOT_001, VERSION_INCREMENT)
        verify(projectActions, times(2)).setVersion(VersionFixtures.RELEASE_001)

        verifyNoMoreInteractions(projectActions, releaseVersionProvider, scmActions, versionIncrementProvider)
    }

    @MethodSource("isForceReleaseVersionIncrements")
    @ParameterizedTest
    fun test_execute_shouldRelease_whenIsForceReleaseSet(
        versionIncrement: VersionIncrement?,
        expectedVersionIncrement: VersionIncrement
    ) {
        // GIVEN
        val projectActions: ProjectActions = mock()
        val projectDescriptor: ProjectDescriptor<File> = ProjectDescriptorMockBuilder(projectActions, workingDir)
            .build()

        val actionContext = ACTION_CONTEXT.copy(
            isForceRelease = true
        )

        underTest = createSetReleaseVersionAction(
            actionContext = actionContext,
            projectDescriptor = projectDescriptor
        )

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.SNAPSHOT_001)

        whenever(scmActions.getLastTag(any(), anyOrNull())).thenReturn(null)
        whenever(scmActions.getCommits(any(), anyOrNull(), anyOrNull())).thenReturn(COMMITS)

        whenever(versionIncrementProvider.provide(COMMITS, CONVENTIONAL_COMMIT_TYPES)).thenReturn(versionIncrement)
        whenever(releaseVersionProvider.provide(VersionFixtures.SNAPSHOT_001, expectedVersionIncrement))
            .thenReturn(VersionFixtures.RELEASE_001)

        // THEN
        underTest.execute()

        verify(scmActions, times(2)).getLastTag(any(), anyOrNull())
        verify(scmActions, times(2)).getCommits(any(), anyOrNull(), anyOrNull())

        verify(projectActions, times(2)).getVersion()
        verify(versionIncrementProvider, times(2)).provide(COMMITS, CONVENTIONAL_COMMIT_TYPES)

        verify(scmActions, times(2)).checkout(any(), anyOrNull())
        verify(scmActions, times(2)).mergeNoCommit(any(), anyOrNull())

        verify(releaseVersionProvider, times(2)).provide(VersionFixtures.SNAPSHOT_001, expectedVersionIncrement)
        verify(projectActions, times(2)).setVersion(VersionFixtures.RELEASE_001)

        verifyNoMoreInteractions(projectActions, releaseVersionProvider, scmActions, versionIncrementProvider)
    }

    @MethodSource("invalidVersionIncrements")
    @ParameterizedTest
    fun test_execute_shouldFail_whenVersionIncrementIsInvalid(versionIncrement: VersionIncrement?) {
        // GIVEN
        val projectActions: ProjectActions = mock()
        val projectDescriptor: ProjectDescriptor<File> = ProjectDescriptorMockBuilder(projectActions, workingDir)
            .build()

        underTest = createSetReleaseVersionAction(projectDescriptor = projectDescriptor)

        // WHEN
        whenever(projectActions.getVersion()).thenReturn(VersionFixtures.SNAPSHOT_001)
        whenever(scmActions.getLastTag(any(), anyOrNull())).thenReturn(null)
        whenever(scmActions.getCommits(any(), anyOrNull(), anyOrNull())).thenReturn(COMMITS)
        whenever(versionIncrementProvider.provide(COMMITS, CONVENTIONAL_COMMIT_TYPES)).thenReturn(versionIncrement)

        // THEN
        assertThatThrownBy { underTest.execute() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("There are no acceptable commits.")

        verifyNoInteractions(releaseVersionProvider)
        verify(projectActions, times(2)).getVersion()
        verify(scmActions, times(2)).getLastTag(any(), anyOrNull())
        verify(scmActions, times(2)).getCommits(any(), anyOrNull(), anyOrNull())
        verify(versionIncrementProvider, times(2)).provide(COMMITS, CONVENTIONAL_COMMIT_TYPES)
        verifyNoMoreInteractions(projectActions, scmActions, versionIncrementProvider)
    }

    private fun createSetReleaseVersionAction(
        actionContext: ActionContext = ACTION_CONTEXT,
        projectDescriptor: ProjectDescriptor<File>,
        scmConfig: ScmConfig = ScmConfig.GIT_FLOW
    ): SetReleaseVersionAction<File> =
        SetReleaseVersionAction(
            actionContext,
            CONVENTIONAL_COMMIT_TYPES,
            releaseVersionProvider,
            projectDescriptor,
            scmActions,
            scmConfig,
            versionIncrementProvider
        )

    companion object {
        private val COMMITS = listOf("fix: message", "test: message")
        private const val GIT_TAG = "0.0.0"
        private val VERSION_INCREMENT = VersionIncrement.PATCH

        @JvmStatic
        private fun invalidVersionIncrements() = listOf(
            Arguments.of(null),
            Arguments.of(VersionIncrement.NONE)
        )

        @JvmStatic
        private fun isForceReleaseVersionIncrements() = listOf(
            Arguments.of(null, VersionIncrement.PATCH),
            Arguments.of(VersionIncrement.NONE, VersionIncrement.PATCH),
            Arguments.of(VersionIncrement.PATCH, VersionIncrement.PATCH),
            Arguments.of(VersionIncrement.MINOR, VersionIncrement.MINOR),
            Arguments.of(VersionIncrement.MAJOR, VersionIncrement.MAJOR)
        )
    }

}
