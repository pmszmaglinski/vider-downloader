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

    public Download(DownloadCoordinator downloadCoordinator) {
        this.downloadCoordinator = downloadCoordinator;
    }

    @Override
    public void run() {
        this.episodeConfigfileMap = downloadCoordinator.getNextEpisodeToDownload();
        this.movieTitle = downloadCoordinator.getMovieTitle();

        while (!this.episodeConfigfileMap.isEmpty()) {
            this.episodeTitle = (String) episodeConfigfileMap.keySet().toArray()[0];
            this.episodeUrl = episodeConfigfileMap.get(episodeTitle).get("url");
            this.downloadStatus = episodeConfigfileMap.get(episodeTitle).get("downloaded");

            log.info(Download.currentThread().getName() + " - Downloading: " + this.episodeTitle);

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
    }

    Download downloadFileFromUrl(String episodeTitle, String url) throws IOException, InterruptedException {
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

        File mp4File = new File(downloadDirectory, episodeTitle + ".mp4");
        URL downloadUrl = new URL(url);

        HttpURLConnection http = (HttpURLConnection) downloadUrl.openConnection();
        http.setRequestProperty("referer", "https://vider.info/");
        double fileSize = (double) http.getContentLengthLong();
        BufferedInputStream in = new BufferedInputStream(http.getInputStream());
        FileOutputStream fos = new FileOutputStream(mp4File);
        BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);

        final int BUFFER_SIZE = 16384;
        byte[] buffer = new byte[BUFFER_SIZE];
        double downloaded = 0;
        int read = 0;

        ProgressBarBuilder pbb = new ProgressBarBuilder()
                .setTaskName(episodeTitle)
                .setInitialMax((long) fileSize)
                .setUnit("MiB", 1048576)
                .showSpeed();

        Display display = Display.getInstance();
        display.registerProgressBar(pbb);
        while (!display.areProgressBarsBuilded) {
            System.out.println("Waiting for progress bar to build...");
            Thread.sleep(1000);
        }

        while ((read = in.read(buffer, 0, BUFFER_SIZE)) >= 0) {
            bout.write(buffer, 0, read);
            downloaded += read;
            display.updateBar(episodeTitle, (long) downloaded);
        }
        bout.close();
        in.close();

        return this;
    }
}
