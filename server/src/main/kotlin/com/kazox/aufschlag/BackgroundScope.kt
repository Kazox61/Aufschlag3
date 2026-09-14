package com.kazox.aufschlag

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Scope for work that outlives the request that started it — mail sends and the token
 * maintenance loop. One instance per application, drained on shutdown so a SIGTERM right
 * after "password reset requested" doesn't silently drop the mail. Supervisor: one failed
 * send never cancels its siblings.
 */
class BackgroundScope : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO

    /** Waits up to [timeout] for in-flight work to finish, then cancels whatever is left.
     *  Long-running loops must be cancelled by their owner before this is called. */
    suspend fun shutdown(timeout: Duration) {
        withTimeoutOrNull(timeout) { job.children.toList().forEach { it.join() } }
        job.cancel()
    }
}
