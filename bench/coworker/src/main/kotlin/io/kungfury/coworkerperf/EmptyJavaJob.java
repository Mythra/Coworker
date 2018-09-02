package io.kungfury.coworkerperf;

import io.kungfury.coworker.BackgroundJavaWork;
import io.kungfury.coworker.dbs.ConnectionManager;

public class EmptyJavaJob extends BackgroundJavaWork {
    public EmptyJavaJob(ConnectionManager _connManager, long id, int stage, String strand, int priority) {
        super(id, stage, strand, priority);
    }

    @Override
    public String getSerializedState() {
        return "";
    }

    @Override
    public void Work(String state) {
        try {
            this.finishWork();
        } catch (Exception e1) {
            // Determine what to do.
        }
    }
}
