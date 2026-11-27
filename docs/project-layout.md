# Project layout

| Path | What |
|---|---|
| `eventus/` | The library. All code is in `commonMain`. |
| `eventus/api/` | The recorded public API. `checkKotlinAbi` fails when the code no longer matches it. |
| `eventus-koin/` | The `@EventusKoinModule` annotation, and nothing else. |
| `eventus-metro/` | The `@ContributesEventHandlers` annotation, and nothing else. |
| `eventus-ksp/` | The symbol processor: the event handler check, the generated Koin module and the generated Metro bindings. JVM only, as a build tool. |
| `eventus-compose/` | The Compose helpers: `collectEffects` and `notifyVisibility`. |
| `eventus-test/` | The test helper: `runEventHandlerTest`. |
| `sample-koin/` | The sample app: a task list and a task editor, with navigation, Koin and tests. Its modules are in the table below. |
| `sample-metro/` | The same app with the same modules, wired with Metro. |
| `integration/metro/` | Tests that run the generated Metro bindings through Metro's compiler plugin. Not published. |
| `tooling/ktlint-rules/` | The project's own formatting rules, applied by ktlint next to its standard ones. |

## The sample apps

There are two samples, and they are the same app: `sample-koin` is wired with Koin and `sample-metro`
with Metro, each the way its library documents. Both are split into modules the way a production app
is. No module depends on the implementation of another: only `app` does, because it puts them together.

The table shows `sample-koin`; `sample-metro` has the same modules under its own folder.

| Path | What |
|---|---|
| `sample-koin/core/domain/` | The task, and the repository and connectivity interfaces. Plain Kotlin, with no Android, Compose or dependency injection. |
| `sample-koin/core/data/` | The implementations of the domain's interfaces, and what binds them: a Koin module, or Metro's annotations in `sample-metro`. |
| `sample-koin/core/ui/` | What the screens share: the top bar, the failure reasons, the router log and the string resources. |
| `sample-koin/core/testing/` | The fakes that the tests of several modules share. |
| `sample-koin/feature/tasklist/api/`, `sample-koin/feature/taskeditor/api/` | What other modules may know of a feature: its destination. |
| `sample-koin/feature/tasklist/impl/`, `sample-koin/feature/taskeditor/impl/` | A feature: its events, state and effects, one event handler class per event, the view model, the screen, its wiring and its tests. |
| `sample-koin/app/` | The app's own router, the navigation between the features and the start of Koin. In `sample-metro`, one Metro graph for each platform. |
| `sample-koin/androidApp/`, `sample-koin/desktopApp/` | Launchers. |
| `sample-koin/iosApp/` | The Xcode project of the iOS launcher. |

Run the sample:

```bash
./gradlew :sample-koin:desktopApp:run
```

```bash
./gradlew :sample-koin:androidApp:installDebug
```

For iOS, open `sample-koin/iosApp/iosApp.xcodeproj` in Xcode and run it.

Run `sample-metro` the same way, with `sample-metro` in place of `sample-koin`. On Android it needs
Android 9 (API 28) or later, which Metro's Android helper requires.

Apart from the wiring, the two samples are the same text. The files that differ are listed, with the
reason for each group, in `config/sample-differences.txt`. After changing one sample, make the same
change in the other and run the check that compares them:

```bash
./gradlew checkSamplesMatch
```

Run a sample's tests on every platform:

```bash
./gradlew sampleKoinTest
```

```bash
./gradlew sampleMetroTest
```

Run the library's checks (tests on every platform, lint, coverage and the public API check). The web
tests need Chrome, and the iOS tests need a Mac with an iOS simulator:

```bash
./gradlew :eventus:check
```

Run the checks of the optional artifacts:

```bash
./gradlew :eventus-koin:check :eventus-metro:check :eventus-ksp:check :eventus-compose:check :eventus-test:check :integration:metro:check
```

Format your changes before you send them. This applies the project's style, including its own rules
(a blank line at the start of a class body, after local declarations and before a `return`):

```bash
./gradlew ktlintFormat
```

---

[Back to the README](../README.md#documentation)
