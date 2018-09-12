package io.kungfury.coworker

import java.time.temporal.TemporalAmount

/**
 * A static configuration input that never changes.
 */
class StaticCoworkerConfigurationInput(
    private val checkWorkEvery: TemporalAmount,
    nstrand: Map<String, Int>
) : CoworkerConfigurationInput {
    private val nstrandMap: Map<Pair<String, Regex>, Int> = nstrand.map { entry ->
        Pair(entry.key, Regex.fromLiteral(entry.key)) to entry.value
    }.toMap()

    override fun getWorkCheckDelay(): TemporalAmount {
        return checkWorkEvery
    }

    override fun getNstrandMap(): Map<Pair<String, Regex>, Int> {
        return nstrandMap
    }
}