package vimer.lan.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.io.FileUtil;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

class ViderDownloader {

    //final String searchUrl = "https://vider.info/search/all/{searchTerms}";
    final String viderUrl = "https://vider.info";
    final String seriesPath = "/dir/+dnv1mm";
    final static String tesseractDatapath = "/usr/local/Cellar/tesseract/4.1.3/share/tessdata";
    final static String tesseractLanguage = "eng";
    final static String tesseractImageDPI = "96";
    Map<String, String> sesonsPathMap = new LinkedHashMap<>();
    Map<String, String> episodesIntermediatePathMap = new LinkedHashMap<>();
    JSONObject configuration = new JSONObject();

    
    public static void main(String[] args) throws TesseractException, IOException {
        ViderDownloader viderDownloader = new ViderDownloader();
        viderDownloader.generateSesonsPathsMap()
                .generateEpisodesIntermediatePathsMap()
                .generateEpisodeDownloadLinksMap();
    }

    ViderDownloader generateSesonsPathsMap() throws IOException, TesseractException {
        Document doc;
        String seriesUrl = viderUrl + seriesPath;
        HttpResponse response = HttpRequest.get(seriesUrl).send();

        if (response.statusCode() == 404) {
            doc = Jsoup.parse(response.toString());
            response = fixCaptcha(seriesUrl, doc, response);
        }

        doc = Jsoup.parse(response.toString());
        addSeriesNameToConfiguration(doc, configuration);
        addElementsToMap(doc, this.sesonsPathMap);

        return this;
    }

    ViderDownloader generateEpisodesIntermediatePathsMap() {
        sesonsPathMap.forEach( (sesonName, sesonPath) -> {
            Document doc;
            String sesonUrl = viderUrl + sesonPath;

            if (sesonName.equals("Sezon 1")) {
                System.out.println(sesonName + " --> " + sesonUrl);
                HttpResponse response = HttpRequest.get(sesonUrl).send();

                if (response.statusCode() == 404) {
                    doc = Jsoup.parse(response.toString());
                    try {
                        response = fixCaptcha(sesonUrl, doc, response);
                    } catch (IOException | TesseractException e) {
                        e.printStackTrace();
                    }
                }

                doc = Jsoup.parse(response.toString());
                addElementsToMap(doc, this.episodesIntermediatePathMap);
            }
        });

        return this;
    }

    ViderDownloader generateEpisodeDownloadLinksMap() {
        episodesIntermediatePathMap.forEach((episodeName, episodePath) -> {
            Document doc;
            String episodeIntermediate1Url = viderUrl + episodePath;
            System.out.println("episodeIntermediate1Url : " + episodeIntermediate1Url);

            //if (episodePath.equals("/vid/+fnns5xx")) {    // Konkretny odcinek - debugowanie
                HttpResponse response = HttpRequest.get(episodeIntermediate1Url).send();
                if (response.statusCode() == 404) {
                    doc = Jsoup.parse(response.toString());
                    try {
                        response = fixCaptcha(episodeIntermediate1Url, doc, response);
                    } catch (IOException | TesseractException e) {
                        e.printStackTrace();
                    }
                }

                doc = Jsoup.parse(response.toString());
                String episodeIntermediate2Url = getepisodeIntermediate2Url(doc);
                System.out.println("episodeIntermediate2Url : " + episodeIntermediate2Url);

                response = HttpRequest.get(episodeIntermediate2Url)
                                    .header("referer","https://vider.info/")
                                    .send();

                if (response.statusCode() == 404) {
                    doc = Jsoup.parse(response.toString());
                    try {
                        response = fixCaptcha(episodeIntermediate1Url, doc, response);
                    } catch (IOException | TesseractException e) {
                        e.printStackTrace();
                    }
                }

                String episodeDownloadLink = response.header("Location");
                System.out.println("episodeDownloadLink : " + episodeDownloadLink);

                //TODO: - Generate json with {Seson 1:{{episode1Name,episodeUrl},{episode2Name,episodeUrl}},Seson2:{...}

                //TODO: - Download file (async?) - kilka jednocześnie ?
                //      - Refactor captcha calling
                //      - Extract classes ?

//                doc = Jsoup.parse(response.toString());
//                System.out.println(doc);
//                System.out.println(response.statusCode());
//                getResponseHeaders(response);


            //}
        });
        System.out.println("sesonsPathMap: " + sesonsPathMap);
        System.out.println("episodesIntermediatePathMap: " + episodesIntermediatePathMap);

        return this;
    }

    private String getepisodeIntermediate2Url(Document doc) {
        return doc.select("link[rel=video_src]")
                .attr("href")
                .replaceAll("^.*file=","");
    }

    private void addSeriesNameToConfiguration(Document doc, JSONObject configuration) {
        String el = Objects.requireNonNull(doc.select("title").first()).text();
        configuration.put("title", el);
    }

    private void addElementsToMap(Document doc, Map<String,String> map) {
        Elements el = doc.select("p.title > a");
        el.forEach( element -> map.put(element.html(), element.attr("href")));
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
        response.headerNames().forEach( header -> System.out.println(header + " -> " + response.header(header)));
    }

    private HttpResponse fixCaptcha(String url, Document doc, HttpResponse response) throws IOException, TesseractException {
        while (response.statusCode() == 404) {
            File captchaFile = getCaptchaFile(doc);
            String captchaCode = getCaptchaCode(captchaFile);
            System.out.println(captchaCode);
            response = sendCaptcha(url, captchaCode);
        }

        return response;
    }

    private String map2Json(Map<String, String> map) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(map);
    }

}
