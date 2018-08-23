package io.kungfury.coworkerperf

import io.kungfury.coworker.dbs.ConnectionManager
import io.kungfury.coworker.BackgroundKotlinWork

/**
 * A job that just echos out.
 */
class EchoJob(
    private val connectionManager: ConnectionManager,
    id: Long,
    stage: Int,
    strand: String,
    priority: Int
) : BackgroundKotlinWork(id, stage, strand, priority) {
    override fun serializeState(): String = ""

    override suspend fun Work(state: String) {
        if (state.isEmpty()) {
            System.out.println("Hello World!")
        } else {
            System.out.println(state)
        }
        finishWork()
    }
}
