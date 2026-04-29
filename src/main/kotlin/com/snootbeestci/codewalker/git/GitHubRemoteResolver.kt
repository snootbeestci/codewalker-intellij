package com.snootbeestci.codewalker.git

import com.intellij.openapi.project.Project
import com.snootbeestci.codewalker.forge.HostNormalizer
import git4idea.repo.GitRepositoryManager

data class ProjectRepoInfo(
    val host: String,
    val owner: String,
    val repo: String,
)

object GitHubRemoteResolver {

    fun resolve(project: Project): ProjectRepoInfo? {
        val repos = GitRepositoryManager.getInstance(project).repositories
        val origin = repos.firstNotNullOfOrNull { repo ->
            repo.remotes.firstOrNull { it.name == "origin" }
        } ?: return null

        val url = origin.firstUrl ?: return null
        return parseRemoteUrl(url)
    }

    internal fun parseRemoteUrl(url: String): ProjectRepoInfo? {
        val cleaned = url.trim().removeSuffix("/").let {
            if (it.endsWith(".git")) it.removeSuffix(".git") else it
        }
        if (cleaned.isEmpty()) return null

        val sshShort = Regex("""(?:ssh://)?git@([^:/]+)[:/]([^/]+)/([^/]+)$""")
        sshShort.matchEntire(cleaned)?.let { match ->
            val (rawHost, owner, repo) = match.destructured
            return makeInfo(rawHost, owner, repo)
        }

        val https = Regex("""https?://([^/]+)/([^/]+)/([^/]+)$""")
        https.matchEntire(cleaned)?.let { match ->
            val (rawHost, owner, repo) = match.destructured
            return makeInfo(rawHost, owner, repo)
        }

        return null
    }

    private fun makeInfo(rawHost: String, owner: String, repo: String): ProjectRepoInfo? {
        if (owner.isEmpty() || repo.isEmpty()) return null
        val host = HostNormalizer.normalize(rawHost)
        if (!host.contains("github")) return null
        return ProjectRepoInfo(host, owner, repo)
    }
}
