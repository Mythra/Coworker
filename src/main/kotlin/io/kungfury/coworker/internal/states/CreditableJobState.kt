package io.kungfury.coworker.internal.states

data class CreditableJobState(val rolling_avg: Long, val total_jobs: Long, val in_use: Long)
