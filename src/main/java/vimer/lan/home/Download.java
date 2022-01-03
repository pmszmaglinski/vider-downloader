package vimer.lan.home;

import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.io.FileUtil;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class Download extends Thread {

    private static final Logger log = Logger.getLogger(Download.class);

    Map<String, Map<String, Map<String, String>>> configfileMap;
    private String seriesTitle;
    private String seasonNumber;
    private String episodeTitle;
    private String episodeUrl;
    private String downloadStatus;
    private Configuration configuration;

    public Download(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + ": " + configuration
                .getNextEpisodeToDownload()
                .toString());

        //this.configfileMap = configuration.getNextEpisodeToDownload();
        //System.out.println(Download.currentThread().getName() + ": " + this.configfileMap.toString());
        //execute();
    }

    private void execute() {
//        while (getNextEpisodeToDownload()) {
//
//            log.info(Download.currentThread().getName() + ": " + "Downloading: " + this.episodeTitle);
//
////            updateEpisodeDownloadStatus(this.seasonNumber, this.episodeTitle, "inProgress");
//
//            try {
//                Thread.sleep(5000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
////
//            updateEpisodeDownloadStatus(this.seasonNumber, this.episodeTitle, "true");
////            System.out.println(Download.currentThread().getName() + ": Finishing");
//        }
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

//    Download loopOverMap() {
//        String seriesTitle = configfileMap.get("title").toString();
//        System.out.println(seriesTitle);
//        configfileMap.forEach((k, v) -> {
//            if (!k.equals("title")) {
//                String seasonNumber = k;
//                Map<String, Object> seasonMap = (Map<String, Object>) v;
//                seasonMap.forEach((x, y) -> {
//                    String episodeTitle = x;
//                    Map<String, String> episodeMap = (Map<String, String>) y;
//                    String episodeUrl = episodeMap.get("url");
//                    String episodeDownloadStatus = episodeMap.get("downloaded");
//                    //downloadFileFromUrl(seriesTitle, seasonNumber, episodeTitle, episodeUrl,episodeDownloadStatus);
//                });
//            }
//        });
//        return this;
//    }
}
