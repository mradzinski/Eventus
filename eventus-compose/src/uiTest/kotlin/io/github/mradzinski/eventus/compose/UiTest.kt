package io.github.mradzinski.eventus.compose

/**
 * The base of every test class that composes something. It is empty everywhere but on Android, where it
 * carries the test runner that gives a test Android's own code to run with. A test class only has to
 * extend it.
 */
expect abstract class UiTest()
