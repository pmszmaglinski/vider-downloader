package vider.lan.home;

import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.io.FileUtil;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class Download extends Thread {

    private static final Logger log = Logger.getLogger(Download.class);

    Map<String, Map<String, String>> episodeConfigfileMap;
    private String seriesTitle;
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
        this.seriesTitle = downloadCoordinator.getSeriesTitle();

        while (!this.episodeConfigfileMap.isEmpty()) {
            this.episodeTitle = (String) episodeConfigfileMap.keySet().toArray()[0];
            this.episodeUrl = episodeConfigfileMap.get(episodeTitle).get("url");
            this.downloadStatus = episodeConfigfileMap.get(episodeTitle).get("downloaded");

            log.info(Download.currentThread().getName() + " - Downloading: " + this.episodeTitle);

            try {
                downloadFileFromUrl(episodeTitle, episodeUrl);
                log.info("Successfully downloaded: " + " " + episodeTitle);
                downloadCoordinator.updateEpisodeDownloadStatus(episodeConfigfileMap, "true");
            } catch (IOException e) {
                log.info("Download failed for: " + episodeTitle);
                downloadCoordinator.updateEpisodeDownloadStatus(episodeConfigfileMap, "false");
                e.printStackTrace();
            }
            this.episodeConfigfileMap = downloadCoordinator.getNextEpisodeToDownload();
        }
    }

    Download downloadFileFromUrl(String episodeTitle, String url) throws IOException {
        String downloadDirectory = System.getProperty("user.home") +
                File.separator +
                "ViderDownloader" +
                File.separator +
                seriesTitle;

        File folder = new File(downloadDirectory);
        if (!folder.exists()) {
            if (folder.mkdirs()) {
                log.info("Folder was created: " + downloadDirectory);
            }
        }

        HttpResponse response = HttpRequest
                .get(url)
                .charset("UTF-8")
                .header("referer", "https://vider.info/")
                .send();

        byte[] rawBytes = response.bodyBytes();
        File mp4File = new File(downloadDirectory, episodeTitle + ".mp4");
        FileUtil.writeBytes(mp4File, rawBytes);

        return this;
    }
}
