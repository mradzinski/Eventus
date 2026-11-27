package io.github.mradzinski.eventus.sample.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.mradzinski.eventus.sample.domain.Connectivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class InMemoryConnectivity : Connectivity {

    override val online: StateFlow<Boolean>
        field = MutableStateFlow(true)

    override fun setOnline(online: Boolean) {
        this.online.value = online
    }
}
