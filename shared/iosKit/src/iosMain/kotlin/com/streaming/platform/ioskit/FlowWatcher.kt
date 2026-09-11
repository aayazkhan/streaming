package com.streaming.platform.ioskit

import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Swift-callable handle to stop observing a [StateFlow] started via [watch]. */
class FlowWatcher internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}

/**
 * Bridges a Kotlin [StateFlow] to a plain Swift closure — Swift can't collect a Flow directly, but
 * a non-suspend callback parameter crosses the Kotlin/Native <-> Swift boundary cleanly. Runs on
 * the main dispatcher so [onChange] can update SwiftUI @Published state directly.
 */
fun <T> watch(flow: StateFlow<T>, onChange: (T) -> Unit): FlowWatcher {
    val scope = MainScope()
    val job = scope.launch {
        flow.collect { onChange(it) }
    }
    return FlowWatcher(job)
}
