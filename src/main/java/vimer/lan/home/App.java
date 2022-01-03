package vimer.lan.home;

import net.sourceforge.tess4j.TesseractException;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class App {

    private static final Logger log = Logger.getLogger(App.class);

    public static void main(String[] args) throws TesseractException, IOException {

        if (!Configuration.checkIfFileExists()) {
            log.info("Preparing configuration file...");
            Configuration
                    .generate();
        }
        Configuration configuration = Configuration
                .getInstance()
                .initiateDownload();

        for (int i = 1; i <= 200; i++) {
            Download d = new Download(configuration);
            d.start();
        }

//        Configuration configuration = Configuration.getInstance();
//        System.out.println(
//                configuration
//                        .initiateDownload()
//                        .getNextEpisodeToDownload()
//                        .toString());
    }

//    public static void main(String[] args) throws InterruptedException {
//
//        Counter counter = Counter.getInstance();
//        System.out.println(counter.count);
//
//        ThreadsTest t = new ThreadsTest();
//        ThreadsTest t1 = new ThreadsTest();
//
//        t.start();
//        t1.start();
//        t.join();
//        t1.join();
//        System.out.println(counter.count);
//    }
}
