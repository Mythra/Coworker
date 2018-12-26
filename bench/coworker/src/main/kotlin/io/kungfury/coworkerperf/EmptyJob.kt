package io.kungfury.coworkerperf

import io.kungfury.coworker.BackgroundKotlinWork
import io.kungfury.coworker.WorkGarbage
import io.kungfury.coworker.dbs.ConnectionManager

/**
 * Represents an empty job that just marks itself as done.
 */
class EmptyJob(
    private val connectionManager: ConnectionManager,
    garbageHeap: WorkGarbage,
    id: Long,
    stage: Int,
    strand: String,
    priority: Int
) : BackgroundKotlinWork(garbageHeap, id, stage, strand, priority) {
    override fun serializeState(): String = ""

    override suspend fun Work(state: String) {
        finishWork()
    }
}