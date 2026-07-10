package com.dreamyao.nhtranslationupdate;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import com.dreamyao.nhtranslationupdate.update.UpdateService;

public final class UpdateBootstrap {

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile String lastStatus = "尚未运行更新检查";

    private UpdateBootstrap() {}

    public static void run(File gameDirectory, boolean client) {
        if (!STARTED.compareAndSet(false, true)) return;

        try {
            lastStatus = new UpdateService(gameDirectory.toPath(), client).run();
        } catch (Throwable throwable) {
            lastStatus = "更新失败，已保留现有汉化: " + throwable.getMessage();
            NHTranslationUpdate.LOG.error(lastStatus, throwable);
        }
    }

    public static String getLastStatus() {
        return lastStatus;
    }
}
