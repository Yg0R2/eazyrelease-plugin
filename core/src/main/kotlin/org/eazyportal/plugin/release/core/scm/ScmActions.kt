package org.eazyportal.plugin.release.core.scm

import org.eazyportal.plugin.release.core.project.model.ProjectFile
import org.eazyportal.plugin.release.core.version.model.Version

interface ScmActions<T> {

    fun add(projectFile: ProjectFile<T>, vararg filePaths: String)

    fun checkout(projectFile: ProjectFile<T>, toRef: String)

    fun commit(projectFile: ProjectFile<T>, message: String)

    fun fetch(projectFile: ProjectFile<T>, remote: String)

    fun getCommits(projectFile: ProjectFile<T>, fromRef: String? = null, toRef: String = "HEAD"): List<String>

    fun getLastTag(projectFile: ProjectFile<T>, fromRef: String = "HEAD"): String?

    fun getSubmodules(projectFile: ProjectFile<T>): List<String>

    fun getTags(projectFile: ProjectFile<T>, fromRef: String = "HEAD"): List<String>

    fun mergeNoCommit(projectFile: ProjectFile<T>, fromBranch: String)

    fun push(projectFile: ProjectFile<T>, remote: String, vararg branches: String)

//    fun status(projectFile: ProjectFile<T>): List<String>
//
    fun tag(projectFile: ProjectFile<T>, version: Version)

}
