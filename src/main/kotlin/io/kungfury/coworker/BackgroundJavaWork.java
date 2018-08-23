package io.kungfury.coworker;

import java.time.Instant;

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
     * @param stage
     * @param strand
     * @param priority
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
