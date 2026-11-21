# Changelog

What changed in each version of Eventus, newest first. Versions follow
[semantic versioning](https://semver.org).

## 1.0.0-rc01 (not released yet)

Initial release, as three artifacts:

- `eventus`: the library.
- `eventus-ksp`: an optional symbol processor. It fails the build when an event has no handler class or
  more than one, and it can generate the Koin module of a module's handlers.
- `eventus-koin`: the annotation that asks `eventus-ksp` for that Koin module.
