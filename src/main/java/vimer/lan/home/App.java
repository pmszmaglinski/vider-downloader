package vimer.lan.home;

import net.sourceforge.tess4j.TesseractException;
import org.apache.log4j.Logger;

import java.io.IOException;

public class App {

    private static final Logger log = Logger.getLogger(App.class);

    public static void main(String[] args) throws TesseractException, IOException {

        if (Configuration.configFile.exists()) {
            log.info("Found " + Configuration.configFileName);

            Download download = new Download()
                    //.loopOverMap();
                            .downloadFileFromUrl();

            System.exit(0);
        }

        Configuration configuration = new Configuration();
        configuration
                .getLinks()
                .createConfigFile(Configuration.configfileMap, Configuration.configFileName);
    }
}
