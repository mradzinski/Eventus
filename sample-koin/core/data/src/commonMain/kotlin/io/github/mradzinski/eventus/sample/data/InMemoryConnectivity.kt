package io.github.mradzinski.eventus.sample.data

import io.github.mradzinski.eventus.sample.domain.Connectivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class InMemoryConnectivity : Connectivity {

    override val online: StateFlow<Boolean>
        field = MutableStateFlow(true)

    override fun setOnline(online: Boolean) {
        this.online.value = online
    }
}
