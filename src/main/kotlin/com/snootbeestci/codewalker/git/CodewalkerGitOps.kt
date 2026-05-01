package com.snootbeestci.codewalker.git

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import git4idea.branch.GitBrancher
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GitOperationException(message: String) : Exception(message)

/**
 * Wraps the Git4Idea operations needed by the Codewalker session lifecycle:
 * dirty-tree detection, stash creation, fetch, branch checkout and stash
 * discovery. All operations are project-scoped.
 *
 * Only Git4Idea APIs are used. The plugin does not shell out to `git` directly.
 */
class CodewalkerGitOps(private val project: Project) {

    private val log = Logger.getInstance(CodewalkerGitOps::class.java)

    fun firstRepository(): GitRepository? =
        GitRepositoryManager.getInstance(project).repositories.firstOrNull()

    fun isWorkingTreeDirty(repo: GitRepository): Boolean {
        val tracked = ChangeListManager.getInstance(project).allChanges
        val untracked = repo.untrackedFilesHolder.retrieveUntrackedFilePaths()
        return tracked.isNotEmpty() || untracked.isNotEmpty()
    }

    fun stashChanges(repo: GitRepository, message: String) {
        val handler = GitLineHandler(project, repo.root, GitCommand.STASH)
        handler.addParameters("push", "--include-untracked", "-m", message)
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            throw GitOperationException(
                "git stash failed: ${result.errorOutputAsJoinedString.ifBlank { "unknown error" }}"
            )
        }
        repo.update()
    }

    fun fetchHeadRef(repo: GitRepository, headRef: String) {
        val handler = GitLineHandler(project, repo.root, GitCommand.FETCH)
        handler.addParameters("origin")
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            throw GitOperationException(
                "git fetch origin failed: ${result.errorOutputAsJoinedString.ifBlank { "unknown error" }}"
            )
        }
        repo.update()
        val found = repo.branches.localBranches.any { it.name == headRef } ||
            repo.branches.remoteBranches.any { it.nameForRemoteOperations == headRef }
        if (!found) {
            throw GitOperationException(
                "Branch '$headRef' is not on origin. Pull requests from forks " +
                    "are not supported in this version."
            )
        }
    }

    suspend fun checkoutBranch(repo: GitRepository, branchName: String) {
        suspendCancellableCoroutine { cont ->
            GitBrancher.getInstance(project).checkout(
                branchName,
                false,
                listOf(repo),
            ) {
                cont.resume(Unit)
            }
        }
    }

    /**
     * Returns the messages of stash entries whose message starts with the
     * codewalker tag. Used to surface leftovers from sessions that did not
     * clean up after themselves.
     */
    fun findCodewalkerStashes(repo: GitRepository): List<String> {
        val handler = GitLineHandler(project, repo.root, GitCommand.STASH)
        handler.addParameters("list", "--format=%gd %s")
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            log.debug("Codewalker: git stash list failed: ${result.errorOutputAsJoinedString}")
            return emptyList()
        }
        return filterCodewalkerStashes(result.output)
    }

    companion object {
        const val CODEWALKER_STASH_TAG = "codewalker-"

        fun stashMessage(sessionTag: String): String = "$CODEWALKER_STASH_TAG$sessionTag"

        /**
         * Pure helper extracted from [findCodewalkerStashes] for unit testing.
         * Given the raw lines from `git stash list --format=%gd %s`, returns
         * only those that came from a Codewalker session.
         */
        internal fun filterCodewalkerStashes(lines: List<String>): List<String> =
            lines.filter { it.contains(CODEWALKER_STASH_TAG) }
    }
}

