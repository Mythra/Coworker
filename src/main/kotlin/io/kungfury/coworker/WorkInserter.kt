package io.kungfury.coworker

import com.jsoniter.output.JsonStream

import io.kungfury.coworker.dbs.ConnectionManager
import io.kungfury.coworker.dbs.ConnectionType
import io.kungfury.coworker.dbs.Marginalia
import io.kungfury.coworker.dbs.TextSafety
import io.kungfury.coworker.internal.states.DelayedLambdaState
import io.kungfury.coworker.internal.states.HandleAsyncFunctorState

import kotlinx.coroutines.runBlocking

import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.sql.Timestamp
import java.time.Instant

object WorkInserter {
    /**
     * Inserts a piece of work into the DB.
     *
     * @param connectionManager
     *  The connection manager to use.
     * @param workName
     *  The name of the work to insert. This should be the name of the class relative to the "work" package.
     * @param workState
     *  The state of the work to queue with.
     * @param strand
     *  The strand this pice of work is in.
     * @param runAt
     *  The instant to run the piece of work at.
     * @param priority
     *  The priority of this piece of work. Defaults to 100.
     */
    @JvmStatic
    fun InsertWork(
        connectionManager: ConnectionManager,
        workName: String,
        workState: String,
        strand: String = "default",
        runAt: Instant = Instant.now(),
        priority: Int = 100
    ): Long {
        return runBlocking {
            when (connectionManager.CONNECTION_TYPE) {
                ConnectionType.POSTGRES -> {
                    connectionManager.executeTransaction({ connection ->
                        val purifiedStrand = TextSafety.EnforceStringPurity(strand, true)
                        val statement = connection.prepareStatement(Marginalia.AddMarginalia(
                            "WorkInserter_InsertWork",
                            "INSERT INTO public.delayed_work (created_at, run_at, stage, strand, priority, work_unique_name, state) VALUES (current_timestamp, ?, 1, ?, ?, ?, ?) RETURNING id"
                        ))
                        statement.setTimestamp(1, Timestamp.from(runAt))
                        statement.setString(2, strand)
                        statement.setInt(3, priority)
                        statement.setString(4, workName)
                        statement.setString(5, workState)
                        val rs = statement.executeQuery()
                        rs.next()
                        val id = rs.getLong(1)

                        connection.createStatement().execute(Marginalia.AddMarginalia(
                            "WorkInserter_Notify",
                            String.format("NOTIFY workers, '%s'", "$id;$priority;${runAt.epochSecond};1;$purifiedStrand")
                        ))

                        id
                    }, true)
                }
            }
        }
    }

    fun Any.serializeToBytes(): ByteArray = ByteArrayOutputStream().use {
        it -> ObjectOutputStream(it).writeObject(this); it
    }.toByteArray()

    @JvmStatic
    @Throws(IllegalStateException::class)
    fun HandleAsynchronously(
        connectionManager: ConnectionManager,
        functor: (Array<com.jsoniter.any.Any>) -> Unit,
        parameters: Array<Any>,
        strand: String = "default",
        runAt: Instant = Instant.now(),
        priority: Int = 100
    ): Long {
        val jobState: HandleAsyncFunctorState
        if (!(functor is Serializable)) {
            throw IllegalStateException("Functor must be serializable.")
        }
        val serializedObj = functor.serializeToBytes()

        jobState = HandleAsyncFunctorState()
        jobState.args = parameters.map {
            com.jsoniter.any.Any.wrap(it)
        }.toTypedArray()
        jobState.isJava = false
        jobState.methodState = DelayedLambdaState()
        jobState.methodState.serializedClosure = serializedObj
        jobState.version = 1

        return InsertWork(
            connectionManager,
            "io.kungfury.coworker.internal.AsyncFunctorRunner",
            JsonStream.serialize(jobState),
            strand,
            runAt,
            priority
        )
    }

    @JvmStatic
    @Throws(java.lang.IllegalStateException::class)
    fun HandleAsynchronouslyJava(
        connectionManager: ConnectionManager,
        functor: java.util.function.Function<Array<com.jsoniter.any.Any>, Void>,
        parameters: Array<com.jsoniter.any.Any>,
        strand: String = "default",
        runAt: Instant = Instant.now(),
        priority: Int = 100
    ): Long {
        val jobState: HandleAsyncFunctorState
        if (!(functor is Serializable)) {
            throw java.lang.IllegalStateException("Functor is not serializable!")
        }
        val serializedObj = functor.serializeToBytes()

        jobState = HandleAsyncFunctorState()
        jobState.args = parameters
        jobState.isJava = true
        jobState.methodState = DelayedLambdaState()
        jobState.methodState.serializedClosure = serializedObj
        jobState.version = 1

        return InsertWork(
            connectionManager,
            "io.kungfury.coworker.internal.AsyncFunctorRunner",
            JsonStream.serialize(jobState),
            strand,
            runAt,
            priority
        )
    }

    @JvmStatic
    fun InsertBulkWork(
        connectionManager: ConnectionManager,
        workName: String,
        workState: String,
        strand: String = "default",
        runAt: Instant = Instant.now(),
        priority: Int = 100,
        count: Int = 1
    ) {
        runBlocking {
            when (connectionManager.CONNECTION_TYPE) {
                ConnectionType.POSTGRES -> {
                    connectionManager.executeTransaction { connection ->
                        val purifiedStrand = TextSafety.EnforceStringPurity(strand, true)
                        val statement = connection.prepareStatement(Marginalia.AddMarginalia(
                            "WorkInserter_InsertBulkWork",
                            "INSERT INTO public.delayed_work (created_at, run_at, stage, strand, priority, work_unique_name, state) VALUES (current_timestamp, ?, 1, ?, ?, ?, ?) RETURNING id"
                        ))
                        statement.setTimestamp(1, Timestamp.from(runAt))
                        statement.setString(2, strand)
                        statement.setInt(3, priority)
                        statement.setString(4, workName)
                        statement.setString(5, workState)
                        for (idx in 1..count) {
                            val rs = statement.executeQuery()
                            rs.next()
                            val id = rs.getLong(1)

                            connection.createStatement().execute(Marginalia.AddMarginalia(
                                "WorkInserter_BulkNotify",
                                String.format("NOTIFY workers, '%s'", "$id;$priority;${runAt.epochSecond};1;$purifiedStrand")
                            ))
                        }

                        connection.commit()
                    }
                }
            }
        }
    }
}
