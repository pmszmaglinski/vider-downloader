package vimer.lan.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.io.FileUtil;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

class Configuration {

    private static final Logger log = Logger.getLogger(Configuration.class);

    private static Configuration instance = null;

    private static final String viderUrl = "https://vider.info";
    private static String seriesPath = "/dir/+dnv1mm"; // Nie final bo będzie brane z propertasów

    private final static String tesseractDatapath = "/usr/local/Cellar/tesseract/4.1.3/share/tessdata";
    private final static String tesseractLanguage = "eng";
    private final static String tesseractImageDPI = "96";

    private static Map<String, Map<String, Map<String, String>>> configfileMap = new LinkedHashMap<>();
    private final static String configFileName = "configfile.json";
    private final static File configFile = new File(configFileName);

    private Map<String, Map<String, Map<String, String>>> nextEpisodeToDownload;

    public static Configuration getInstance() {
        Configuration result = instance;
        if (result == null) {
            instance = new Configuration();
        }
        return instance;
    }

    private Configuration() {
    }

    public synchronized Configuration initiateDownload() {
        configfileMap = configfileToMap();
        nextEpisodeToDownload = setNextEpisodeToDownload();
        if (nextEpisodeToDownload.isEmpty()) {
            allDownloadedMessage();
        }
        return this;
    }

    public synchronized Map<String, Map<String, Map<String, String>>> getNextEpisodeToDownload() {
        Map<String, Map<String, Map<String, String>>> tempNextEpisodeToDownload = nextEpisodeToDownload;
        updateEpisodeDownloadStatus(tempNextEpisodeToDownload);
        nextEpisodeToDownload = setNextEpisodeToDownload();
        if (nextEpisodeToDownload.isEmpty()) {
            allDownloadedMessage();
        }
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

        createConfigFile(configfileMap);
    }

//    void updateEpisodeDownloadStatus(String seasonNumber, String episodeTitle, String newStatus) {
//        String currentEpisodeDownloadStatus = checkEpisodeDownloadStatus(seasonNumber, episodeTitle);
//
//        if (!currentEpisodeDownloadStatus.equals(newStatus)) {
//            Map<String, Map<String, String>> seasonMap =
//                    (Map<String, Map<String, String>>) configfileMap
//                            .get(seasonNumber);
//
//            seasonMap.get(episodeTitle).replace("downloaded", newStatus);
//            log.info("Updated download status of: " + seasonNumber + " -> " + episodeTitle + " to: " + newStatus);
//
//            Configuration.update(configfileMap);
//        } else {
//            log.info("Status " + newStatus + " for: " + seasonNumber + " -> " + episodeTitle + " already present. Not updating.");
//        }
//    }

    String checkEpisodeDownloadStatus(String seasonNumber, String episodeTitle) {
        Map<String, Map<String, String>> seasonMap =
                (Map<String, Map<String, String>>) configfileMap
                        .get(seasonNumber);

        return seasonMap
                .get(episodeTitle)
                .get("downloaded");
    }

    public static boolean checkIfFileExists() {
        return Configuration.configFile.exists();
    }

    public static void generate() throws TesseractException, IOException {
        Configuration
                .getInstance()
                .getLinks();
        createConfigFile(Configuration.configfileMap);
    }

//    public static Map<String,Object> getConfigurationMap() {
//        return configfileToMap();
//    }

    public static void update(Map<String, Map<String, Map<String, String>>> configMap) {
        createConfigFile(configMap);
    }

    private Configuration getLinks() throws IOException, TesseractException {
        Document seriesDocument;
        String seriesUrl = viderUrl + seriesPath;
        HttpResponse seriesResponse = HttpRequest.get(seriesUrl).send();

        if (seriesResponse.statusCode() == 404) {
            seriesDocument = Jsoup.parse(seriesResponse.toString());
            seriesResponse = fixCaptcha(seriesUrl, seriesDocument, seriesResponse);
        }

        seriesDocument = Jsoup.parse(seriesResponse.toString());
        //TODO: Put series title somewhere else than configfileMap.
        //      Incompatible types - will keep consistant structure
        //      without series title for now.
        //configfileMap.put("title", getSeriesName(seriesDocument));

        Elements seriesElement = seriesDocument.select("p.title > a");
        seriesElement.forEach(season -> {
            String seasonName = season.html();
            String seasonPath = season.attr("href");
            String seasonUrl = viderUrl + seasonPath;
            Map<String, Map<String, String>> seasonMap = new LinkedHashMap<>();

            HttpResponse seasonResponse = HttpRequest.get(seasonUrl).send();
            Document seasonDocument;

            if (seasonResponse.statusCode() == 404) {
                seasonDocument = Jsoup.parse(seasonResponse.toString());
                try {
                    seasonResponse = fixCaptcha(seasonUrl, seasonDocument, seasonResponse);
                } catch (IOException | TesseractException e) {
                    log.error("Failed on getting seasons ! ", e);
                }
            }

            seasonDocument = Jsoup.parse(seasonResponse.toString());
            Elements seasonElement = seasonDocument.select("p.title > a");
            seasonElement.forEach(episode -> {
                String episodeName = episode.html();
                String episodePath = episode.attr("href");
                String episodeIntermediateLink1 = viderUrl + episodePath;
                Map<String, String> episodeMap = new LinkedHashMap<>();

                HttpResponse episodeResponse = HttpRequest.get(episodeIntermediateLink1).send();
                Document episodeDocument;
                if (episodeResponse.statusCode() == 404) {
                    episodeDocument = Jsoup.parse(episodeResponse.toString());
                    try {
                        episodeResponse = fixCaptcha(episodeIntermediateLink1, episodeDocument, episodeResponse);
                    } catch (IOException | TesseractException e) {
                        log.error("Failed on getting episodes ! ", e);
                    }
                }

                episodeDocument = Jsoup.parse(episodeResponse.toString());
                String episodeIntermediateLink2 = getEpisodeIntermediateLink2(episodeDocument);

                episodeResponse = HttpRequest.get(episodeIntermediateLink2)
                        .header("referer", "https://vider.info/")
                        .send();

                if (episodeResponse.statusCode() == 404) {
                    episodeDocument = Jsoup.parse(episodeResponse.toString());
                    try {
                        episodeResponse = fixCaptcha(episodeIntermediateLink2, episodeDocument, episodeResponse);
                    } catch (IOException | TesseractException e) {
                        log.error("Failed on getting episodes final link ! ", e);
                    }
                }
                String episodeDownloadLink = episodeResponse.header("Location");

                episodeMap.put("url", episodeDownloadLink);
                episodeMap.put("downloaded", "false");
                seasonMap.put(episodeName, episodeMap);
                log.info("Finished generating configuration for episode: " + episodeName);
            });

            configfileMap.put(seasonName, seasonMap);
            log.info("Finished generating config for season: " + seasonName);
        });

        return this;
    }

    private String getEpisodeIntermediateLink2(Document doc) {
        return doc.select("link[rel=video_src]")
                .attr("href")
                .replaceAll("^.*file=", "");
    }

    private String getSeriesName(Document doc) {
        return Objects.requireNonNull(doc.select("title").first())
                .text();
    }

    private void addElementsToMap(Document doc, Map<String, String> map) {
        Elements el = doc.select("p.title > a");
        el.forEach(element -> map.put(element.html(), element.attr("href")));
    }

    private static Tesseract getTesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tesseractDatapath);
        tesseract.setLanguage(tesseractLanguage);
        tesseract.setTessVariable("user_defined_dpi", tesseractImageDPI);
        return tesseract;
    }

    private File getCaptchaFile(Document doc) throws IOException {
        String captchaURL = (viderUrl + doc
                .select("div.content-404.centered > form > img")
                .attr("src"));

        HttpResponse captchaResponse = HttpRequest
                .get(captchaURL)
                .charset("UTF-8")
                .header("Content-type", "application/png")
                .send();

        byte[] rawBytes = captchaResponse.bodyBytes();
        File captchaFile = new File(System.getProperty("user.dir"), "captcha.png");
        FileUtil.writeBytes(captchaFile, rawBytes);

        return captchaFile;
    }

    private String getCaptchaCode(File captchaFile) throws TesseractException {
        String captchaCode;
        Tesseract tesseract = getTesseract();
        captchaCode = tesseract.doOCR(captchaFile);

        return captchaCode.trim();
    }

    private HttpResponse sendCaptcha(String url, String captchaCode) {

        return HttpRequest
                .post(url)
                .form("captcha", captchaCode)
                .send();
    }

    private void getResponseHeaders(HttpResponse response) {
        response.headerNames().forEach(header -> System.out.println(header + " -> " + response.header(header)));
    }

    private HttpResponse fixCaptcha(String url, Document doc, HttpResponse response) throws IOException, TesseractException {
        while (response.statusCode() == 404) {
            File captchaFile = getCaptchaFile(doc);
            String captchaCode = getCaptchaCode(captchaFile);
            log.debug("Trying captcha: " + captchaCode);
            response = sendCaptcha(url, captchaCode);
        }

        return response;
    }

    private String map2Json(Map<String, Object> map) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(map);
    }

    private static void createConfigFile(Map<String, Map<String, Map<String, String>>> map) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        try (FileWriter fw = new FileWriter(Configuration.configFileName)) {
            gson.toJson(map, fw);
            log.info("Generated configuration file: " + Configuration.configFileName);
        } catch (IOException e) {
            log.error("Faild to create configuration file ! ", e);
        }
    }

    private static Map<String, Map<String, Map<String, String>>> configfileToMap() {
        Map<String, Map<String, Map<String, String>>> map = null;
        Gson gson = new Gson();
        try (Reader reader = Files.newBufferedReader(Paths.get(Configuration.configFileName))) {
            map = gson.fromJson(reader, Map.class);
        } catch (IOException e) {
            log.error("Problems with converting configfile to map !" + e);
        }
        return map;
    }

    private void allDownloadedMessage() {
        log.info("All episodes download status in " + configFileName + " is set to true.");
        System.exit(0);
    }
}
