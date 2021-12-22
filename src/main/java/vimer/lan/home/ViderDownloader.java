package vimer.lan.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import jodd.io.FileUtil;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

class ViderDownloader {

    //final String searchUrl = "https://vider.info/search/all/{searchTerms}";
    final String viderUrl = "https://vider.info";
    final String sesonsPath = "/dir/+dnv1mm";
    final static String tesseractDatapath = "/usr/local/Cellar/tesseract/4.1.3/share/tessdata";
    final static String tesseractLanguage = "eng";
    final static String tesseractImageDPI = "96";
    Map<String, String> sesonsMap = new LinkedHashMap<>();
    Map<String, String> moviesPathMap = new LinkedHashMap<>();


    public static void main(String[] args) throws TesseractException, IOException {
        ViderDownloader viderDownloader = new ViderDownloader();
        viderDownloader.getSesonsDirURLs()
                .getMoviesPath()
                .getMoviesEncodedUrl();
    }

    ViderDownloader getSesonsDirURLs() throws IOException, TesseractException {
        Document doc;
        String sesonsUrl = viderUrl + sesonsPath;
        HttpResponse response = HttpRequest.get(sesonsUrl).send();

        if (response.statusCode() == 404) {
            doc = Jsoup.parse(response.toString());
            response = fixCaptcha(sesonsUrl, doc, response);
        }

        doc = Jsoup.parse(response.toString());
        getElementsMap(doc, this.sesonsMap);

        return this;
    }

    ViderDownloader getMoviesPath() {
        sesonsMap.forEach( (sesonName, sesonPath) -> {
            Document doc;
            String sesonUrl = viderUrl + sesonPath;

            if (sesonName.equals("Sezon 1")) {
                System.out.println(sesonUrl);
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
                getElementsMap(doc, this.moviesPathMap);
            }
        });

        return this;
    }

    ViderDownloader getMoviesEncodedUrl() {
        moviesPathMap.forEach((sesonName, sesonPath) -> {
            Document doc;
            String movieUrl = viderUrl + sesonPath;

            //if (sesonPath.equals("/vid/+fnns5xx")) {    // Konkretny odcinek - debugowanie
                HttpResponse response = HttpRequest.get(movieUrl).send();
                if (response.statusCode() == 404) {
                    doc = Jsoup.parse(response.toString());
                    try {
                        response = fixCaptcha(movieUrl, doc, response);
                    } catch (IOException | TesseractException e) {
                        e.printStackTrace();
                    }
                }

                doc = Jsoup.parse(response.toString());
                String videoSrcUrl = getVideoSrcUrl(doc);
                System.out.println(videoSrcUrl);

                response = HttpRequest.get(videoSrcUrl)
                                    .header("referer","https://vider.info/")
                                    .send();

                if (response.statusCode() == 404) {
                    doc = Jsoup.parse(response.toString());
                    try {
                        response = fixCaptcha(movieUrl, doc, response);
                    } catch (IOException | TesseractException e) {
                        e.printStackTrace();
                    }
                }

                String redirectedMovieLink = response.header("Location");
                System.out.println(redirectedMovieLink);

                //TODO: - Download file (async?) - kilka jednocześnie ?
                //      - Refactor captcha calling
                //      - Extract classes ?

//                doc = Jsoup.parse(response.toString());
//                System.out.println(doc);
//                System.out.println(response.statusCode());
//                getResponseHeaders(response);


            //}
        });

        return this;
    }

    private String getVideoSrcUrl(Document doc) {
        return doc.select("link[rel=video_src]")
                .attr("href")
                .replaceAll("^.*file=","");
    }

    private void getElementsMap(Document doc, Map<String,String> map) {
        Elements sesonsURI = doc.select("p.title > a");
        sesonsURI.forEach( element -> map.put(element.html(), element.attr("href")));

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
