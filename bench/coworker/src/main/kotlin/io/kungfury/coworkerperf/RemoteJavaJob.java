package io.kungfury.coworkerperf;

import io.kungfury.coworker.BackgroundJavaWork;
import io.kungfury.coworker.dbs.ConnectionManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.time.Instant;

public class RemoteJavaJob extends BackgroundJavaWork {
    private ConnectionManager conn;

    public RemoteJavaJob(ConnectionManager connManager, long id, int stage, String strand, int priority) {
        super(id, stage, strand, priority);

        this.conn = connManager;
    }

    @Override
    public String getSerializedState() {
        return "";
    }

    @Override
    public void Work(String state) {
        try {
            switch (this.getStage()) {
                case 1:
                    OkHttpClient ok = new OkHttpClient();
                    Request req = (new Request.Builder()).url("https://google.com").build();
                    Response resp = ok.newCall(req).execute();
                    System.out.println(resp.body().string());
                    System.out.println(Instant.now().getEpochSecond());
                    this.yieldNextStage(this.conn, Instant.now().plusSeconds(60));
                case 2:
                    System.out.println("Fake remote java job completed!");
                    this.finishWork();
                default:
                    System.err.println("Unknown Stage!");
                    this.failWork(this.conn, "RemoteJavaJob", "Unknown Stage!");
            }
        } catch (Exception e1) {
            // Determine what to do.
        }
    }
}
