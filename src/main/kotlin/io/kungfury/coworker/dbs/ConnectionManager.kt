package io.kungfury.coworker.dbs

import kotlinx.coroutines.channels.ReceiveChannel

import java.io.IOException
import java.sql.Connection
import java.util.concurrent.TimeoutException
import java.util.function.Function

/**
 * Describes a connection manager to a database.
 *
 */
interface ConnectionManager {
    /**
     * The timeout time for executing a transaction.
     */
    val TIMEOUT_MS: Long

    /**
     * The type of this connection.
     */
    val CONNECTION_TYPE: ConnectionType

    /**
     * A wrapper around executeTransaction(query, commitOnExit) to provide an easier java interface.
     *
     * @param query
     *  A java function that takes a connection, and returns your result.
     * @throws TimeoutException
     *   If we took too long.
     * @throws IOException
     *   If we failed to connect to the database.
     * @throws IllegalStateException
     *  If there was no exception but no return value.
     * @throws Exception
     *  Will rethrow any exception thrown in the block rolling back the transaction.
     *
     * NOTE: All queries are done in transaction mode. You NEED TO Commit in your query code.
     * NOTE 2: We WILL call rollback if there is an unhandled exception.
     */
    @Throws(TimeoutException::class, IOException::class, IllegalStateException::class)
    fun <T> executeTransaction(query: Function<Connection, T>): T {
        return executeTransaction(query, false)
    }

    /**
     * Executes a query in a transaction, rolling back if an exception is thrown.
     *
     * @param query
     *  A Function that takes a connection, and returns your result.
     * @param commitOnExit
     *  Determines if we should commit the transaction on exit.
     * @throws TimeoutException
     *   If we took too long.
     * @throws IOException
     *   If we failed to connect to the database.
     * @throws IllegalStateException
     *  If there was no exception but no return value.
     * @throws Exception
     *  Will rethrow any exception thrown in the block rolling back the transaction.
     *
     * <p>
     * NOTE: All queries are done in transaction mode.
     * You NEED TO Commit in your query code, unless passing commitOnExit!
     * </p>
     *
     * <p>
     * NOTE 2: We WILL call rollback if any exception is thrown!
     * </p>
     */
    @Throws(TimeoutException::class, IOException::class, IllegalStateException::class)
    fun <T> executeTransaction(query: Function<Connection, T>, commitOnExit: Boolean = false): T

    /**
     * A wrapper around executeTransaction (query, commitOnExit) to provide a nicer kotlin interface.
     *
     * @param query
     *  A Function that takes a connection, and returns your result.
     * @throws TimeoutException
     *   If we took too long.
     * @throws IOException
     *   If we failed to connect to the database.
     * @throws IllegalStateException
     *  If there was no exception but no return value.
     * @throws Exception
     *  Will rethrow any exception thrown in the block rolling back the transaction.
     *
     * NOTE: All queries are done in transaction mode. You NEED TO Commit in your query code.
     * NOTE 2: We WILL call rollback if there is an unhandled exception.
     */
    @Throws(TimeoutException::class, IOException::class, IllegalStateException::class)
    suspend fun <T> executeTransaction(query: suspend (Connection) -> T): T {
        return executeTransaction(query, false)
    }

    /**
     * Executes a query in a transaction, rolling back if an exception is thrown.
     *
     * @param query
     *  A Function that takes a connection, and returns your result.
     * @param commitOnExit
     *  Determines if we should commit the transaction on exit.
     * @throws Any
     *  Will rethrow any exception thrown in the block rolling back the transaction
     *
     * <p>
     * NOTE: All queries are done in transaction mode.
     * You NEED TO Commit in your query code, unless passing commitOnExit!
     * </p>
     *
     * <p>
     * NOTE 2: We WILL call rollback if any exception is thrown!
     * </p>
     */
    suspend fun <T> executeTransaction(query: suspend (Connection) -> T, commitOnExit: Boolean = false): T

    /**
     * Starts listening to a a channel on the db, returning a ReceiveChannel for when a message is received.
     *
     * @param channel
     *  The channel name to listen too.
     */
    fun listenToChannel(channel: String): ReceiveChannel<String>
}
