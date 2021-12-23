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
    Map<String, Object> downloadMap = new LinkedHashMap<>();
    //JSONObject configuration = new JSONObject();

    
    public static void main(String[] args) throws TesseractException, IOException {
        ViderDownloader viderDownloader = new ViderDownloader();
        viderDownloader.getLinks();
    }

    private void getLinks() throws IOException, TesseractException {
        Document doc;
        String seriesUrl = viderUrl + seriesPath;
        HttpResponse response = HttpRequest.get(seriesUrl).send();

        if (response.statusCode() == 404) {
            doc = Jsoup.parse(response.toString());
            response = fixCaptcha(seriesUrl, doc, response);
        }

        doc = Jsoup.parse(response.toString());
        Elements el1 = doc.select("p.title > a");

        el1.forEach( element -> {
            String seasonName = element.html();
            String seasonPath = element.attr("href");
            String seasonUrl = viderUrl + seasonPath;
            System.out.println(seasonName);

            HttpResponse resp1 = HttpRequest.get(seasonUrl).send();
            Document doc1;

            if (resp1.statusCode() == 404) {
                doc1 = Jsoup.parse(resp1.toString());
                try {
                    resp1 = fixCaptcha(seasonUrl, doc1, resp1);
                } catch (IOException | TesseractException e) {
                    e.printStackTrace();
                }
            }

            doc1 = Jsoup.parse(resp1.toString());
            Elements el2 = doc1.select("p.title > a");
            el2.forEach( element2 -> {
                String episodeName = element2.html();
                String episodePath = element2.attr("href");
                String episodeIntermediateLink1 = viderUrl + episodePath;

                HttpResponse resp2 = HttpRequest.get(episodeIntermediateLink1).send();
                Document doc2;
                if (resp2.statusCode() == 404) {
                    doc2 = Jsoup.parse(resp2.toString());
                    try {
                        resp2 = fixCaptcha(episodeIntermediateLink1, doc2, resp2);
                    } catch (IOException | TesseractException e) {
                        e.printStackTrace();
                    }
                }

                doc2 = Jsoup.parse(resp2.toString());
                String episodeIntermediateLink2 = getepisodeIntermediate2Url(doc2);

                resp2 = HttpRequest.get(episodeIntermediateLink2)
                        .header("referer","https://vider.info/")
                        .send();

                if (resp2.statusCode() == 404) {
                    doc2 = Jsoup.parse(resp2.toString());
                    try {
                        resp2 = fixCaptcha(episodeIntermediateLink2, doc2, resp2);
                    } catch (IOException | TesseractException e) {
                        e.printStackTrace();
                    }
                }

                String episodeDownloadLink = resp2.header("Location");
                System.out.println(episodeName + " " + episodeDownloadLink);

            });
        });
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
