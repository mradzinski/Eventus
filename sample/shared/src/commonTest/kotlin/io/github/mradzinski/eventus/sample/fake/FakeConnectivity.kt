package io.github.mradzinski.eventus.sample.fake

import io.github.mradzinski.eventus.sample.data.Connectivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeConnectivity(online: Boolean = true) : Connectivity {

    override val online: StateFlow<Boolean>
        field = MutableStateFlow(online)

    override fun setOnline(online: Boolean) {
        this.online.value = online
    }
}
