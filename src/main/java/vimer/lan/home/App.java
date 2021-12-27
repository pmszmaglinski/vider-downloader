package vimer.lan.home;

import net.sourceforge.tess4j.TesseractException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.io.IOException;

public class App {

    private static final Logger log = Logger.getLogger(App.class);

    public static void main(String[] args) throws TesseractException, IOException {

        if (Configuration.configFile.exists()) {
            log.info("Plik istnieje... Sprawdzam czy wszystko jest ściągnięte... Jak nie to kontynuuje ściąganie...");

            System.exit(0);
        }

        Configuration configuration = new Configuration();
        configuration
                .getLinks()
                .createConfigFile(Configuration.configfileMap, Configuration.configFileName);
    }
}
