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
    static Boolean areProgressBarsBuilded = false;
    static Boolean newProgressBarRegistered = false;

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

    synchronized void registerProgressBarBuilder(ProgressBarBuilder pbb) {
        progressBarBuilders.add(pbb);
        //log.info("Registered thread for " + pbb);
        newProgressBarRegistered = true;
        areProgressBarsBuilded = false;
    }

    ProgressBar getProgressBarByEpisodeTitle(String episodeTitle) {
        return progressBars.stream()
                .filter(progressBar -> episodeTitle.equals(progressBar.getTaskName()))
                .findAny()
                .orElse(null);
    }

    void updateBar(String episodeTitle, long downloaded) {
        ProgressBar pb = getProgressBarByEpisodeTitle(episodeTitle);
        try {
            progressBars.get((progressBars.indexOf(pb))).stepTo(downloaded);
        } catch (IndexOutOfBoundsException e) {
            throw new RuntimeException("Ni ma indeksu !");
        }
    }

    void closeBar(String episodeTitle) {
        ProgressBar pb = getProgressBarByEpisodeTitle(episodeTitle);
        progressBars.get((progressBars.indexOf(pb))).close();
    }

    void waitForThreadsToRegisterProgressbarBuilders() {
        while (progressBarBuilders.size() < App.threadNumber) {
            try {
                log.info("Waiting for threads to register... " + progressBarBuilders.size() + " -> " + App.threadNumber);
                Thread.currentThread().sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        log.info("All " + App.threadNumber + " threads registered progresbar builders.");
    }

    void buildProgressbars() {
        try {
            progressBarBuilders.forEach(pbb -> {
                progressBars.add(pbb.build());
            });
            areProgressBarsBuilded = true;
            newProgressBarRegistered = false;
            //System.out.print("\033[H\033[2J");
        } catch (RuntimeException e) {
            log.error(e);
        }
    }

    @Override
    public void run() {

        waitForThreadsToRegisterProgressbarBuilders();

        buildProgressbars();


        while (true) { //todo: needs to finish somehow

            if (newProgressBarRegistered) {
                //System.out.println("New progress");
                progressBarBuilders.get(progressBarBuilders.size() - 1).build();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                newProgressBarRegistered = false;
                areProgressBarsBuilded = true;

            }
            //System.out.println("Got progress bar for: " + progressBars.get(0).getTaskName() + progressBars.get(0).getMax());


        }
    }

}
