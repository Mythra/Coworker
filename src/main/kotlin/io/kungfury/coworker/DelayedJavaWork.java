package io.kungfury.coworker;

import io.kungfury.coworker.dbs.ConnectionManager;
import io.kungfury.coworker.dbs.Marginalia;
import io.kungfury.coworker.utils.NetworkUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public interface DelayedJavaWork {
    /**
     * @return
     *  The work garbage heap.
     */
    WorkGarbage getGarbageHeap();

    /**
     * @return
     *  The ID of this delayed piece of work.
     */
    long getID();

    /**
     * @return
     *  The stage this piece of work is on is on.
     */
    int getStage();

    /**
     * @return
     *   The priority of this piece of work.
     */
    int getPriority();

    /**
     * @return
     *  The strand of this piece of work.
     */
    String getStrand();

    /**
     * @return
     *  The time this piece of work started.
     */
    Instant getStartTime();

    /**
     * @return
     *  The current representation of the state as a serialized string.
     */
    String getSerializedState();

    /**
     * In charge of working a specific part of the work.
     *
     * @param state
     *  The state.
     * @return
     *  A runnable that exits when it's completed.
     */
    Runnable WorkPart(String state);

    /**
     * Yields the piece of work to higher priority work, moving to an arbitrary stage.
     *
     * @param connectionManager
     *  The postgres connection manager.
     * @param stage
     *  The stage to yield to.
     *
     * <p>
     *  yieldStage should be called when "part" of a piece of work is done, but not all of it.
     * </p>
     */
    default void yieldStage(ConnectionManager connectionManager, int stage) throws Exception {
        yieldStage(connectionManager, stage, Instant.now());
    }

    /**
     * Yields the piece of work to higher priority work, moving to an arbitrary stage.
     *
     * @param connectionManager
     *  The postgres connection manager.
     * @param stage
     *  The stage to yield to.
     * @param runAt
     *  A time to enforce the next stage doesn't run before.
     *
     * <p>
     *  yieldStage should be called when "part" of a piece of work is done, but not all of it.
     * </p>
     */
    default void yieldStage(ConnectionManager connectionManager, int stage, Instant runAt) throws Exception {
        switch (connectionManager.getCONNECTION_TYPE()) {
            case POSTGRES:
                connectionManager.executeTransaction((connection -> {
                    try {
                        PreparedStatement stmt = connection.prepareStatement(Marginalia.INSTANCE.AddMarginalia(
                            "DelayedJavaWork",
                            "UPDATE public.delayed_work SET run_at = ?, stage = ?, state = ?, locked_by = NULL WHERE id = ?"
                        ));
                        stmt.setTimestamp(1, Timestamp.from(runAt));
                        stmt.setInt(2, stage);
                        stmt.setString(3, this.getSerializedState());
                        stmt.setLong(4, this.getID());
                        stmt.execute();

                        connection.createStatement().execute(Marginalia.INSTANCE.AddMarginalia(
                            "DelayedJavaWork_yieldStage_notify",
                            String.format("NOTIFY workers, '%d;%d;%d;%d;%s'", this.getID(), this.getPriority(), runAt.getEpochSecond(), stage, this.getStrand())
                        ));

                        return true;
                    } catch (SQLException e1) {
                        throw new UncheckedIOException(new IOException(e1.getNextException()));
                    }
                }), true);
        }
    }

    /**
     * Yield the next stage of the current piece of work.
     *
     * @param connectionManager
     *  The connection manager to the database.
     */
    default void yieldNextStage(ConnectionManager connectionManager) throws Exception {
        yieldNextStage(connectionManager, Instant.now());
    }

    /**
     * Yield the next stage of the current piece of work, providing a time to wait before running.
     *
     * @param connectionManager
     *  The connection manager to the database.
     * @param runAt
     *  The instant you want the piece of work to be workable again.
     */
    default void yieldNextStage(ConnectionManager connectionManager, Instant runAt) throws Exception {
        yieldStage(connectionManager, this.getStage() + 1, runAt);
    }

    /**
     * Reyield the current stage of the piece of work, useful for retrying.
     *
     * @param connectionManager
     *  The connection manager for the postgres instance.
     */
    default void yieldCurrentStage(ConnectionManager connectionManager) throws Exception {
        yieldCurrentStage(connectionManager, Instant.now());
    }

    /**
     * Reyield the current stage of the piece of work, useful for retrying providing a time it can run again.
     *
     * @param connectionManager
     *  The connection manager to the database.
     * @param runAt
     *  When the piece of work can be run again.
     */
    default void yieldCurrentStage(ConnectionManager connectionManager, Instant runAt) throws Exception {
        yieldStage(connectionManager, this.getStage(), runAt);
    }

    /**
     * Mark this piece of work as finished.
     *
     */
    default void finishWork() throws Exception {
        getGarbageHeap().AddJobToCleanupHeap(this.getID());
    }

    /**
     * Mark a piece of work as failed.
     *
     * @param connectionManager
     *  The postgres connection manager.
     * @param workName
     *  The unique name of this piece of work.
     * @param failedMsg
     *  The failure message.
     */
    default void failWork(ConnectionManager connectionManager, String workName, String failedMsg) throws Exception {
        switch (connectionManager.getCONNECTION_TYPE()) {
            case POSTGRES:
                connectionManager.executeTransaction((connection -> {
                    try {
                        PreparedStatement stmt = connection.prepareStatement(Marginalia.INSTANCE.AddMarginalia(
                            "DelayedJavaWork_failWorkDelete",
                            "DELETE FROM public.delayed_work WHERE id = ?"
                        ));
                        stmt.setLong(1, this.getID());
                        stmt.execute();

                        PreparedStatement createFailed = connection.prepareStatement(Marginalia.INSTANCE.AddMarginalia(
                            "DelayedJavaWork_failWorkCreate",
                            "INSERT INTO public.failed_work (id, failed_at, stage, work_unique_name, failed_msg, state, run_by) VALUES ( ?, current_timestamp, ? , ?, ?, ?, ? )"
                        ));
                        createFailed.setLong(1, this.getID());
                        createFailed.setInt(2, this.getStage());
                        createFailed.setString(3, workName);
                        createFailed.setString(4, failedMsg);
                        createFailed.setString(5, this.getSerializedState());
                        createFailed.setString(6, NetworkUtils.INSTANCE.getLocalHostLANAddress().getHostAddress());
                        createFailed.execute();

                        return true;
                    } catch (SQLException e1) {
                        throw new UncheckedIOException(new IOException(e1.getNextException()));
                    }
                }), true);
        }
    }
}
