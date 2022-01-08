package vider.lan.home;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DownloadCoordinator {

    private static final Logger log = Logger.getLogger(ConfigurationManager.class);
    private static DownloadCoordinator instance = null;

    private static Map<String, Map<String, Map<String, String>>> configfileMap = new LinkedHashMap<>();
    private static String seriesTitle;
    private Map<String, Map<String, Map<String, String>>> nextEpisodeToDownload;

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
        seriesTitle = ConfigurationManager.seriesTitleFileToString();
        setInProgressToFalse();
        nextEpisodeToDownload = setNextEpisodeToDownload();
        if (nextEpisodeToDownload.isEmpty()) {
            allDownloadedMessage();
        }
        return this;
    }

    public String getSeriesTitle() {
        return seriesTitle;
    }

    public synchronized Map<String, Map<String, Map<String, String>>> getNextEpisodeToDownload() {
        if (nextEpisodeToDownload.isEmpty()) {
            allDownloadedMessage();
        }
        Map<String, Map<String, Map<String, String>>> tempNextEpisodeToDownload = nextEpisodeToDownload;
        updateEpisodeDownloadStatus(tempNextEpisodeToDownload, "inProgress");
        nextEpisodeToDownload = setNextEpisodeToDownload();

        return tempNextEpisodeToDownload;
    }

    private Map<String, Map<String, Map<String, String>>> setNextEpisodeToDownload() {
        Map<String, Map<String, Map<String, String>>> episodeToDownloadFullPathMap = new LinkedHashMap<>();
        for (Map.Entry entry : configfileMap.entrySet()) {
                Map<String, Object> seasonMap = (Map<String, Object>) entry.getValue();
                for (Map.Entry<String, Object> episodesMap : seasonMap.entrySet()) {
                    Map<String, String> episodeMap = (Map<String, String>) episodesMap.getValue();
                    if (episodeMap.get("downloaded").equals("false")) {
                        String seasonNumber = entry.getKey().toString();
                        String episodeTitle = episodesMap.getKey();
                        String episodeUrl = episodeMap.get("url");
                        String downloadStatus = episodeMap.get("downloaded");

                        Map<String, String> nextEpisodeToDownloadMap = new LinkedHashMap<>();
                        Map<String, Map<String, String>> seasonToDownloadEpisodeFromMap = new LinkedHashMap<>();

                        nextEpisodeToDownloadMap.put("url", episodeUrl);
                        nextEpisodeToDownloadMap.put("downloaded", downloadStatus);
                        seasonToDownloadEpisodeFromMap.put(episodeTitle, nextEpisodeToDownloadMap);
                        episodeToDownloadFullPathMap.put(seasonNumber, seasonToDownloadEpisodeFromMap);

                        return episodeToDownloadFullPathMap;
                    }
                }
        }
        return episodeToDownloadFullPathMap;
    }

    void updateEpisodeDownloadStatus(Map<String, Map<String, Map<String, String>>> map, String downloadStatus) {
        String seasonNumber = (String) map.keySet().toArray()[0];
        String episodeTitle = (String) map.get(seasonNumber).keySet().toArray()[0];

        configfileMap.get(seasonNumber)
                .get(episodeTitle)
                .replace("downloaded", downloadStatus);

        ConfigurationManager.createConfigFile(configfileMap);
    }

    public void setInProgressToFalse() {
        for (Map.Entry entry : configfileMap.entrySet()) {
            Map<String, Map<String,String>> seasonMap = (Map<String, Map<String,String>>) entry.getValue();
            for (Map.Entry<String, Map<String,String>> episodesMap : seasonMap.entrySet()) {
                Map<String, String> episodeMap = episodesMap.getValue();
                if (episodeMap.get("downloaded").equals("inProgress")) {
                    String seasonNumber = entry.getKey().toString();
                    String episodeTitle = episodesMap.getKey();

                    configfileMap.get(seasonNumber)
                            .get(episodeTitle)
                            .replace("downloaded", "false");

                    log.info("Setting back failed downloads back to false for: " + seasonNumber + " " + episodeTitle);
                }
            }
            ConfigurationManager.createConfigFile(configfileMap);
        }
    }

    private void allDownloadedMessage() {
        log.info("All episodes download status in " + ConfigurationManager.configFileName + " is set to true.");
        System.exit(0);
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
