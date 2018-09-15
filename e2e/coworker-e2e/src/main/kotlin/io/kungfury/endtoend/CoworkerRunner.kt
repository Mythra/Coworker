package io.kungfury.endtoend

import com.jsoniter.any.Any

import io.kungfury.coworker.CoworkerManager
import io.kungfury.coworker.StaticCoworkerConfigurationInput
import io.kungfury.coworker.WorkInserter
import io.kungfury.coworker.dbs.postgres.PgConnectionManager

import java.time.Duration

fun EmptyMethodReference(_args: Array<Any>) {
    System.err.println("Empty method")
}

fun MethodReferenceWithArgs(args: Array<Any>) {
    System.err.println("Method reference: ${args.first().toInt()}")
}

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
            WorkInserter.InsertWork(connManager, "io.kungfury.endtoend.EmptyJob", "")

            // Test no argument lambda.
            WorkInserter.HandleAsynchronously(connManager, {
                System.err.println("Hewwo")
            }, arrayOf())
            // Test w/ argument lambda.
            WorkInserter.HandleAsynchronously(connManager, { args: Array<Any> ->
                val value = args.first()
                System.err.println(value.toInt())
            }, arrayOf(10))
            // Test w/ Method Reference no args.
            WorkInserter.HandleAsynchronously(connManager, ::EmptyMethodReference, arrayOf())
            // Test w/ Method Reference and args
            WorkInserter.HandleAsynchronously(connManager, ::MethodReferenceWithArgs, arrayOf(10))

            // Test with Java:
            JavaTester.TestQueueJava(connManager)

            return
        }
    }
    System.out.println("Starting Coworker with: [ $threads ] Threads.")
    val manager = CoworkerManager(
        connManager,
        threads,
        null,
        StaticCoworkerConfigurationInput(Duration.parse("PT5M"), HashMap())
    )
    manager.Start()
}
