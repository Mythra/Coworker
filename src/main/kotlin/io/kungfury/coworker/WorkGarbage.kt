package io.kungfury.coworker

import io.kungfury.coworker.dbs.ConnectionManager
import io.kungfury.coworker.dbs.ConnectionType
import io.kungfury.coworker.dbs.Marginalia

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

/**
 * WorkGarbage is a landing ground for `finishWork`.
 *
 * WorkGarbage is meant to act as a buffer for DELETEs in the database. It does this to be nice to your DB.
 * WorkGarbage gets passed into your job, and is what gets called under the hood when you finish a piece of particular
 * work. WorkGarbage is not in the path for fail work.
 */
class WorkGarbage(config: CoworkerConfigurationInput, metricRegistry: MeterRegistry) {
    private val lock = ReentrantLock()
    private var lastCleaned = Instant.now()
    private val cleanupInterval: Duration = config.getCleanDuration()
    private val maxJobs: Int = config.getGarbageMaxSize()
    private val garbageHeap = ArrayList<Long>(maxJobs)

    private val receivedCleanup = metricRegistry.counter("coworker.garbage.heap.received", Tags.empty())
    private val actuallyCleaned = metricRegistry.counter("coworker.garbage.heap.cleaned", Tags.empty())

    /**
     * Add a job to the cleanup heap.
     */
    fun AddJobToCleanupHeap(id: Long) {
        lock.lock()
        receivedCleanup.increment()
        garbageHeap.add(id)
        lock.unlock()
    }

    /**
     * Determines if a job id is scheduled for delete.
     */
    fun isScheduledForDelete(id: Long): Boolean {
        lock.lock()
        val retV = garbageHeap.contains(id)
        lock.unlock()
        return retV
    }

    /**
     * If we should run a cleanup.
     */
    fun ShouldCleanup(): Boolean {
        if (garbageHeap.isEmpty()) {
            return false
        }
        if (Instant.now().minus(cleanupInterval).isBefore(lastCleaned)) {
            return true
        }
        if (garbageHeap.size >= maxJobs) {
            return true
        }
        return false
    }

    /**
     * Cleanup jobs that have been finished.
     */
    suspend fun Cleanup(connectionManager: ConnectionManager) {
        when (connectionManager.CONNECTION_TYPE) {
            ConnectionType.POSTGRES -> {
                val array = garbageHeap.toTypedArray()
                withContext(Dispatchers.IO) {
                    connectionManager.executeTransaction { connection ->
                        val statement = connection.prepareStatement(Marginalia.AddMarginalia(
                            "WorkGarbage_Cleanup",
                            "DELETE FROM public.delayed_work WHERE id = ANY(?)"
                        ))
                        lock.lock()
                        statement.setArray(1, connection.createArrayOf("BIGINT", array))
                        garbageHeap.clear()
                        lock.unlock()
                        statement.execute()
                        connection.commit()
                        lastCleaned = Instant.now()
                    }
                }
                actuallyCleaned.increment(array.size.toDouble())
            }
        }
    }
}
