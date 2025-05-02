package org.eazyportal.plugin.release.core.scm

import org.eazyportal.plugin.release.core.executor.CommandExecutor
import org.eazyportal.plugin.release.core.project.model.ProjectFile
import org.eazyportal.plugin.release.core.scm.exception.ScmActionException
import org.eazyportal.plugin.release.core.utils.isWindows
import org.eazyportal.plugin.release.core.version.model.Version

class GitActions<T>(
    private val commandExecutor: CommandExecutor<ProjectFile<T>>
) : ScmActions<T> {

    companion object {
        internal val GIT_EXECUTABLE =
            if (isWindows()) "git.exe" else "git"

        val LINE_BREAK_REGEX = Regex("\r?\n")
    }

    override fun add(projectFile: ProjectFile<T>, vararg filePaths: String) {
        execute(projectFile, "add", *filePaths)
    }

    override fun checkout(projectFile: ProjectFile<T>, toRef: String) {
        execute(projectFile, "checkout", toRef)
    }

    override fun commit(projectFile: ProjectFile<T>, message: String) {
        execute(projectFile, "commit", "-m", message)
    }

    fun execute(projectFile: ProjectFile<T>, vararg gitCommands: String): String {
        try {
            return commandExecutor.execute(projectFile, GIT_EXECUTABLE, *gitCommands)
        }
        catch (exception: Exception) {
            throw ScmActionException(exception)
        }
    }

    override fun fetch(projectFile: ProjectFile<T>, remote: String) {
        execute(projectFile, "fetch", remote, "--tags", "--prune", "--prune-tags", "--recurse-submodules")
    }

    override fun getCommits(projectFile: ProjectFile<T>, fromRef: String?, toRef: String): List<String> {
        val refs = listOfNotNull(fromRef, toRef)
            .joinToString("..")

        return execute(projectFile, "log", "--pretty=format:%s", refs)
            .split(LINE_BREAK_REGEX)
    }

    override fun getLastTag(projectFile: ProjectFile<T>, fromRef: String): String? =
        try  {
            execute(projectFile, "describe", "--abbrev=0", "--tags", fromRef)
                .trim()
        } catch (_: Exception) {
            null
        }

    override fun getSubmodules(projectFile: ProjectFile<T>): List<String> {
        return execute(projectFile, "submodule")
            .lines()
            .map { it.replace(Regex("""^[\s\W]?\w+\s(.*?)\s?(\(.*\))?${'$'}"""), "$1") }
            .filter { it.isNotBlank() }
            .map { it.trim() }
            .toList()
    }

    override fun getTags(projectFile: ProjectFile<T>, fromRef: String): List<String> {
        return execute(projectFile, "tag", "--sort=-creatordate", "--contains", fromRef)
            .split(LINE_BREAK_REGEX)
            .filter { it.isNotBlank() }
    }

    override fun mergeNoCommit(projectFile: ProjectFile<T>, fromBranch: String) {
        execute(projectFile, "merge", "--no-ff", "--no-commit", "--strategy-option=theirs", fromBranch)
    }

    override fun push(projectFile: ProjectFile<T>, remote: String, vararg branches: String) {
        val branchesRefs = branches
            .distinct()
            .map { "$it:$it" }
            .toTypedArray()

        execute(projectFile, "push", "--atomic", "--tags", "--recurse-submodules=on-demand", remote, *branchesRefs)
    }

    override fun tag(projectFile: ProjectFile<T>, version: Version) {
        execute(projectFile, "tag", "-a", version.toString(), "-m", "v$version")
    }

}
