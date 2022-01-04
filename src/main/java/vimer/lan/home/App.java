package vimer.lan.home;

import net.sourceforge.tess4j.TesseractException;
import org.apache.log4j.Logger;

import java.io.IOException;

public final class App {

    private static final Logger log = Logger.getLogger(App.class);

    public static void main(String[] args) throws TesseractException, IOException {
        try {
            log.info("Generating configuration for " + args[0]);
        } catch (Exception e) {
            System.out.println("Provide series link as an arugment !");
        }

        if (!ConfigurationManager
                .checkIfFileExists()) {
            log.info("Preparing configuration file...");
            ConfigurationManager
                    .getInstance()
                    .setSeriesPath(args[0])
                    .generate();
        }

        DownloadCoordinator downloadCoordinator = DownloadCoordinator
                .getInstance()
                .initiateDownload();

        for (int i = 1; i <= 120; i++) {
            Download d = new Download(downloadCoordinator);
            d.start();
        }
    }
}
