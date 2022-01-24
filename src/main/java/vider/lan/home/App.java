package vider.lan.home;

import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import net.sourceforge.tess4j.TesseractException;
import org.apache.log4j.Logger;

import java.io.IOException;

public final class App {

    private static final Logger log = Logger.getLogger(App.class);

    public static void main(String[] args) throws TesseractException, IOException {
        if(args.length == 0 && !ConfigurationManager.checkIfFileExists())
        {
            System.out.println("Provide a series link as an arugment !");
            System.exit(0);
        }

        if (!ConfigurationManager
                .checkIfFileExists()) {
            log.info("Preparing configuration file...");
            ConfigurationManager
                    .getInstance()
                    .setUrlPath(args[0])
                    .generate();
        } else {
            log.info("Found configuration file: " + ConfigurationManager.configFileName);
        }

        DownloadCoordinator downloadCoordinator = DownloadCoordinator
                .getInstance()
                .initiateDownload();

        log.info(downloadCoordinator.numberOfEpisodesLeftToDownload + " left to download.");

        for (int i = 1; i <= 10; i++) {
            Download d = new Download(downloadCoordinator);
            d.start();
        }
    }

//public static void main(String[] args) {
//    String response302 = "https://stream.vider.info/video/113722/v.mp4?uid=0";
//    String response206 = "https://stream.vider.info/video/13859/v.mp4?uid=0";
//
//    HttpResponse episodeResponse = HttpRequest.get(response302)
//            .header("referer", "https://vider.info/")
//            .header("Range", "bytes=0-0")
//            .send();
//
//    HttpResponse episodeResponse2 = HttpRequest.get(response206)
//            .header("referer", "https://vider.info/")
//            .header("Range", "bytes=0-0")
//            .send();
//
//    System.out.println("Response 302: Content-type: " + episodeResponse.contentType().contains("text/html"));
//    System.out.println("Response 206: Content-type: " + episodeResponse2.contentType().contains("video/mp4"));
//}

}