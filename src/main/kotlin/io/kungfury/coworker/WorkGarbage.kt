package io.kungfury.coworker

import io.kungfury.coworker.dbs.ConnectionManager
import io.kungfury.coworker.dbs.ConnectionType
import io.kungfury.coworker.dbs.Marginalia

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
class WorkGarbage(config: CoworkerConfigurationInput) {
    private val lock = ReentrantLock()
    private var lastCleaned = Instant.now()
    private val CLEANUP_INTERVAL: Duration = config.getCleanDuration()
    private val MAX_JOBS: Int = config.getGarbageMaxSize()
    private val garbageHeap = ArrayList<Long>(MAX_JOBS)

    /**
     * Add a job to the cleanup heap.
     */
    fun AddJobToCleanupHeap(id: Long) {
        lock.lock()
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
        if (Instant.now().minus(CLEANUP_INTERVAL).isBefore(lastCleaned)) {
            return true
        }
        if (garbageHeap.size >= MAX_JOBS) {
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
                connectionManager.executeTransaction { connection ->
                    val statement = connection.prepareStatement(Marginalia.AddMarginalia(
                        "WorkGarbage_Cleanup",
                        "DELETE FROM public.delayed_work WHERE id = ANY(?)"
                    ))
                    lock.lock()
                    statement.setArray(1, connection.createArrayOf("BIGINT", garbageHeap.toTypedArray()))
                    garbageHeap.clear()
                    lock.unlock()
                    statement.execute()
                    connection.commit()
                    lastCleaned = Instant.now()
                }
            }
        }
    }
}
