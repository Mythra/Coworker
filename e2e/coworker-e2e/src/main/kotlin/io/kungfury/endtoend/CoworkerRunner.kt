package io.kungfury.endtoend

import com.jsoniter.any.Any

import io.kungfury.coworker.CoworkerManager
import io.kungfury.coworker.StaticCoworkerConfigurationInput
import io.kungfury.coworker.WorkInserter
import io.kungfury.coworker.dbs.Marginalia
import io.kungfury.coworker.dbs.postgres.PgConnectionManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

import kotlinx.coroutines.runBlocking

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
    }, null, null)

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

    System.out.println("Starting Coworker with: [ $threads ] Threads.")
    val manager = CoworkerManager(
        connManager,
        threads,
        null,
        null,
        StaticCoworkerConfigurationInput(Duration.parse("PT5M"), HashMap())
    )
    val queued = runBlocking {
        connManager.executeTransaction { conn ->
            val queryStatement = conn.createStatement()
            val rs = queryStatement.executeQuery(Marginalia.AddMarginalia("first_working_check", "SELECT COUNT(*) AS working_count FROM public.delayed_work"))
            rs.next()
            val jobsrunning = rs.getLong("working_count")
            rs.close()
            queryStatement.close()
            jobsrunning
        }
    }
    if (queued == 0L) {
        System.err.println("No jobs were inserted!")
        System.exit(2)
    }
    val job = GlobalScope.launch { manager.Start() }

    loop@ while (true) {
        val pair = runBlocking {
            connManager.executeTransaction { conn ->
                val queryDelayedStatement = conn.createStatement()
                val rs = queryDelayedStatement.executeQuery(
                    Marginalia.AddMarginalia("working_count_query", "SELECT COUNT(*) AS working_count FROM public.delayed_work")
                )
                rs.next()
                val jobsrunning = rs.getLong("working_count")
                rs.close()
                queryDelayedStatement.close()

                val queryFailedStatement = conn.createStatement()
                val rsFailed = queryFailedStatement.executeQuery(
                    Marginalia.AddMarginalia("failed_query_count", "SELECT COUNT(*) AS working_count FROM public.failed_work")
                )
                rsFailed.next()
                val jobsFailed = rsFailed.getLong("working_count")
                rsFailed.close()
                queryFailedStatement.close()

                conn.commit()

                if (jobsFailed > 0) {
                    Pair(true, true)
                } else if (jobsrunning == 0L) {
                    Pair(true, false)
                } else {
                    Pair(false, false)
                }
            }
        }

        if (pair.second) {
            System.err.println("Job IDS Failed!")
            System.exit(1)
        }
        if (pair.first) {
            break@loop
        }
    }

    System.exit(0)
}
