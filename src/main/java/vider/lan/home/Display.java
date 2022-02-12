package vider.lan.home;

import me.tongfei.progressbar.ProgressBar;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Display extends Thread {

    private static final Logger log = Logger.getLogger(Display.class);

    ProgressBarBuilderQueue progressBarBuilderQueue;
    ArrayList<ProgressBar> progressBars = new ArrayList<>();

    final ReentrantLock lock = new ReentrantLock(true);
    final Condition progressBarBuilded = lock.newCondition();
    final Condition addedProgressBarBuilderToQueue = lock.newCondition();

    static volatile boolean downloadFinished = false;

    Display(ProgressBarBuilderQueue progressBarBuilderQueue) {
        this.progressBarBuilderQueue = progressBarBuilderQueue;
    }

    ProgressBar getProgressBarByEpisodeTitle(String episodeTitle) throws InterruptedException {
        synchronized (progressBarBuilded) {
            return progressBars.stream()
                    .filter(progressBar -> episodeTitle.equals(progressBar.getTaskName()))
                    .findAny()
                    .orElse(null);
        }
    }

    synchronized void updateBar(String episodeTitle, long downloaded) throws InterruptedException {
        ProgressBar pb = getProgressBarByEpisodeTitle(episodeTitle);
        try {
            progressBars.get((progressBars.indexOf(pb))).stepTo(downloaded);
        } catch (IndexOutOfBoundsException e) {
            throw new RuntimeException("No episode title in progress bar found !");
        }
    }

    synchronized void closeBar(String episodeTitle) throws InterruptedException {
        ProgressBar pb = getProgressBarByEpisodeTitle(episodeTitle);
        progressBars.get((progressBars.indexOf(pb))).close();
    }

    @Override
    public void run() {
        while (!downloadFinished) {
            while (progressBarBuilderQueue.progressBarBuilders.size() == 0 && Download.runningThreadsNumber > 0) {
                synchronized (addedProgressBarBuilderToQueue) {
                    try {
                        addedProgressBarBuilderToQueue.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            synchronized (progressBarBuilded) {
                if (progressBarBuilderQueue.progressBarBuilders.size() > 0) {
                    try {
                        progressBars.add(progressBarBuilderQueue.progressBarBuilders.remove().build());
                    } finally {
                        progressBarBuilded.notifyAll();
                    }
                }
            }
        }
    }
}
