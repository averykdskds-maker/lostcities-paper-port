package mcjty.lostcities.setup;

import mcjty.lostcities.LostCities;
import mcjty.lostcities.api.ILostCityProfileSetup;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ModSetup {
    private static Logger logger;
    public final List<Consumer<ILostCityProfileSetup>> profileSetups = new ArrayList<>();

    public static Logger getLogger() {
        return logger == null ? LostCities.LOGGER : logger;
    }

    public void preInit() {
        logger = LostCities.LOGGER;
        mcjty.lostcities.config.ProfileSetup.setupProfiles();
    }
}
