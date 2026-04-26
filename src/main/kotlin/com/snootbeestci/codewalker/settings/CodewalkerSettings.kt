package com.snootbeestci.codewalker.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(
    name = "CodewalkerSettings",
    storages = [Storage("codewalker.xml")]
)
class CodewalkerSettings : PersistentStateComponent<CodewalkerSettings.State> {

    data class State(
        var backendAddress: String = "localhost:50051",
        var experienceLevel: String = "EXPERIENCE_LEVEL_MID"
    )

    private var state = State()

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    companion object {
        fun getInstance(): CodewalkerSettings =
            ApplicationManager.getApplication().getService(CodewalkerSettings::class.java)
    }
}
