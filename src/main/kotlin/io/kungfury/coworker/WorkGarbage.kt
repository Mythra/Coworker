package io.kungfury.coworker

import io.kungfury.coworker.dbs.ConnectionManager
import io.kungfury.coworker.dbs.ConnectionType
import io.kungfury.coworker.dbs.Marginalia

import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

/**
 * WorkGarbage is a landing ground for `finishWork`.
 *
 * Instead of making a whole bunch of individual calls to the database for DELETEs we decide to batch them up
 * and send them every 1k jobs, or every minute. This increases the speed of jobs worked considerably.
 */
object WorkGarbage {
    private val lock = ReentrantLock()
    private var lastCleaned = Instant.now()
    private val MAX_JOBS: Int

    init {
        val env = System.getenv()
        if (env.containsKey("COWORKER_HEAP_MAX_SIZE")) {
            val potentialInt = env.get("COWORKER_HEAP_MAX_SIZE")!!.toIntOrNull()
            if (potentialInt != null) {
                MAX_JOBS = potentialInt
            } else {
                MAX_JOBS = 1000
            }
        } else {
            MAX_JOBS = 1000
        }
    }
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
        if (Instant.now().epochSecond - 30 > lastCleaned.epochSecond) {
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
