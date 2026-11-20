package io.github.mradzinski.eventus.sample.data

/** One thing to do. */
data class Task(
    val id: Long,
    val title: String,
    val notes: String,
    val done: Boolean,
)
