package io.github.mradzinski.eventus.sample.domain

import kotlinx.coroutines.flow.StateFlow

/** Whether the app may reach the server. The sample lets the user switch it off, to see failures. */
interface Connectivity {

    val online: StateFlow<Boolean>

    fun setOnline(online: Boolean)
}
