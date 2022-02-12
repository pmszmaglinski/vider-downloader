package vider.lan.home;

import net.sourceforge.tess4j.TesseractException;
import org.apache.log4j.Logger;

import java.io.IOException;

public final class App {

    private static final Logger log = Logger.getLogger(App.class);
    static Integer threadNumber;

    public static void main(String[] args) throws TesseractException, IOException, InterruptedException {
        if (args.length == 0 && !ConfigurationManager.checkIfFileExists()) {
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

        //TODO: If zero episodes to download exit - while (episodesToDownload > 0) { ...stuff... }
        log.info(downloadCoordinator.numberOfEpisodesLeftToDownload + " episodes left to download.");

        Integer numberOfEpisodesLeftToDownload = downloadCoordinator.numberOfEpisodesLeftToDownload;
        threadNumber = numberOfEpisodesLeftToDownload;
        if (numberOfEpisodesLeftToDownload > 10) threadNumber = 10;

        //TODO: To remove - set only for developement
        threadNumber = 3;

        ProgressBarBuilderQueue progressBarBuilderQueue = new ProgressBarBuilderQueue(threadNumber);
        Display display = new Display(progressBarBuilderQueue);
        display.start();

        for (int i = 1; i <= threadNumber; i++) {
            Download d = new Download(downloadCoordinator, progressBarBuilderQueue, display);
            d.start();
        }
    }
}