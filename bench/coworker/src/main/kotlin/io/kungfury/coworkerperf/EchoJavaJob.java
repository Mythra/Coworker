package io.kungfury.coworkerperf;

import io.kungfury.coworker.BackgroundJavaWork;
import io.kungfury.coworker.WorkGarbage;
import io.kungfury.coworker.dbs.ConnectionManager;

public class EchoJavaJob extends BackgroundJavaWork {
    public EchoJavaJob(ConnectionManager _connManager, WorkGarbage garbageHeap, long id, int stage, String strand, int priority) {
        super(garbageHeap, id, stage, strand, priority);
    }

    @Override
    public String getSerializedState() {
        return "";
    }

    @Override
    public void Work(String state) {
        try {
            System.out.println("Hello World");
            this.finishWork();
        } catch (Exception e1) {
            // Determine what to do.
        }
    }
}
