package com.snootbeestci.codewalker.forge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class GhCliTest {

    private class StubRunner(
        val outcome: GhCli.Outcome? = null,
        val throwable: Throwable? = null,
    ) : GhCli.ProcessRunner {
        var lastCommand: List<String>? = null
        override fun run(command: List<String>, timeoutSeconds: Long): GhCli.Outcome {
            lastCommand = command
            throwable?.let { throw it }
            return outcome ?: error("no outcome configured")
        }
    }

    @Test
    fun `successful invocation returns trimmed token`() {
        val runner = StubRunner(GhCli.Outcome(0, "ghp_abc123\n", ""))
        val cli = GhCli(processRunner = runner)

        val result = cli.fetchToken("github.com")

        assertTrue(result is GhCli.Result.Success)
        assertEquals("ghp_abc123", (result as GhCli.Result.Success).token)
        assertEquals(listOf("gh", "auth", "token", "--hostname", "github.com"), runner.lastCommand)
    }

    @Test
    fun `host is normalised before invocation`() {
        val runner = StubRunner(GhCli.Outcome(0, "ghp_abc\n", ""))
        val cli = GhCli(processRunner = runner)

        cli.fetchToken("https://GitHub.com/")

        assertEquals(listOf("gh", "auth", "token", "--hostname", "github.com"), runner.lastCommand)
    }

    @Test
    fun `IOException maps to NotInstalled`() {
        val runner = StubRunner(throwable = IOException("Cannot run program \"gh\""))
        val cli = GhCli(processRunner = runner)

        val result = cli.fetchToken("github.com")

        assertTrue(result is GhCli.Result.NotInstalled)
    }

    @Test
    fun `non-zero exit maps to NoToken with stderr`() {
        val runner = StubRunner(GhCli.Outcome(1, "", "no oauth token\n"))
        val cli = GhCli(processRunner = runner)

        val result = cli.fetchToken("github.com")

        assertTrue(result is GhCli.Result.NoToken)
        assertTrue((result as GhCli.Result.NoToken).message.contains("no oauth token"))
    }

    @Test
    fun `empty stdout with zero exit maps to NoToken`() {
        val runner = StubRunner(GhCli.Outcome(0, "  \n", ""))
        val cli = GhCli(processRunner = runner)

        val result = cli.fetchToken("github.com")

        assertTrue(result is GhCli.Result.NoToken)
    }

    @Test
    fun `empty host is rejected`() {
        val runner = StubRunner()
        val cli = GhCli(processRunner = runner)

        val result = cli.fetchToken("")

        assertTrue(result is GhCli.Result.Failed)
    }
}
