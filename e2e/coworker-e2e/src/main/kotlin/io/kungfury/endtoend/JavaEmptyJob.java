package io.kungfury.endtoend;

import io.kungfury.coworker.BackgroundJavaWork;
import io.kungfury.coworker.WorkGarbage;
import io.kungfury.coworker.dbs.ConnectionManager;

public class JavaEmptyJob extends BackgroundJavaWork {
    public JavaEmptyJob(ConnectionManager _connManager, WorkGarbage garbageHeap, long id, int stage, String strand, int priority) {
        super(garbageHeap, id, stage, strand, priority);
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
            System.err.println("Couldn't finish work!");
            System.err.println(e1.getMessage());
        }
    }
}
