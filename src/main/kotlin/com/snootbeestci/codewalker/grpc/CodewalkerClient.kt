package com.snootbeestci.codewalker.grpc

import codewalker.v1.CodeWalkerGrpcKt
import codewalker.v1.Codewalker.ListPullRequestsResponse
import codewalker.v1.fetchFileAtRefRequest
import codewalker.v1.getVersionRequest
import codewalker.v1.listPullRequestsRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.messages.Topic
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

@Service
class CodewalkerClient : Disposable {

    private var channel: ManagedChannel? = null
    private var stub: CodeWalkerGrpcKt.CodeWalkerCoroutineStub? = null

    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    enum class ConnectionState {
        DISCONNECTED,
        INCOMPATIBLE,
        CONNECTED
    }

    suspend fun connect(address: String) {
        disconnect()
        try {
            channel = ManagedChannelBuilder.forTarget(address)
                .usePlaintext()
                .build()
            stub = CodeWalkerGrpcKt.CodeWalkerCoroutineStub(channel!!)
            val version = stub!!.getVersion(getVersionRequest {})
            connectionState = if (version.protoMajor == SUPPORTED_PROTO_MAJOR) {
                ConnectionState.CONNECTED
            } else {
                ConnectionState.INCOMPATIBLE
            }
        } catch (e: Exception) {
            connectionState = ConnectionState.DISCONNECTED
        }
        ApplicationManager.getApplication().messageBus
            .syncPublisher(CONNECTION_STATE_TOPIC)
            .stateChanged(connectionState)
    }

    fun disconnect() {
        channel?.shutdownNow()
        channel = null
        stub = null
        connectionState = ConnectionState.DISCONNECTED
    }

    fun getStub(): CodeWalkerGrpcKt.CodeWalkerCoroutineStub? = stub

    suspend fun listPullRequests(
        host: String,
        owner: String,
        repo: String,
        forgeToken: String,
    ): ListPullRequestsResponse {
        val s = stub ?: error("Not connected to backend")
        return s.listPullRequests(listPullRequestsRequest {
            this.host = host
            this.owner = owner
            this.repo = repo
            this.forgeToken = forgeToken
        })
    }

    suspend fun fetchFileAtRef(
        host: String,
        owner: String,
        repo: String,
        path: String,
        ref: String,
        forgeToken: String,
    ): ByteArray {
        val s = stub ?: error("Not connected to backend")
        val response = s.fetchFileAtRef(fetchFileAtRefRequest {
            this.host = host
            this.owner = owner
            this.repo = repo
            this.path = path
            this.ref = ref
            this.forgeToken = forgeToken
        })
        return response.content.toByteArray()
    }

    override fun dispose() { disconnect() }

    companion object {
        const val SUPPORTED_PROTO_MAJOR = 1
        val CONNECTION_STATE_TOPIC: Topic<ConnectionStateListener> =
            Topic.create("CodewalkerConnectionState", ConnectionStateListener::class.java)

        fun getInstance(): CodewalkerClient =
            ApplicationManager.getApplication().getService(CodewalkerClient::class.java)
    }
}

interface ConnectionStateListener {
    fun stateChanged(state: CodewalkerClient.ConnectionState)
}
