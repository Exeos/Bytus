package me.exeos.bytus.api.logger;

import me.exeos.bytus.Bytus;

public class Task {

    private final String task;
    private boolean running = false;

    public Task(String task) {
        this.task = task;
    }

    public Task start() {
        return start("Starting Task: ");
    }

    public Task start(String message) {
        if (running) {
            throw new IllegalStateException("Can't start task while another Task is still running");
        }
        running = true;

        Bytus.instance.logger.info(message);

        return this;
    }

    public void fail() {
        fail("Undefined Error");
    }

    public void fail(String message) {
        if (!running) {
            throw new IllegalStateException("Can't fail task, no task running");
        }
        running = false;

        Bytus.instance.logger.error("Failed Task: " + task + ": " + message);
    }

    public void finish() {
        finish("Finished Task: " + task);
    }

    public void finish(String message) {
        if (!running) {
            throw new IllegalStateException("Can't finish task, no task running");
        }
        running = false;

        Bytus.instance.logger.success(message);
    }
}
