# Where Eventus sits in your app

Eventus belongs to the presentation layer and nowhere else.

![Three layers: the presentation layer uses Eventus, where the UI talks to the router and the router runs the event handlers; the handlers depend on repository interfaces or use cases in the domain layer; the data layer implements those interfaces. The domain and data layers do not use Eventus](where-eventus-sits.svg)

- **Screens and their event handlers depend on Eventus.** In a modular app, that is your feature
  modules. A feature module holds one or more screens, and each screen has its own router. See
  [structuring the events of a feature](structuring-the-events-of-a-feature.md).
- **Handlers depend inward**, on repository interfaces or use cases. They never reach a data source.
- **Domain and data code never import Eventus.** A repository that takes a `ScreenStore` is a sign
  that presentation logic has leaked downward.
- **The UI depends on the router interface only**: `state`, `effects`, `onEvent`.

You do not need a use-case layer to use Eventus: a handler already gives an operation a name, a file
and a test. Add a use case when the same orchestration appears in a third handler, or when something
that is not a screen (a widget, a background job) needs it too.

---

[Back to the README](../README.md#documentation)
