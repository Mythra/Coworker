package io.kungfury.coworker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import java.time.Instant

import kotlin.coroutines.CoroutineContext

/**
 * A Wrapper around DelayedKotlinWork implementing a lot of the boilerplate for you.
 *
 * @param garbageHeap
 *  The work garbage heap.
 * @param id
 *  The ID of this piece of work.
 * @param stage
 *  The stage this pice of work is running in.
 * @param strand
 *  The strand of this piece of work.
 * @param priority
 *  The priority of this piece of work.
 */
abstract class BackgroundKotlinWork(
    garbageHeap: WorkGarbage,
    id: Long,
    stage: Int,
    strand: String,
    priority: Int
) : DelayedKotlinWork {
    override val Id: Long = id
    override val Stage: Int = stage
    override val Priority: Int = priority
    override val Strand: String = strand
    override val GarbageHeap: WorkGarbage = garbageHeap
    private val startTime = Instant.now()
    override fun getStartTime(): Instant = startTime

    /**
     * Performs the actual work of the piece of work. Implemented by the user.
     *
     * @param state
     *  The state of the piece of work.
     */
    abstract suspend fun Work(state: String)

    override fun WorkPart(state: String, coroutineContext: CoroutineContext): Job {
        return CoroutineScope(coroutineContext).launch {
            Work(state)
        }
    }
}
