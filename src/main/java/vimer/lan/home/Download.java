package vimer.lan.home;

import com.google.gson.Gson;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.io.FileUtil;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class Download {

    private static final Logger log = Logger.getLogger(Download.class);

    Map<String, Object> configfileMap;
    private String seriesTitle;
    private String seasonNumber;
    private String episodeTitle;
    private String episodeUrl;
    private String downloadStatus;

    public Download() {
        this.configfileMap = configfileToMap();
    }

    Download getNextEpisodeToDownload() {
        for (Map.Entry<String, Object> entry : configfileMap.entrySet()) {
            if (!entry.getKey().equals("title")) {
                Map<String, Object> seasonMap = (Map<String, Object>) entry.getValue();
                for (Map.Entry<String, Object> entry1 : seasonMap.entrySet()) {
                    Map<String, String> episodeMap = (Map<String, String>) entry1.getValue();
                    if (episodeMap.get("downloaded").equals("false")) {
                        this.seriesTitle = configfileMap.get("title").toString();
                        this.seasonNumber = entry.getKey();
                        this.episodeTitle = entry1.getKey();
                        this.episodeUrl = episodeMap.get("url");
                        this.downloadStatus = episodeMap.get("downloaded");
                        return this;
                    }
                }
            }
        }
        return this;
    }

    String checkEpisodeDownloadStatus() {
        Map<String, Map<String, String>> seasonMap =
                (Map<String, Map<String, String>>) configfileMap
                        .get(this.seasonNumber);

        return seasonMap
                .get(this.episodeTitle)
                .get("downloaded");
    }

    String checkEpisodeDownloadStatus(String seasonNumber, String episodeTitle) {
        Map<String, Map<String, String>> seasonMap =
                (Map<String, Map<String, String>>) configfileMap
                        .get(seasonNumber);

        return seasonMap
                .get(episodeTitle)
                .get("downloaded");
    }

    void updateEpisodeDownloadStatus(String seasonNumber, String episodeTitle, String newStatus) {
        String currentEpisodeDownloadStatus = checkEpisodeDownloadStatus(seasonNumber, episodeTitle);

        if (!currentEpisodeDownloadStatus.equals(newStatus)) {
            Map<String, Map<String, String>> seasonMap =
                    (Map<String, Map<String, String>>) configfileMap
                            .get(seasonNumber);

            seasonMap.get(episodeTitle).replace("downloaded", newStatus);
            log.info("Updated download status of: " + seasonNumber + " -> " + episodeTitle + " to: " + newStatus);

            // TODO: Przerobić na statyczne metody z singeltona
            // TODO: - jedna instancja konfiguracji (może wyizolować) obsługiwana przez różne wątki
            Configuration configuration = new Configuration()
                    .createConfigFile(configfileMap, Configuration.configFileName);
        } else {
            log.info("Status " + newStatus + " for: " + seasonNumber + " -> " + episodeTitle + " already present. Not updating.");
        }
    }

    Download loopOverMap() { // TODO: To remove - will not iterate
        // TODO: thread will pick first episode with {"downloaded":"false"} status
        String seriesTitle = configfileMap.get("title").toString();
        System.out.println(seriesTitle);
        configfileMap.forEach((k, v) -> {
            if (!k.equals("title")) {
                String seasonNumber = k;
                Map<String, Object> seasonMap = (Map<String, Object>) v;
                seasonMap.forEach((x, y) -> {
                    String episodeTitle = x;
                    Map<String, String> episodeMap = (Map<String, String>) y;
                    String episodeUrl = episodeMap.get("url");
                    String episodeDownloadStatus = episodeMap.get("downloaded");
                    //downloadFileFromUrl(seriesTitle, seasonNumber, episodeTitle, episodeUrl,episodeDownloadStatus);
                });
            }
        });
        return this;
    }

    Download downloadFileFromUrl() throws IOException {
        HttpResponse response = HttpRequest
                .get("https://stream2.vider.info/video_dummy2/eyJmaWxlSUQiOiIxMTM3MjIiLCJjaGVja3N1bV9pZCI6Ijc0MzAyOCIsInByZW1pdW0iOmZhbHNlLCJsaW1pdF9yYXRlX2FmdGVyIjozOTB9.mp4?uid=0")
                .charset("UTF-8")
                .header("referer", "https://vider.info/")
                .send();

        byte[] rawBytes = response.bodyBytes();
        File mp4File = new File(System.getProperty("user.dir"), "movie.mp4");
        FileUtil.writeBytes(mp4File, rawBytes);

        return this;
    }

    private Map<String, Object> configfileToMap() {
        Map<String, Object> map = null;
        Gson gson = new Gson();
        try (Reader reader = Files.newBufferedReader(Paths.get(Configuration.configFileName))) {
            map = gson.fromJson(reader, Map.class);
        } catch (IOException e) {
            log.error("Problems with converting configfile to map !" + e);
        }
        return map;
    }
}
