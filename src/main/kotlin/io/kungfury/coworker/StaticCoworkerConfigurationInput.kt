package io.kungfury.coworker

import java.time.Duration
import java.time.temporal.TemporalAmount

/**
 * A static configuration input that never changes.
 */
@UseExperimental(ExperimentalUnsignedTypes::class)
class StaticCoworkerConfigurationInput(
    private val checkWorkEvery: TemporalAmount,
    nstrand: Map<String, Int>,
    private val failureLimit: Short = 3,
    private val garbageHeapSize: Int = 1000,
    private val cleanupDuration: Duration = Duration.ofSeconds(30)
) : CoworkerConfigurationInput {
    private val nstrandMap: Map<Pair<String, Regex>, Int> = nstrand.map { entry ->
        Pair(entry.key, Regex.fromLiteral(entry.key)) to entry.value
    }.toMap()

    override fun getWorkCheckDelay(): TemporalAmount = checkWorkEvery
    override fun getNstrandMap(): Map<Pair<String, Regex>, Int> = nstrandMap
    override fun getFailureLimit(): Short = failureLimit
    override fun getGarbageMaxSize(): Int = garbageHeapSize
    override fun getCleanDuration(): Duration = cleanupDuration
}