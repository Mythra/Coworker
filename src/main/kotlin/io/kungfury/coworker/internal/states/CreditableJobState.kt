package io.kungfury.coworker.internal.states

/**
 * Internal local state of a creditable job.
 *
 * @param rolling_avg
 *  The rolling average of this creditable job so far.
 * @param total_jobs
 *  The total amount of jobs seen so far.
 * @param in_use
 *  How many credits are in use.
 */
data class CreditableJobState(val rolling_avg: Long, val total_jobs: Long, val in_use: Long)
