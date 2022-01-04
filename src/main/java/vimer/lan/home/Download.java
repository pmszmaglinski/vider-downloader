package vimer.lan.home;

import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.io.FileUtil;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class Download extends Thread {

    private static final Logger log = Logger.getLogger(Download.class);

    Map<String, Map<String, Map<String, String>>> configfileMap;
    private String seriesTitle;
    private String seasonNumber;
    private String episodeTitle;
    private String episodeUrl;
    private String downloadStatus;
    private final DownloadCoordinator downloadCoordinator;

    public Download(DownloadCoordinator downloadCoordinator) {
        this.downloadCoordinator = downloadCoordinator;
    }

    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + ": " + downloadCoordinator
                .getNextEpisodeToDownload()
                .toString());

    }

    Download downloadFileFromUrl(String url) throws IOException {
        HttpResponse response = HttpRequest
                .get(url)
                .charset("UTF-8")
                .header("referer", "https://vider.info/")
                .send();

        byte[] rawBytes = response.bodyBytes();
        File mp4File = new File(System.getProperty("user.dir"), "movie.mp4");
        FileUtil.writeBytes(mp4File, rawBytes);

        return this;
    }
}
