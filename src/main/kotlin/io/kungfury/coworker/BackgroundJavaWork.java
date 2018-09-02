package io.kungfury.coworker;

import java.time.Instant;

/**
 * A class that wraps must of the "boilerplate" around creating a java job.
 */
public abstract class BackgroundJavaWork implements DelayedJavaWork {
    private long _id;
    private int _stage;
    private String _strand;
    private int _priority;
    private Instant startTime;

    /**
     * Initialize a BackgroundJavaWork provides an easier wrapper around DelayedJavaWork.
     *
     * @param id
     *  The ID of this job.
     * @param stage
     *  The stage of this job.
     * @param strand
     *  The strand this job is in.
     * @param priority
     *  The priority of this job.
     */
    public BackgroundJavaWork(long id, int stage, String strand, int priority) {
        this._id = id;
        this._stage = stage;
        this._strand = strand;
        this._priority = priority;
        this.startTime = Instant.now();
    }

    @Override
    public long getID() {
        return this._id;
    }

    @Override
    public int getStage() {
        return this._stage;
    }

    @Override
    public int getPriority() {
        return this._priority;
    }

    @Override
    public String getStrand() {
        return this._strand;
    }

    @Override
    public Instant getStartTime() {
        return startTime;
    }

    /**
     * Provides the actual implementation of the work.
     *
     * @param state
     *  The state of the work.
     */
    public abstract void Work(String state);

    @Override
    public Runnable WorkPart(String state) {
        return () -> Work(state);
    }
}
