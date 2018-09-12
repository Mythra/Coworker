package io.kungfury.coworkerperf

import io.kungfury.coworker.CoworkerManager
import io.kungfury.coworker.StaticCoworkerConfigurationInput
import io.kungfury.coworker.WorkInserter
import io.kungfury.coworker.dbs.postgres.PgConnectionManager

import java.time.Duration

import kotlin.system.measureTimeMillis

fun main(arguments: Array<String>) {
    val env = System.getenv()
    var threads = 1
    if (env.containsKey("THREADS")) {
        threads = env["THREADS"]!!.toInt()
    }
    val connManager = PgConnectionManager({ toConfigure ->
        toConfigure.jdbcUrl = System.getenv("JDBC_URL")
        toConfigure
    }, null)
    if (arguments.firstOrNull() != null) {
        if (arguments.first() == "queue-jobs") {
            val queueTime = measureTimeMillis {
                for (idx in 1..10) {
                    WorkInserter.InsertBulkWork(connManager, "io.kungfury.coworkerperf.EmptyJob", "", count = 10000)
                }
            }
            System.out.println("Took $queueTime ms. to insert 100,000 jobs.")
            return
        }
        if (arguments.first() == "queue-real-life") {
            val queueTime = measureTimeMillis {
                for (idx in 1..2) {
                    if (idx % 2 == 0) {
                        WorkInserter.InsertBulkWork(connManager, "io.kungfury.coworkerperf.RemoteJob", "", count = 100)
                    } else {
                        WorkInserter.InsertBulkWork(connManager, "io.kungfury.coworkerperf.EchoJob", "", count = 100)
                    }
                }
            }
            System.out.println("Took $queueTime ms. to insert 200 jobs.")
            return
        }
        if (arguments.first() == "test-yield-next") {
            WorkInserter.InsertWork(connManager, "io.kungfury.coworkerperf.RemoteJob", "")
            return
        }
    }
    System.out.println("Starting Coworker with: [ $threads ] Threads.")
    val manager = CoworkerManager(connManager, threads, null, StaticCoworkerConfigurationInput(Duration.parse("PT5M"), HashMap()))
    manager.Start()
}
