package io.github.mradzinski.eventus.sample.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the app may reach the server. The sample lets the user switch it off, to see failures. */
interface Connectivity {

    val online: StateFlow<Boolean>

    fun setOnline(online: Boolean)
}

internal class InMemoryConnectivity : Connectivity {

    override val online: StateFlow<Boolean>
        field = MutableStateFlow(true)

    override fun setOnline(online: Boolean) {
        this.online.value = online
    }
}
