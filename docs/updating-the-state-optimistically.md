# Updating the state optimistically

When the user ticks a box or taps a heart, show the change at once and do the saving afterwards. If
the saving fails, put the old value back and say why. Where the "at once" happens depends on who
owns the data.

## State that only the screen holds

If the screen loaded a value once and keeps its own copy, the event handler changes that copy with
`updateOptimistically`:

```kotlin
class OnFavoriteChangeHandler(
    private val profiles: ProfileRepository,
) : EventHandler<OnFavoriteChange, ProfileUiState, ProfileEffect> {

    override val eventType = OnFavoriteChange::class
    override val strategy = DispatchStrategy.Sequential

    override suspend fun handle(event: OnFavoriteChange, store: ScreenStore<ProfileUiState, ProfileEffect>) {
        val profileId = store.current.profileId
        val favoriteBefore = store.current.favorite

        store.updateOptimistically(
            stateUpdate = { it.copy(favorite = event.favorite) },      // what the screen shows at once
            stateIfFailed = { it.copy(favorite = favoriteBefore) },    // what it goes back to
        ) {
            profiles.setFavorite(profileId, event.favorite)            // the work itself
        }
    }
}
```

- **`stateUpdate` and `stateIfFailed` only change what the screen shows.** Neither does any work,
  and neither can: they are not suspending, so a repository call inside them does not compile. The
  work goes in the block at the end and nowhere else.
- **If the work finishes, the change stays.** Nothing else happens.
- **If the work throws, `updateOptimistically` puts the state back and the exception goes on**, so
  the screen's `ErrorHandler` still tells the user why. If the work is cancelled,
  `updateOptimistically` puts the state back as well.
- **`stateIfFailed` receives the state as it is then, not a copy from before.** Undo only your own
  change, as above, and everything else that changed meanwhile (text the user typed, say) stays.
- **Choose the strategy for repeated taps.** `Sequential` applies them one after the other, each
  starting from what the previous one left. `DropIfBusy` ignores taps until the save is done. With
  the default, two saves for one value can overlap and undo each other in the wrong order.

## Data the screen observes

If the screen shows what a repository holds and follows its changes, the repository is the owner, so
it is the one that shows the change early and takes it back. The handler stays one line:

```kotlin
// The repository: the single source of truth for tasks.
override suspend fun setDone(id: Long, done: Boolean) {
    tasks.update { it.withDone(id, done) }    // every observer sees the tick at once

    try {
        sendToServer(id, done)
    } catch (error: OfflineException) {
        tasks.update { it.withDone(id, confirmedByServer(id)) }    // take it back
        throw error                                                // the handler lets it through to the ErrorHandler
    }
}

// The handler: the default strategy starts every tap at once.
override suspend fun handle(event: OnTaskDoneChange, store: TaskListStore) {
    tasks.setDone(id = event.taskId, done = event.done)
}
```

Do not use `updateOptimistically` here. The observing handler replaces that part of the state every
time the repository emits, so it would overwrite the optimistic value in the middle of the save.
Keeping one owner also gives one place to order, or merge, several writes to the same item.

The sample does both: the list's checkboxes go through the repository, and the editor's "Done" box,
which is the editor's own copy, uses `updateOptimistically`.

---

[Back to the README](../README.md#documentation)
