package io.kungfury.coworker

import java.time.temporal.TemporalAmount

interface CoworkerConfigurationInput {
    /**
     * Get the temporal amount for how often we should scan the table for missed notification.
     */
    fun getWorkCheckDelay(): TemporalAmount

    /**
     * Get the map of nstrands.
     */
    fun getNstrandMap(): Map<Pair<String, Regex>, Int>
}
