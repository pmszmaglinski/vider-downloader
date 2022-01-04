package vimer.lan.home;

import org.apache.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DownloadCoordinator {

    private static final Logger log = Logger.getLogger(ConfigurationManager.class);
    private static DownloadCoordinator instance = null;

    private static Map<String, Map<String, Map<String, String>>> configfileMap = new LinkedHashMap<>();
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

    public synchronized DownloadCoordinator initiateDownload() {
        configfileMap = ConfigurationManager.configfileToMap();
        nextEpisodeToDownload = setNextEpisodeToDownload();
        if (nextEpisodeToDownload.isEmpty()) {
            allDownloadedMessage();
        }
        return this;
    }

    public synchronized Map<String, Map<String, Map<String, String>>> getNextEpisodeToDownload() {
        if (nextEpisodeToDownload.isEmpty()) {
            allDownloadedMessage();
        }
        Map<String, Map<String, Map<String, String>>> tempNextEpisodeToDownload = nextEpisodeToDownload;
        updateEpisodeDownloadStatus(tempNextEpisodeToDownload);
        nextEpisodeToDownload = setNextEpisodeToDownload();

        return tempNextEpisodeToDownload;
    }

    private Map<String, Map<String, Map<String, String>>> setNextEpisodeToDownload() {
        Map<String, Map<String, Map<String, String>>> episodeToDownloadFullPathMap = new LinkedHashMap<>();
        for (Map.Entry entry : configfileMap.entrySet()) {
            if (!entry.getKey().equals("title")) {
                Map<String, Object> seasonMap = (Map<String, Object>) entry.getValue();
                for (Map.Entry<String, Object> entry1 : seasonMap.entrySet()) {
                    Map<String, String> episodeMap = (Map<String, String>) entry1.getValue();
                    if (episodeMap.get("downloaded").equals("false")) {
                        String seasonNumber = entry.getKey().toString();
                        String episodeTitle = entry1.getKey();
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
        }
        return episodeToDownloadFullPathMap;
    }

    void updateEpisodeDownloadStatus(Map<String, Map<String, Map<String, String>>> map) {
        String seasonNumber = null;
        String episodeTitle = null;
        for (Map.Entry entry : map.entrySet()) {
            Map<String, Object> inLoopSeasonMap = (Map<String, Object>) entry.getValue();
            for (Map.Entry<String, Object> entry1 : inLoopSeasonMap.entrySet()) {
                seasonNumber = entry.getKey().toString();
                episodeTitle = entry1.getKey();
            }
        }

        configfileMap.get(seasonNumber)
                .get(episodeTitle)
                .replace("downloaded", "inProgress");

        ConfigurationManager.createConfigFile(configfileMap);
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
