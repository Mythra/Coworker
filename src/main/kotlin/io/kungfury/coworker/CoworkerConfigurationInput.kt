package io.kungfury.coworker

import java.time.Duration
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

    /**
     * Get the allowed failure limit for reading from the stream.
     *
     * @return
     *  The failure limit for listening to the channel.
     */
    fun getFailureLimit(): Short

    /**
     * Get the max garbage heap size (buffer deletes).
     *
     * @return
     *  The maximum garbage heap size.
     */
    fun getGarbageMaxSize(): Int

    /**
     * Get the duration between garbage cleanings regardless of size.
     *
     * @return
     *  The duration of garbage clean size.
     */
    fun getCleanDuration(): Duration

    /**
     * Get the total amount of time to sleep when no jobs have been found to prevent CPU
     * busy spins.
     */
    fun getCheckSleepDuration(): Duration
}
