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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

class Configuration {   //TODO As singleton ?

    private static final Logger log = Logger.getLogger(Configuration.class);

    //final String searchUrl = "https://vider.info/search/all/{searchTerms}";
    final String viderUrl = "https://vider.info";
    final String seriesPath = "/dir/+dnv1mm";
    //final String seriesPath = "/dir/+dnc5"; Dempsey i Makepeace na tropie ;)

    final static String tesseractDatapath = "/usr/local/Cellar/tesseract/4.1.3/share/tessdata";
    final static String tesseractLanguage = "eng";
    final static String tesseractImageDPI = "96";

    final static Map<String, Object> configfileMap = new LinkedHashMap<>();
    final static String configFileName = "configfile.json";
    final static File configFile = new File(configFileName);


    Configuration getLinks() throws IOException, TesseractException {
        Document seriesDocument;
        String seriesUrl = viderUrl + seriesPath;
        HttpResponse seriesResponse = HttpRequest.get(seriesUrl).send();

        if (seriesResponse.statusCode() == 404) {
            seriesDocument = Jsoup.parse(seriesResponse.toString());
            seriesResponse = fixCaptcha(seriesUrl, seriesDocument, seriesResponse);
        }

        seriesDocument = Jsoup.parse(seriesResponse.toString());
        configfileMap.put("title", getSeriesName(seriesDocument));

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
                String episodeIntermediateLink2 = getepisodeIntermediateLink2(episodeDocument);

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

    private String getepisodeIntermediateLink2(Document doc) {
        return doc.select("link[rel=video_src]")
                .attr("href")
                .replaceAll("^.*file=", "");
    }

    private String getSeriesName(Document doc) {
        return Objects.requireNonNull(doc.select("title")
                        .first())
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

    Configuration createConfigFile(Map<String, Object> map, String configFileName) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        try (FileWriter fw = new FileWriter(configFileName)) {
            gson.toJson(map, fw);
            log.info("Created configuration file: " + configFileName);
        } catch (IOException e) {
            log.error("Faild to create configuration file ! ", e);
        }

        return this;
    }
}
