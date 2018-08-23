package io.kungfury.jesqueperf;

public class EchoJob implements Runnable {
    public EchoJob() {
    }

    @Override
    public void run() {
        System.out.println("Hello World!");
    }
}
