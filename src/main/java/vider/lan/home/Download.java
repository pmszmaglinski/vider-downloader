package vider.lan.home;


import org.apache.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import me.tongfei.progressbar.*;

public final class Download extends Thread {

    private static final Logger log = Logger.getLogger(Download.class);

    Map<String, Map<String, String>> episodeConfigfileMap;
    private String movieTitle;
    private String episodeTitle;
    private String episodeUrl;
    private String downloadStatus;
    private final DownloadCoordinator downloadCoordinator;
    private final ProgressBarBuilderQueue progressBarBuilderQueue;
    private final Display display;
    static int runningThreadsNumber = App.threadNumber;

    public Download(DownloadCoordinator downloadCoordinator, ProgressBarBuilderQueue progressBarBuilderQueue, Display display) {
        this.downloadCoordinator = downloadCoordinator;
        this.progressBarBuilderQueue = progressBarBuilderQueue;
        this.display = display;
    }

    @Override
    public void run() {
        this.episodeConfigfileMap = downloadCoordinator.getNextEpisodeToDownload();
        this.movieTitle = downloadCoordinator.getMovieTitle();

        while (!this.episodeConfigfileMap.isEmpty()) {
            this.episodeTitle = (String) episodeConfigfileMap.keySet().toArray()[0];
            this.episodeUrl = episodeConfigfileMap.get(episodeTitle).get("url");
            this.downloadStatus = episodeConfigfileMap.get(episodeTitle).get("downloaded");

            try {
                downloadFileFromUrl(episodeTitle, episodeUrl);
                log.info("Successfully downloaded: " + " " + episodeTitle);
                downloadCoordinator.updateEpisodeDownloadStatus(episodeConfigfileMap, "true");
            } catch (IOException | InterruptedException e) {
                log.info("Download failed for: " + episodeTitle);
                downloadCoordinator.updateEpisodeDownloadStatus(episodeConfigfileMap, "false");
                e.printStackTrace();
            }
            this.episodeConfigfileMap = downloadCoordinator.getNextEpisodeToDownload();
        }

        runningThreadsNumber--;
        log.info("Running download threads left: " + runningThreadsNumber);
        if (runningThreadsNumber == 0) {
            Display.downloadFinished = true;
            synchronized (display.addedProgressBarBuilderToQueue) {
                display.addedProgressBarBuilderToQueue.notify();
            }
        }
    }

    void downloadFileFromUrl(String episodeTitle, String url) throws IOException, InterruptedException {

        String downloadDirectory = prepareDownloadDirectory();

        File mp4File = new File(downloadDirectory, episodeTitle + ".mp4");
        URL downloadUrl = new URL(url);

        HttpURLConnection http = (HttpURLConnection) downloadUrl.openConnection();
        http.setRequestProperty("referer", "https://vider.info/");
        http.setRequestProperty("User-Agent", "curl/7.64.1");

        double fileSize = (double) http.getContentLengthLong();
        BufferedInputStream in = new BufferedInputStream(http.getInputStream());
        FileOutputStream fos = new FileOutputStream(mp4File);
        BufferedOutputStream bout = new BufferedOutputStream(fos, 1024); //TODO: how this buffer size relates to BUFFER_SIZE variable ?

        final int BUFFER_SIZE = 16384;
        byte[] buffer = new byte[BUFFER_SIZE];
        double downloaded = 0;
        int read = 0;

        ProgressBarBuilder pbb = new ProgressBarBuilder()
                .setTaskName(episodeTitle)
                .setInitialMax((long) fileSize)
                .setUnit("MiB", 1048576)
                .showSpeed();

        progressBarBuilderQueue.progressBarBuilders.add(pbb);
        synchronized (display.addedProgressBarBuilderToQueue) {
            display.addedProgressBarBuilderToQueue.notify();
        }

        synchronized (display.progressBarBuilded) {
            while (display.getProgressBarByEpisodeTitle(episodeTitle) == null) {
                display.progressBarBuilded.wait();
            }
        }

        while ((read = in.read(buffer, 0, BUFFER_SIZE)) >= 0) {
            bout.write(buffer, 0, read);
            downloaded += read;

            display.updateBar(episodeTitle, (long) downloaded);
        }

        bout.close();
        in.close();
        display.closeBar(episodeTitle);
    }

    String prepareDownloadDirectory() {
        String downloadDirectory = System.getProperty("user.home") +
                File.separator +
                "ViderDownloader" +
                File.separator +
                movieTitle;

        File folder = new File(downloadDirectory);
        if (!folder.exists()) {
            if (folder.mkdirs()) {
                log.info("Folder was created: " + downloadDirectory);
            }
        }

        return downloadDirectory;
    }
}
