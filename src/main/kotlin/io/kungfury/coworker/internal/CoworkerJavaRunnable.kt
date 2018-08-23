package io.kungfury.coworker.internal

import io.kungfury.coworker.DelayedJavaWork

/**
 * ThinWrapper to run an actual piece of work for java.
 *
 * @param describedWork
 *  The described work to run to run.
 * @param work
 *  The actual instance of the class.
 */
class CoworkerJavaRunnable(
    private val describedWork: DescribedWork,
    private val work: DelayedJavaWork
) : Runnable {
    override fun run() {
        // Don't use runBlocking since the job will call it with connectionManager.
        work.WorkPart(describedWork.State)
    }
}