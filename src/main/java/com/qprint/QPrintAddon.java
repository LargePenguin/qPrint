package com.qprint;

import com.qprint.commands.QuickPrintCommand;
import com.qprint.modules.QuickPrintModule;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class QPrintAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("qPrint");
    public static final HudGroup HUD_GROUP = new HudGroup("qPrint");

    public static QuickPrintModule printModule;

    @Override
    public void onInitialize() {
        LOG.info("Initializing qPrint");

        printModule = new QuickPrintModule();

        // Modules
        Modules.get().add(printModule);

        // Commands
        Commands.add(new QuickPrintCommand());

        // HUD
        //Hud.get().register(HudMain.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.qprint";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("LargePenguin", "qPrint");
    }
}
