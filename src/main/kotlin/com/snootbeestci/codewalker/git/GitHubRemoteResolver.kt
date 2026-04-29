package com.snootbeestci.codewalker.git

import com.intellij.openapi.project.Project
import com.snootbeestci.codewalker.forge.HostNormalizer
import git4idea.repo.GitRepositoryManager

data class ProjectRepoInfo(
    val host: String,
    val owner: String,
    val repo: String,
)

/**
 * Result of parsing a git remote URL into a structured outcome that
 * distinguishes the cases UI code needs to render differently.
 */
sealed class RemoteParseResult {
    data class Ok(val info: ProjectRepoInfo) : RemoteParseResult()
    data class NonGitHub(val host: String) : RemoteParseResult()
    data object Unparseable : RemoteParseResult()
    data object Empty : RemoteParseResult()
}

object GitHubRemoteResolver {

    fun resolve(project: Project): ProjectRepoInfo? {
        val repos = GitRepositoryManager.getInstance(project).repositories
        val origin = repos.firstNotNullOfOrNull { repo ->
            repo.remotes.firstOrNull { it.name == "origin" }
        } ?: return null

        val url = origin.firstUrl ?: return null
        return parseRemoteUrl(url)
    }

    /**
     * Result-bearing variant. UI callers use this when they need to
     * distinguish "non-GitHub host" from "unparseable" to render
     * different error messages.
     */
    fun resolveResult(project: Project): RemoteParseResult {
        val repos = GitRepositoryManager.getInstance(project).repositories
        val origin = repos.firstNotNullOfOrNull { repo ->
            repo.remotes.firstOrNull { it.name == "origin" }
        } ?: return RemoteParseResult.Empty

        val url = origin.firstUrl ?: return RemoteParseResult.Empty
        return parseRemoteUrlResult(url)
    }

    internal fun parseRemoteUrl(url: String): ProjectRepoInfo? {
        return when (val r = parseRemoteUrlResult(url)) {
            is RemoteParseResult.Ok -> r.info
            else -> null
        }
    }

    /**
     * Single source of truth for all URL → host extraction in the plugin.
     */
    internal fun parseRemoteUrlResult(url: String): RemoteParseResult {
        val cleaned = url.trim().removeSuffix("/").let {
            if (it.endsWith(".git")) it.removeSuffix(".git") else it
        }
        if (cleaned.isEmpty()) return RemoteParseResult.Empty

        val sshShort = Regex("""(?:ssh://)?git@([^:/]+)[:/]([^/]+)/([^/]+)$""")
        sshShort.matchEntire(cleaned)?.let { match ->
            val (rawHost, owner, repo) = match.destructured
            return classify(rawHost, owner, repo)
        }

        val https = Regex("""https?://([^/]+)/([^/]+)/([^/]+)$""")
        https.matchEntire(cleaned)?.let { match ->
            val (rawHost, owner, repo) = match.destructured
            return classify(rawHost, owner, repo)
        }

        val hostOnly = extractHostOnly(cleaned)
        return if (hostOnly != null) RemoteParseResult.NonGitHub(hostOnly)
        else RemoteParseResult.Unparseable
    }

    private fun classify(rawHost: String, owner: String, repo: String): RemoteParseResult {
        if (owner.isEmpty() || repo.isEmpty()) return RemoteParseResult.Unparseable
        val host = HostNormalizer.normalize(rawHost)
        return if (host.contains("github")) {
            RemoteParseResult.Ok(ProjectRepoInfo(host, owner, repo))
        } else {
            RemoteParseResult.NonGitHub(host)
        }
    }

    private fun extractHostOnly(cleaned: String): String? {
        Regex("""(?:ssh://)?git@([^:/]+)[:/].*""").matchEntire(cleaned)?.let {
            return HostNormalizer.normalize(it.groupValues[1])
        }
        Regex("""https?://([^/]+)(?:/.*)?""").matchEntire(cleaned)?.let {
            return HostNormalizer.normalize(it.groupValues[1])
        }
        return null
    }
}
