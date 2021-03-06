package io.kungfury.coworker.internal

/**
 * A class that shows a serialized piece of work.
 */
data class DescribedWork(
    val workUniqueName: String,
    val workId: Long,
    val Stage: Int,
    val Strand: String,
    val State: String,
    val Priority: Int,
    val QueuedAt: Long
)