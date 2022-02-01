package vider.lan.home;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import org.apache.log4j.Logger;

import java.util.ArrayList;

public class Display extends Thread {   //TODO: Handle next movie download in same thread
                                        //      and finish display when all downloaded

    private static final Logger log = Logger.getLogger(App.class);

    ArrayList<ProgressBarBuilder> progressBarBuilders = new ArrayList<>();
    ArrayList<ProgressBar> progressBars = new ArrayList<>();
    Boolean areProgressBarsBuilded = false;

    private static Display instance = null;

    private Display() {
    }

    public static Display getInstance() {
        Display result = instance;
        if (result == null) {
            instance = new Display();
        }
        return instance;
    }

    void registerProgressBar(ProgressBarBuilder pbb) {
        progressBarBuilders.add(pbb);
        System.out.println("Registered thread for " + pbb);
    }

    @Override
    public void run() {
        while (progressBarBuilders.size() < App.threadNumber) {
            try {
                System.out.println("Waiting for threads to register... " + progressBarBuilders.size() + " -> " + App.threadNumber);
                Thread.currentThread().sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("All threads registered..." + progressBarBuilders);

        try {
            progressBarBuilders.forEach(pbb -> {
                progressBars.add(pbb.build());
            });
            areProgressBarsBuilded = true;
            //System.out.print("\033[H\033[2J");
        } catch (RuntimeException e) {
            log.error(e);
        }

        //System.out.println("Got progress bar for: " + progressBars.get(0).getTaskName() + progressBars.get(0).getMax());

        while (!progressBars.isEmpty()) {

        }

    }

    void updateBar(String episodeTitle, long downloaded) {
        ProgressBar pb = progressBars.stream()
                .filter(progressBar -> episodeTitle.equals(progressBar.getTaskName()))
                .findAny()
                .orElse(null);
        progressBars.get((progressBars.indexOf(pb))).stepTo(downloaded);
    }
}
