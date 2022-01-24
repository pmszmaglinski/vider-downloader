package vider.lan.home;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DownloadCoordinator {

    private static final Logger log = Logger.getLogger(ConfigurationManager.class);
    private static DownloadCoordinator instance = null;

    private static Map<String, Map<String, String>> configfileMap = new LinkedHashMap<>();
    private static String movieTitle;
    private Map<String, Map<String, String>> nextEpisodeToDownload;

    public Integer numberOfEpisodesLeftToDownload = null;

    public static DownloadCoordinator getInstance() {
        DownloadCoordinator result = instance;
        if (result == null) {
            instance = new DownloadCoordinator();
        }
        return instance;
    }

    private DownloadCoordinator() {
    }

    public synchronized DownloadCoordinator initiateDownload() throws IOException {
        configfileMap = ConfigurationManager.configfileToMap();
        movieTitle = ConfigurationManager.movieTitleFileToString();
        setInProgressToFalse();
        numberOfEpisodesLeftToDownload = getNumberOfEpisodesLeftToDownload();
        nextEpisodeToDownload = setNextEpisodeToDownload();

        if (nextEpisodeToDownload.isEmpty()) {
            allDownloadedMessage();
        }
        return this;
    }

    public String getMovieTitle() {
        return movieTitle;
    }

    public synchronized Map<String, Map<String, String>> getNextEpisodeToDownload() {
        if (nextEpisodeToDownload.isEmpty()) {
            allDownloadedMessage();
            return nextEpisodeToDownload;
        }
        Map<String, Map<String, String>> tempNextEpisodeToDownload = nextEpisodeToDownload;
        updateEpisodeDownloadStatus(tempNextEpisodeToDownload, "inProgress");
        nextEpisodeToDownload = setNextEpisodeToDownload();

        return tempNextEpisodeToDownload;
    }

    public void setInProgressToFalse() {
        for (Map.Entry entry : configfileMap.entrySet()) {
            Map<String, String> episodeMap = (Map<String, String>) entry.getValue();
            if (episodeMap.get("downloaded").equals("inProgress")) {
                String episodeTitle = entry.getKey().toString();

                configfileMap
                        .get(episodeTitle)
                        .replace("downloaded", "false");

                log.info("Setting not finished downloads back to false for: " + episodeTitle);
            }
        }
        ConfigurationManager.createConfigFile(configfileMap);
    }

    private Integer getNumberOfEpisodesLeftToDownload() {
        Integer number = Math.toIntExact(configfileMap.values()
                .stream()
                .filter(v -> v.get("downloaded")
                        .equals("false"))
                .count());

        return number;
    }

    private Map<String, Map<String, String>> setNextEpisodeToDownload() {
        Map<String, Map<String, String>> episodeToDownloadFullPathMap = new LinkedHashMap<>();

        for (Map.Entry entry : configfileMap.entrySet()) {
            Map<String, String> episodeMap = (Map<String, String>) entry.getValue();

            if (episodeMap.get("downloaded").equals("false")) {

                String episodeTitle = entry.getKey().toString();
                String episodeUrl = episodeMap.get("url");
                String downloadStatus = episodeMap.get("downloaded");

                Map<String, String> nextEpisodeToDownloadMap = new LinkedHashMap<>();

                nextEpisodeToDownloadMap.put("url", episodeUrl);
                nextEpisodeToDownloadMap.put("downloaded", downloadStatus);
                episodeToDownloadFullPathMap.put(episodeTitle, nextEpisodeToDownloadMap);

                return episodeToDownloadFullPathMap;
            }
        }
        return episodeToDownloadFullPathMap;
    }

    void updateEpisodeDownloadStatus(Map<String, Map<String, String>> map, String downloadStatus) {
        String episodeTitle = (String) map.keySet().toArray()[0];

        configfileMap
                .get(episodeTitle)
                .replace("downloaded", downloadStatus);

        ConfigurationManager.createConfigFile(configfileMap);
    }

    private void allDownloadedMessage() {
        log.info("Nothing left to download in " + ConfigurationManager.configFileName);
        //System.exit(0);
    }


//    String checkEpisodeDownloadStatus(String seasonNumber, String episodeTitle) {
//        Map<String, Map<String, String>> seasonMap =
//                (Map<String, Map<String, String>>) configfileMap
//                        .get(seasonNumber);
//
//        return seasonMap
//                .get(episodeTitle)
//                .get("downloaded");
//    }
}
