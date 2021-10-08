package interfaces;

import net.dv8tion.jda.api.events.GenericEvent;

public interface IBotModule {
    void coreProcess(GenericEvent event);
}
