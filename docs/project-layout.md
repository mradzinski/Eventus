# Project layout

| Path | What |
|---|---|
| `eventus/` | The library. All code is in `commonMain`. |
| `eventus/api/` | The recorded public API. `checkKotlinAbi` fails when the code no longer matches it. |
| `eventus-koin/` | The `@EventusKoinModule` annotation, and nothing else. |
| `eventus-ksp/` | The symbol processor: the handler check and the generated Koin module. JVM only, as a build tool. |
| `eventus-compose/` | The Compose helpers: `collectEffects` and `notifyVisibility`. |
| `sample/shared/` | The sample app: a task list and a task editor, with navigation, Koin and tests. One module, layered by package, to keep it short. |
| `sample/androidApp/`, `sample/desktopApp/` | Launchers. |
| `sample/iosApp/` | The Xcode project of the iOS launcher. |
| `tooling/ktlint-rules/` | The project's own formatting rules, applied by ktlint next to its standard ones. |

Run the sample:

```bash
./gradlew :sample:desktopApp:run
```

```bash
./gradlew :sample:androidApp:installDebug
```

For iOS, open `sample/iosApp/iosApp.xcodeproj` in Xcode and run it.

Run the library's checks (tests on every platform, lint, coverage and the public API check). The web
tests need Chrome, and the iOS tests need a Mac with an iOS simulator:

```bash
./gradlew :eventus:check
```

Run the checks of the optional artifacts:

```bash
./gradlew :eventus-koin:check :eventus-ksp:check :eventus-compose:check
```

Format your changes before you send them. This applies the project's style, including its own rules
(a blank line at the start of a class body, after local declarations and before a `return`):

```bash
./gradlew ktlintFormat
```

---

[Back to the README](../README.md#documentation)
