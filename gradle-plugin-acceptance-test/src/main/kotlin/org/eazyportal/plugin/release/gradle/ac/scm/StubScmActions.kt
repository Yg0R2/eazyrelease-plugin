package org.eazyportal.plugin.release.gradle.ac.scm

import org.eazyportal.plugin.release.core.project.model.ProjectFile
import org.eazyportal.plugin.release.core.scm.ScmActions
import org.eazyportal.plugin.release.core.version.model.Version
import java.io.File

class StubScmActions(
    private val commits: List<String> = listOf("fix: message"),
    private val lastTag: String = "0.0.1",
    private val submodules: List<String> = listOf(),
    private val tags: List<String> = listOf("0.0.1", "stable")
) : ScmActions<File> {

    override fun add(projectFile: ProjectFile<File>, vararg filePaths: String) = Unit

    override fun checkout(projectFile: ProjectFile<File>, toRef: String) = Unit

    override fun commit(projectFile: ProjectFile<File>, message: String) = Unit

    override fun fetch(projectFile: ProjectFile<File>, remote: String) = Unit

    override fun getCommits(projectFile: ProjectFile<File>, fromRef: String?, toRef: String): List<String> = commits

    override fun getLastTag(projectFile: ProjectFile<File>, fromRef: String): String? = lastTag

    override fun getSubmodules(projectFile: ProjectFile<File>): List<String> = submodules

    override fun getTags(projectFile: ProjectFile<File>, fromRef: String): List<String> = tags

    override fun mergeNoCommit(projectFile: ProjectFile<File>, fromBranch: String) = Unit

    override fun push(projectFile: ProjectFile<File>, remote: String, vararg branches: String) = Unit

    override fun tag(projectFile: ProjectFile<File>, version: Version) = Unit

}
