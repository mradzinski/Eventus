package io.github.mradzinski.eventus.integration.metro

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides

/** A dependency the handlers take as an interface, bound the way a Metro app binds a repository. */
internal interface NoteRepository {

    fun notes(): List<String>
}

@ContributesBinding(AppScope::class)
@Inject
internal class InMemoryNoteRepository : NoteRepository {

    override fun notes(): List<String> = listOf("milk", "bread")
}

/** A dependency that only a qualifier tells apart from any other `String`. */
@ContributesTo(AppScope::class)
@BindingContainer
internal object GreetingBindings {

    @Provides
    @Named("greeting")
    fun greeting(repository: NoteRepository): String = "Hello, ${repository.notes().size} notes"
}
