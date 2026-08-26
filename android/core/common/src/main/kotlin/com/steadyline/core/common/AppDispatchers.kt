package com.steadyline.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The only place a dispatcher is named.
 *
 * Writing `Dispatchers.IO` inline hides where work happens and makes tests
 * depend on real threads. Everything takes this instead.
 */
interface AppDispatchers {
    /** UI. Compose reads state here. */
    val main: CoroutineDispatcher

    /** UI, without the dispatch when already on the main thread. */
    val mainImmediate: CoroutineDispatcher

    /** Blocking work: disk, DataStore, network. */
    val io: CoroutineDispatcher

    /** CPU work: JSON parsing, scheduling, sorting. */
    val default: CoroutineDispatcher
}

/**
 * Backed by the standard pools. No custom Executor: [Dispatchers.IO] is already
 * an elastic shared pool, and a bespoke one would compete with it for memory.
 */
class DefaultAppDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
