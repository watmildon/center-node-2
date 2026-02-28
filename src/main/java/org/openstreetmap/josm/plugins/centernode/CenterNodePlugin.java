package org.openstreetmap.josm.plugins.centernode;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class CenterNodePlugin extends Plugin {

    public CenterNodePlugin(PluginInformation info) {
        super(info);
        MainMenu.add(MainApplication.getMenu().moreToolsMenu, new AddCenterNodeAction());
        MainMenu.add(MainApplication.getMenu().moreToolsMenu, new ReplaceWithCenterNodeAction());
    }
}
