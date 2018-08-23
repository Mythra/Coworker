package io.kungfury.jesqueperf;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class RemoteJob implements Runnable {
    public RemoteJob() {
    }

    @Override
    public void run() {
        try {
            OkHttpClient client = new OkHttpClient();
            Request req = (new Request.Builder()).url("https://google.com").build();
            Response resp = client.newCall(req).execute();
            System.out.println(resp.body().string());
        } catch (IOException expected) { }
        try {
            Thread.sleep(60000);
        } catch (InterruptedException expected) { }
    }
}
