# Dispatching an event from a handler

Handling one event often has to start another. Adding an item to a cart opens the cart and then
refreshes its total. A failed request signs the user out. The store dispatches the second event
with `dispatch`:

```kotlin
class OnAddToCartHandler(
    private val cart: CartRepository,
) : CartHandler<OnAddToCart> {

    override val eventType = OnAddToCart::class

    override suspend fun handle(event: OnAddToCart, store: CartStore) {
        cart.add(event.item)

        store.emit(CartEffect.OpenCart)
        store.dispatch(OnCartRefresh)
    }
}
```

`OnCartRefresh` is then handled exactly as if the UI had sent it through `onEvent`: by its own event
handler, under its own [dispatch strategy](choosing-a-dispatch-strategy.md), and with its own
[reports](logging-events.md).

## Contents

- [Waiting for the event](#waiting-for-the-event)
- [Dispatching from an error handler](#dispatching-from-an-error-handler)
- [What you can rely on](#what-you-can-rely-on)
- [Reusing logic between event handlers](#reusing-logic-between-event-handlers)
- [Testing an event handler that dispatches an event](#testing-an-event-handler-that-dispatches-an-event)

## Waiting for the event

`dispatch` does not wait. It returns the `Job` that handles the event, and the event handler that
dispatched it goes on at once. To go on only after the second event has been handled to its end,
join the job:

```kotlin
override suspend fun handle(event: OnAddToCart, store: CartStore) {
    cart.add(event.item)

    store.dispatch(OnCartRefresh).join()
    store.emit(CartEffect.OpenCart)       // the total is already up to date
}
```

`join` returns when the event has been handled, has failed or was cancelled. When the strategy of
the event ignored it, the job has already ended and `join` returns at once.

Do not join an event whose strategy makes it wait for the event that dispatched it, such as two
events that share `Sequential`. Each would wait for the other, and neither would end.

## Dispatching from an error handler

An error handler receives the same store, so it dispatches an event the same way:

```kotlin
class CartErrorHandler : ErrorHandler<CartEvent, CartUiState, CartEffect> {

    override suspend fun handle(event: CartEvent, error: Throwable, store: CartStore) {
        if (error is SessionExpiredException) {
            store.dispatch(OnSignOut)

            return
        }

        store.emit(CartEffect.ShowFailure(error.toFailureReason()))
    }
}
```

The error handler's `handle` is a suspending function, so it can join the job as well. It runs in
the coroutine of the event that failed. That event stays active until `handle` returns, and `handle`
is cancelled with it: when another event replaces the failed one, or when the router's scope is
cancelled.

An event handler that catches a failure in order to recover can dispatch an event too. Catch only to
recover; a failure that only needs a message still goes to the error handler, as
[handling failures](handling-failures.md) explains.

## What you can rely on

- **The event belongs to the same screen.** `dispatch` reaches the router that called the event
  handler, and no other. See [reaching another screen](reaching-another-screen.md) for the ways across.
- **An event with no event handler throws from `dispatch`**, with the same message `onEvent` gives.
  The event that dispatched it then fails, and that failure goes to the error handler.
- **A dispatched event does not depend on the event that dispatched it.** It keeps running when
  that event ends, fails or is replaced.
- **Nothing stops two events from dispatching each other.** If each dispatches the other without a
  condition, they go on until the router's scope is cancelled.
- **A store you create yourself cannot dispatch.** It belongs to no router, so `dispatch` throws.

## Reusing logic between event handlers

`dispatch` starts an event. To share logic between two event handlers, give both the same dependency:

> [!TIP]
> **Do:** dispatch the event, and let the router run its event handler.
>
> ```kotlin
> class OnAddToCartHandler(
>     private val cart: CartRepository,
> ) : CartHandler<OnAddToCart> {
>
>     override val eventType = OnAddToCart::class
>
>     override suspend fun handle(event: OnAddToCart, store: CartStore) {
>         cart.add(event.item)
>
>         store.dispatch(OnCartRefresh)
>     }
> }
> ```

> [!CAUTION]
> **Don't:** hold another event handler and call it. Its dispatch strategy is skipped, and the logs
> show one event where two things happened.
>
> ```kotlin
> class OnAddToCartHandler(
>     private val cart: CartRepository,
>     private val refresh: OnCartRefreshHandler,
> ) : CartHandler<OnAddToCart> {
>
>     override val eventType = OnAddToCart::class
>
>     override suspend fun handle(event: OnAddToCart, store: CartStore) {
>         cart.add(event.item)
>
>         refresh.handle(OnCartRefresh, store)
>     }
> }
> ```

## Testing an event handler that dispatches an event

Give `runEventHandlerTest` both event handlers. The second event then runs as it does in the app,
and the reports show both events:

```kotlin
@Test
fun `adding an item refreshes the cart`() = runEventHandlerTest(
    handlers = listOf(OnAddToCartHandler(cart), OnCartRefreshHandler(cart)),
    initialState = CartUiState(),
) {
    dispatch(OnAddToCart(item = tea))

    state.total shouldBe tea.price
    effects shouldContainExactly listOf(CartEffect.OpenCart)
}
```

See [testing](testing.md) for everything the block gives you.

---

[Back to the README](../README.md#documentation)
