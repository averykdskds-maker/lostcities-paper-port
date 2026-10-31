package dev.rotbound.lostcitiespaper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public final class LostCitiesPaperBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                URI uri = Objects.requireNonNull(
                        LostCitiesPaperBootstrap.class.getResource("/lostcities_datapack"),
                        "Embedded Lost Cities datapack was not packaged"
                ).toURI();
                event.registrar().discoverPack(uri, "provided", configurer -> configurer.autoEnableOnServerStart(true));
            } catch (Exception e) {
                throw new IllegalStateException("Could not register embedded Lost Cities + Modern Tweaks datapack", e);
            }
        });
    }

    @Override
    public JavaPlugin createPlugin(io.papermc.paper.plugin.bootstrap.PluginProviderContext context) {
        return new LostCitiesPaperPlugin();
    }
}
