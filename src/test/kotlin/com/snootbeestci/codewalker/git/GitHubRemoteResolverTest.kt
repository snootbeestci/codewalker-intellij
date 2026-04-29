package com.snootbeestci.codewalker.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GitHubRemoteResolverTest {

    @Test
    fun `https github url parses`() {
        val info = GitHubRemoteResolver.parseRemoteUrl("https://github.com/snootbeestci/codewalker")
        assertNotNull(info)
        assertEquals("github.com", info!!.host)
        assertEquals("snootbeestci", info.owner)
        assertEquals("codewalker", info.repo)
    }

    @Test
    fun `https github url with dot-git suffix parses`() {
        val info = GitHubRemoteResolver.parseRemoteUrl("https://github.com/snootbeestci/codewalker.git")
        assertNotNull(info)
        assertEquals("github.com", info!!.host)
        assertEquals("snootbeestci", info.owner)
        assertEquals("codewalker", info.repo)
    }

    @Test
    fun `https github url with trailing slash parses`() {
        val info = GitHubRemoteResolver.parseRemoteUrl("https://github.com/snootbeestci/codewalker/")
        assertNotNull(info)
        assertEquals("codewalker", info!!.repo)
    }

    @Test
    fun `ssh short form parses`() {
        val info = GitHubRemoteResolver.parseRemoteUrl("git@github.com:snootbeestci/codewalker.git")
        assertNotNull(info)
        assertEquals("github.com", info!!.host)
        assertEquals("snootbeestci", info.owner)
        assertEquals("codewalker", info.repo)
    }

    @Test
    fun `ssh short form without dot-git parses`() {
        val info = GitHubRemoteResolver.parseRemoteUrl("git@github.com:owner/repo")
        assertNotNull(info)
        assertEquals("github.com", info!!.host)
        assertEquals("owner", info.owner)
        assertEquals("repo", info.repo)
    }

    @Test
    fun `ssh long form parses`() {
        val info = GitHubRemoteResolver.parseRemoteUrl("ssh://git@github.com/owner/repo.git")
        assertNotNull(info)
        assertEquals("github.com", info!!.host)
        assertEquals("owner", info.owner)
        assertEquals("repo", info.repo)
    }

    @Test
    fun `github enterprise host is accepted`() {
        val info = GitHubRemoteResolver.parseRemoteUrl("https://github.mycompany.com/team/service.git")
        assertNotNull(info)
        assertEquals("github.mycompany.com", info!!.host)
        assertEquals("team", info.owner)
        assertEquals("service", info.repo)
    }

    @Test
    fun `github enterprise via ssh is accepted`() {
        val info = GitHubRemoteResolver.parseRemoteUrl("git@github.mycompany.com:team/service.git")
        assertNotNull(info)
        assertEquals("github.mycompany.com", info!!.host)
    }

    @Test
    fun `mixed case host is normalised to lowercase`() {
        val info = GitHubRemoteResolver.parseRemoteUrl("https://GitHub.MyCompany.com/Owner/Repo.git")
        assertNotNull(info)
        assertEquals("github.mycompany.com", info!!.host)
        // Owner/repo casing is preserved — only the host is canonicalised.
        assertEquals("Owner", info.owner)
        assertEquals("Repo", info.repo)
    }

    @Test
    fun `gitlab host is rejected`() {
        assertNull(GitHubRemoteResolver.parseRemoteUrl("https://gitlab.com/owner/repo.git"))
    }

    @Test
    fun `bitbucket host is rejected`() {
        assertNull(GitHubRemoteResolver.parseRemoteUrl("git@bitbucket.org:owner/repo.git"))
    }

    @Test
    fun `gitea host is rejected`() {
        assertNull(GitHubRemoteResolver.parseRemoteUrl("https://gitea.example.com/owner/repo"))
    }

    @Test
    fun `empty input returns null`() {
        assertNull(GitHubRemoteResolver.parseRemoteUrl(""))
        assertNull(GitHubRemoteResolver.parseRemoteUrl("   "))
    }

    @Test
    fun `malformed input returns null`() {
        assertNull(GitHubRemoteResolver.parseRemoteUrl("not a url"))
        assertNull(GitHubRemoteResolver.parseRemoteUrl("https://github.com/just-owner"))
        assertNull(GitHubRemoteResolver.parseRemoteUrl("https://"))
    }
}
