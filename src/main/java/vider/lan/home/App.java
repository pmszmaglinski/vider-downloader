package vider.lan.home;

import net.sourceforge.tess4j.TesseractException;
import org.apache.log4j.Logger;

import java.io.IOException;

public final class App {

    private static final Logger log = Logger.getLogger(App.class);
    static Integer threadNumber;

    public static void main(String[] args) throws TesseractException, IOException, InterruptedException {
        if (args.length == 0 && !ConfigurationManager.checkIfFileExists()) {
            System.out.println("Provide a series top link or movie url as an arugment !");
            System.exit(0);
        }

        if (!ConfigurationManager
                .checkIfFileExists()) {
            System.out.println("Preparing configuration file...");
            ConfigurationManager
                    .getInstance()
                    .setUrlPath(args[0])
                    .generate();
        } else {
            String message = "Found configuration file: " + ConfigurationManager.configFileName;
            System.out.println(message);
            log.info(message);
        }

        DownloadCoordinator downloadCoordinator = DownloadCoordinator
                .getInstance()
                .initiateDownload();

        System.out.print("\033[H\033[2J");
        if (downloadCoordinator.numberOfEpisodesLeftToDownload > 0) {
            String message = downloadCoordinator.numberOfEpisodesLeftToDownload + " episodes left to download.";
            System.out.println(message);
            log.info(message);

            Integer numberOfEpisodesLeftToDownload = downloadCoordinator.numberOfEpisodesLeftToDownload;
            threadNumber = numberOfEpisodesLeftToDownload;
            if (numberOfEpisodesLeftToDownload > 10) threadNumber = 10;

            ProgressBarBuilderQueue progressBarBuilderQueue = new ProgressBarBuilderQueue(threadNumber);
            Display display = new Display(progressBarBuilderQueue);
            display.start();

            for (int i = 1; i <= threadNumber; i++) {
                Download d = new Download(downloadCoordinator, progressBarBuilderQueue, display);
                d.start();
            }
        } else {
            System.out.println("Your " + ConfigurationManager.configFileName + " says that all downloads are completed.");
        }
    }
}