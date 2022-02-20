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
    private String urlPath = null;

    private final static String tesseractDatapath = System.getenv("TESSDATA_PREFIX");
    private final static String tesseractLanguage = "eng";
    private final static String tesseractImageDPI = "96";

    private final static Map<String, Map<String, String>> configfileMap = new LinkedHashMap<>();
    final static String configFileName = "configfile.json";
    private final static File configFile = new File(configFileName);

    private final static String movieInfoFileName = "series-info";
    private final static File movieInfoFile = new File(movieInfoFileName);

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

    public ConfigurationManager setUrlPath(String link) {
        this.urlPath = link;
        return this;
    }

    public void generate() throws TesseractException, IOException {
        getLinks(urlPath);
        createConfigFile(configfileMap);
    }

    void getLinks(String url) throws TesseractException, IOException {
        String docUrl = viderUrl + url;
        HttpResponse response = getResponse(docUrl);
        Document doc = Jsoup.parse(response.toString());

        createMovieInfoFile(getMovieTitle(doc));

        if (url.startsWith("/vid/+f")) {
            log.info("Got movie link...");
            Map<String, String> episodeMap = getEpisodeMap(url);
            configfileMap.put(getMovieTitle(doc), episodeMap);
            String message = "Finished generating config for movie: " + getMovieTitle(doc);
            System.out.println(message);
            log.info(message);
        } else if (url.startsWith("/dir/+d")) {
            doc.select("p.title > a").forEach(x -> {
                String capturedLink, linkDescription;
                try {
                    capturedLink = x.attr("href");
                    linkDescription = x.html();
                    if (capturedLink.startsWith("/dir/+d")) {
                        log.info("Entering directory: " + capturedLink + " " + linkDescription);
                        getLinks(linkDescription);
                    } else if (capturedLink.startsWith("/vid/+f")) {
                        log.info("Found movie: " + capturedLink + " -> " + linkDescription);
                        Map<String, String> episodeMap = getEpisodeMap(capturedLink);
                        configfileMap.put(linkDescription, episodeMap);
                        String message = "Finished generating config for episode: " + linkDescription;
                        System.out.println(message);
                        log.info(message);
                    } else throw new RuntimeException("Found unknown link: " + capturedLink + linkDescription);
                } catch (TesseractException | IOException e) {
                    e.printStackTrace();
                }
            });
        } else throw new RuntimeException("Provided unknown link " + url);
    }

    private Map<String, String> getEpisodeMap(String capturedLink) throws TesseractException, IOException {
        Map<String, String> episodeMap = new LinkedHashMap<>();
        String episodeIntermediateLink1 = viderUrl + capturedLink;
        HttpResponse episodeResponse = getResponse(episodeIntermediateLink1);
        Document episodeDocument = Jsoup.parse(episodeResponse.toString());

        String episodeIntermediateLink2 = getEpisodeIntermediateLink2(episodeDocument);
        episodeResponse = getResponse(episodeIntermediateLink2);

        String episodeDownloadLink;
        if (episodeResponse.statusCode() == 206 && episodeResponse.contentType().contains("video/mp4")) {
            episodeDownloadLink = episodeIntermediateLink2;
        } else if (episodeResponse.statusCode() == 302 && episodeResponse.contentType().contains("text/html")) {
            episodeDownloadLink = episodeResponse.header("Location");
        } else throw new RuntimeException("Unknown return status code for episode link capture: "
                + episodeResponse);

        episodeMap.put("url", episodeDownloadLink);
        episodeMap.put("downloaded", "false");

        return episodeMap;

    }

    private HttpResponse getResponse(String docUrl) throws TesseractException, IOException {
        Document doc;
        HttpResponse response = HttpRequest.get(docUrl)
                .header("referer", "https://vider.info/")
                .header("Range", "bytes=0-0")
                .send();
        if (response.statusCode() == 404) {
            doc = Jsoup.parse(response.toString());
            response = fixCaptcha(docUrl, doc, response);
        }
        return response;
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
        try {
            log.info("Sleeping a bit...");
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return HttpRequest
                .post(url)
                .form("captcha", captchaCode)
                .send();
    }

    private HttpResponse fixCaptcha(String url, Document doc, HttpResponse response) throws IOException, TesseractException {
        while (response.statusCode() == 404) {
            File captchaFile = getCaptchaFile(doc);
            String captchaCode = getCaptchaCode(captchaFile);
            log.info("Trying captcha: " + captchaCode);
            response = sendCaptcha(url, captchaCode);
        }

        return response;
    }

    static void createConfigFile(Map<String, Map<String, String>> map) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        try (FileWriter fw = new FileWriter(configFileName)) {
            gson.toJson(map, fw);
        } catch (IOException e) {
            log.error("Faild to create configuration file ! ", e);
        }
    }

    static Map<String, Map<String, String>> configfileToMap() {
        Map<String, Map<String, String>> map = null;
        Gson gson = new Gson();
        try (Reader reader = Files.newBufferedReader(Paths.get(configFileName))) {
            map = gson.fromJson(reader, Map.class);
        } catch (IOException e) {
            log.error("Problems with converting configfile to map !" + e);
        }
        return map;
    }

    static void createMovieInfoFile(String seriesName) throws IOException {
        FileUtils.writeStringToFile(ConfigurationManager.movieInfoFile, seriesName, StandardCharsets.UTF_8);
    }

    static String movieTitleFileToString() throws IOException {
        return FileUtils.readFileToString(ConfigurationManager.movieInfoFile, StandardCharsets.UTF_8).trim();
    }

    private String getMovieTitle(Document doc) {
        return Objects.requireNonNull(doc.select("title").first())
                .text();
    }
}
