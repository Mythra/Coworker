package io.kungfury.coworker

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking

fun <T> AwaitValue(value: Deferred<T>) = runBlocking { value.await() }
