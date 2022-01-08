package vider.lan.home;

import net.sourceforge.tess4j.TesseractException;
import org.apache.log4j.Logger;

import java.io.IOException;

public final class App {

    private static final Logger log = Logger.getLogger(App.class);

    public static void main(String[] args) throws TesseractException, IOException {
        if(args.length == 0 && !ConfigurationManager.checkIfFileExists())
        {
            System.out.println("Provide series link as an arugment !");
            System.exit(0);
        }

        if (!ConfigurationManager
                .checkIfFileExists()) {
            log.info("Preparing configuration file...");
            ConfigurationManager
                    .getInstance()
                    .setSeriesPath(args[0])
                    .generate();
        } else {
            log.info("Found configuration file: " + ConfigurationManager.configFileName);
        }

        DownloadCoordinator downloadCoordinator = DownloadCoordinator
                .getInstance()
                .initiateDownload();

        for (int i = 1; i <= 3; i++) {
            Download d = new Download(downloadCoordinator);
            d.start();
        }
    }
}