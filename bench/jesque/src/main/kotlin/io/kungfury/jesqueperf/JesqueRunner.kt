package io.kungfury.jesqueperf

import net.greghaines.jesque.ConfigBuilder
import net.greghaines.jesque.Job
import net.greghaines.jesque.client.ClientPoolImpl
import net.greghaines.jesque.utils.PoolUtils
import net.greghaines.jesque.worker.MapBasedJobFactory
import net.greghaines.jesque.worker.WorkerImpl

import java.util.Arrays

import kotlin.system.measureTimeMillis

fun main(arguments: Array<String>) {
    val env = System.getenv()
    var threads = 1
    if (env.containsKey("THREADS")) {
        threads = env["THREADS"]!!.toInt()
    }
    val config = if (env.containsKey("REDIS_HOST")) {
        val builder = ConfigBuilder()
        builder.withHost(env.get("REDIS_HOST"))
        builder.withPort(env.get("REDIS_PORT")!!.toInt())
        builder.build()
    } else {
        ConfigBuilder.getDefaultConfig()
    }
    val jesqueClientPool = ClientPoolImpl(config, PoolUtils.createJedisPool(config))

    if (arguments.firstOrNull() != null) {
        if (arguments.first() == "queue-jobs") {
            val queueTime = measureTimeMillis {
                for (idx in 1..10) {
                    for (jdx in 1..10000) {
                        val job = Job("io.kungfury.jesqueperf.EmptyJob")
                        jesqueClientPool.enqueue("work", job)
                    }
                }
            }
            System.out.println("Took $queueTime ms to insert 100,000 jobs.")
            return
        }
        if (arguments.first() == "queue-real-life") {
            val queueTime = measureTimeMillis {
                for (idx in 1..10) {
                    if (idx % 2 == 0) {
                        for (jdx in 1..10000) {
                            val job = Job("io.kungfury.jesqueperf.RemoteJob")
                            jesqueClientPool.enqueue("work", job)
                        }
                    } else {
                        for (jdx in 1..10000) {
                            val job = Job("io.kungfury.jesqueperf.EchoJob")
                            jesqueClientPool.enqueue("work", job)
                        }
                    }
                }
            }
            System.out.println("Took $queueTime ms. to insert 100,000 jobs.")
            return
        }
    }

    System.out.println("Starting Jesque with: [ $threads ] Threads.")

    for (idx in 1..threads) {
        val worker = WorkerImpl(config, Arrays.asList("work"), MapBasedJobFactory(mapOf(Pair("io.kungfury.jesqueperf.EmptyJob", EmptyJob::class.java), Pair("io.kungfury.jesqueperf.EchoJob", EchoJob::class.java), Pair("io.kungfury.jesqueperf.RemoteJob", RemoteJob::class.java))))
        val wthread = Thread(worker)
        wthread.start()
    }
    while (true) {}
}