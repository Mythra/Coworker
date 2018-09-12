# Dynamic Configuration #

Dynamic Configuration is a possibility with coworker, however it requires
the users of Coworker to implement it themselves. The reason for this is
so users can implement the refreshing/data source they get their config
from in anyway possible. Coworker won't lock you in. Ideally you'd use
something like [Consul](https://consul.io) since it's also available for
service checking, but you _don't need to_ use it. You can use
whatever your heart desires.

Implementing a dynamic configuration is as simple as having a class implement
the `CoworkerConfigurationInput` interface. For example our static configuration
class that doesn't provide refreshing looks like:

```kotlin
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
```

Ideally you'd do something similar for your dynamic configuration:

```kotlin
package com.mycompany.myapp

import java.time.temporal.TemporalAmount

class DynamicCoworkerConfigurationInput(
    private var checkWorkEvery: TemporalAmount,
    nstrand: Map<String, Int>
) : CoworkerConfigurationInput {
    private var nstrandMap: Map<Pair<String, Regex>, Int> = nstrand.map { entry ->
        Pair(entry.key, Regex.fromLiteral(entry.key)) to entry.value
    }.toMap()

    fun setCheckWorkEvery(new: TemporalAmount) {
      this.checkWorkEvery = new
    }

    fun setNStrandMap(map: Map<String, Int>) {
        this.nstrandMap = map.map { entry ->
            Pair(entry.key, Regex.fromLiteral(entry.key)) to entry.value
        }.toMap()
    }

    override fun getWorkCheckDelay(): TemporalAmount {
        return checkWorkEvery
    }

    override fun getNstrandMap(): Map<Pair<String, Regex>, Int> {
        return nstrandMap
    }
}
```

The important "gotcha" here is you want to return as quickly as possible from your two
overriden functions. E.g. by returning a cached variable like we do in the example. This
is because we call these values ***a lot.*** If you perform your refresh inside these methods
you ***will*** cause slowdown for coworker. Please never do this.
