package io.kungfury.coworker.internal

import io.kungfury.coworker.DelayedKotlinWork

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.runBlocking

/**
 * ThinWrapper to run an actual piece of work for kotlin.
 *
 * @param describedWork
 *  The described work to run to run.
 * @param work
 *  The actual instance of the class.
 */
class CoworkerKotlinRunnable(
    private val describedWork: DescribedWork,
    private val work: DelayedKotlinWork
) : Runnable {
    override fun run() {
        runBlocking {
            CoroutineName("CoworkerKotlinRunnable - ${work.Id}")
            work.WorkPart(describedWork.State, this.coroutineContext)
        }
    }
}