package vider.lan.home;

import me.tongfei.progressbar.ProgressBarBuilder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ProgressBarBuilderQueue {

    final BlockingQueue<ProgressBarBuilder> progressBarBuilders;

    ProgressBarBuilderQueue(int queueSize) {
        this.progressBarBuilders = new ArrayBlockingQueue<>(queueSize);
    }
}
