package vider.lan.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.io.FileUtil;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class ConfigurationManager {

    private static final Logger log = Logger.getLogger(ConfigurationManager.class);

    private static ConfigurationManager instance = null;

    private static final String viderUrl = "https://vider.info";
    private String seriesPath = null;

    private final static String tesseractDatapath = System.getenv("TESSDATA_PREFIX");
    private final static String tesseractLanguage = "eng";
    private final static String tesseractImageDPI = "96";

    private final static Map<String, Map<String, Map<String, String>>> configfileMap = new LinkedHashMap<>();
    final static String configFileName = "configfile.json";
    private final static File configFile = new File(configFileName);

    private final static String seriesInfoFileName = "series-info";
    private final static File seriesInfoFile = new File(seriesInfoFileName);

    public static ConfigurationManager getInstance() {
        ConfigurationManager result = instance;
        if (result == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }

    private ConfigurationManager() {
    }

    public static boolean checkIfFileExists() {
        return configFile.exists();
    }

    public ConfigurationManager setSeriesPath(String link) {
        this.seriesPath = link;
        return this;
    }

    public void generate() throws TesseractException, IOException {
        getLinks();
        createConfigFile(configfileMap);
    }

    private void getLinks() throws IOException, TesseractException {
        Document seriesDocument;
        String seriesUrl = viderUrl + seriesPath;
        HttpResponse seriesResponse = HttpRequest.get(seriesUrl).send();

        if (seriesResponse.statusCode() == 404) {
            seriesDocument = Jsoup.parse(seriesResponse.toString());
            seriesResponse = fixCaptcha(seriesUrl, seriesDocument, seriesResponse);
        }

        seriesDocument = Jsoup.parse(seriesResponse.toString());
        createSeriesInfoFile(seriesInfoFile, getSeriesTitle(seriesDocument));
        //configfileMap.put("title", getSeriesTitle(seriesDocument));

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
                        .header("Range", "bytes=0-0")
                        .send();

                if (episodeResponse.statusCode() == 404) {
                    episodeDocument = Jsoup.parse(episodeResponse.toString());
                    try {
                        episodeResponse = fixCaptcha(episodeIntermediateLink2, episodeDocument, episodeResponse);
                    } catch (IOException | TesseractException e) {
                        log.error("Failed on getting episodes final link ! ", e);
                    }
                }

                String episodeDownloadLink = null;
                if (episodeResponse.statusCode() == 206 && episodeResponse.contentType().contains("video/mp4")) {
                    episodeDownloadLink = episodeIntermediateLink2;
                } else if (episodeResponse.statusCode() == 302 && episodeResponse.contentType().contains("text/html")) {
                    episodeDownloadLink = episodeResponse.header("Location");
                }

                episodeMap.put("url", episodeDownloadLink);
                episodeMap.put("downloaded", "false");
                seasonMap.put(episodeName, episodeMap);
                log.info("Finished generating configuration for episode: " + episodeName);
            });

            configfileMap.put(seasonName, seasonMap);
            log.info("Finished generating config for season: " + seasonName);
        });
    }

    private String getEpisodeIntermediateLink2(Document doc) {
        return doc.select("link[rel=video_src]")
                .attr("href")
                .replaceAll("^.*file=", "");
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

    private HttpResponse fixCaptcha(String url, Document doc, HttpResponse response) throws IOException, TesseractException {
        while (response.statusCode() == 404) {
            File captchaFile = getCaptchaFile(doc);
            String captchaCode = getCaptchaCode(captchaFile);
            log.debug("Trying captcha: " + captchaCode);
            response = sendCaptcha(url, captchaCode);
        }

        return response;
    }

    static void createConfigFile(Map<String, Map<String, Map<String, String>>> map) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        try (FileWriter fw = new FileWriter(configFileName)) {
            gson.toJson(map, fw);
//            log.info("Generated configuration file: " + configFileName);
        } catch (IOException e) {
            log.error("Faild to create configuration file ! ", e);
        }
    }

    static Map<String, Map<String, Map<String, String>>> configfileToMap() {
        Map<String, Map<String, Map<String, String>>> map = null;
        Gson gson = new Gson();
        try (Reader reader = Files.newBufferedReader(Paths.get(configFileName))) {
            map = gson.fromJson(reader, Map.class);
        } catch (IOException e) {
            log.error("Problems with converting configfile to map !" + e);
        }
        return map;
    }

    static void createSeriesInfoFile(File seriesInfoFile, String seriesName) throws IOException {
        FileUtils.writeStringToFile(seriesInfoFile, seriesName, StandardCharsets.UTF_8);
    }

    static String seriesTitleFileToString() throws IOException {
        return FileUtils.readFileToString(seriesInfoFile, StandardCharsets.UTF_8).trim();
    }

    private String getSeriesTitle(Document doc) {
        return Objects.requireNonNull(doc.select("title").first())
                .text();
    }

//    private void addElementsToMap(Document doc, Map<String, String> map) {
//        Elements el = doc.select("p.title > a");
//        el.forEach(element -> map.put(element.html(), element.attr("href")));
//    }
//
//        private String map2Json(Map<String, Object> map) {
//        Gson gson = new GsonBuilder().setPrettyPrinting().create();
//        return gson.toJson(map);
//    }

//        private void getResponseHeaders(HttpResponse response) {
//        response.headerNames().forEach(header -> System.out.println(header + " -> " + response.header(header)));
//    }

}
