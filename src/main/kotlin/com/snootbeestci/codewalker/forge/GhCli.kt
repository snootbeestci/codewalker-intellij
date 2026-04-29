package com.snootbeestci.codewalker.forge

import java.util.concurrent.TimeUnit

/**
 * Wrapper around the `gh` CLI for one-shot token import.
 *
 * Runs `gh auth token --hostname <host>` and returns the token if the
 * command succeeds. The plugin uses this as an explicit "Import" action
 * in settings — never as a runtime fallback, because doing that would
 * silently leak whichever token `gh` happens to be holding.
 */
class GhCli(
    private val processRunner: ProcessRunner = DefaultProcessRunner,
    private val timeoutSeconds: Long = 5,
) {

    sealed class Result {
        data class Success(val token: String) : Result()
        data class NotInstalled(val message: String) : Result()
        data class NoToken(val message: String) : Result()
        data class Failed(val message: String) : Result()
    }

    fun fetchToken(host: String): Result {
        val canonical = HostNormalizer.normalize(host)
        if (canonical.isEmpty()) {
            return Result.Failed("Empty host")
        }
        val outcome = try {
            processRunner.run(
                listOf("gh", "auth", "token", "--hostname", canonical),
                timeoutSeconds,
            )
        } catch (e: java.io.IOException) {
            return Result.NotInstalled(
                "`gh` CLI not found on PATH. Install from https://cli.github.com/ and run `gh auth login`."
            )
        } catch (e: Exception) {
            return Result.Failed(e.message ?: "gh invocation failed")
        }

        return when {
            outcome.exitCode == 0 -> {
                val token = outcome.stdout.trim()
                if (token.isEmpty()) {
                    Result.NoToken("`gh` returned no token for $canonical. Run `gh auth login --hostname $canonical`.")
                } else {
                    Result.Success(token)
                }
            }
            else -> {
                val stderr = outcome.stderr.trim()
                val msg = stderr.ifEmpty { "exit code ${outcome.exitCode}" }
                Result.NoToken("`gh` could not return a token for $canonical: $msg")
            }
        }
    }

    data class Outcome(val exitCode: Int, val stdout: String, val stderr: String)

    interface ProcessRunner {
        fun run(command: List<String>, timeoutSeconds: Long): Outcome
    }

    private object DefaultProcessRunner : ProcessRunner {
        override fun run(command: List<String>, timeoutSeconds: Long): Outcome {
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw RuntimeException("gh timed out after ${timeoutSeconds}s")
            }
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            return Outcome(process.exitValue(), stdout, stderr)
        }
    }
}
