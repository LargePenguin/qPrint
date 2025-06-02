package com.qprint.modules;

import baritone.api.BaritoneAPI;
import com.qprint.QPrintAddon;
import com.qprint.states.PrintState;
import com.qprint.states.StateMachine;
import com.qprint.utils.MessageUtils;
import com.qprint.utils.QPrintData;
import com.qprint.utils.RecencyTracker;
import com.qprint.utils.StorageRegion;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.joml.Vector3d;

import java.util.*;

import static com.qprint.utils.MessageUtils.hasDoneBuildingMessage;
import static com.qprint.utils.Utilities.*;

public class QuickPrintModule extends Module {
    public QuickPrintModule() {
        super(QPrintAddon.CATEGORY, "quick-print", "Main printer module");

        data = loadData();
    }

    public enum RecoveryAction {
        Retry, Disconnect, Idle
    }

    public enum ReachModes {
        Sphere, Box
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRegions = settings.createGroup("Regions");
    private final SettingGroup sgRestock = settings.createGroup("Restock Options");
    private final SettingGroup sgRecovery = settings.createGroup("Recovery Options");
    private final SettingGroup sgAvoidance = settings.createGroup("Avoidance Options");
    private final SettingGroup sgRender = settings.createGroup("Render Options");

    // ============================= GENERAL SETTINGS ============================================
    private final Setting<Boolean> printContents = sgGeneral.add(new BoolSetting.Builder()
        .name("Print Storage Contents (debug)")
        .description("Prints the containers found in the storage region.")
        .defaultValue(false)
        .onChanged(value -> {
            if (!value)
                return;
            printStorageContents();
        })
        .build()
    );

    public final Setting<Boolean> disconnectOnFinish = sgGeneral.add(new BoolSetting.Builder()
        .name("Disconnect On Complete")
        .description("If the build succeeds, should we disconnect?")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> doStateLogging = sgGeneral.add(new BoolSetting.Builder()
        .name("Enable State Logging")
        .description("Controls whether state entry/exiting is logged to chat. Useful for debugging.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> doProgressReporting = sgGeneral.add(new BoolSetting.Builder()
        .name("Report Progress?")
        .description("Print progress reports directly to chat?")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> progReportInterval = sgGeneral.add(new IntSetting.Builder()
        .name("Progress Report Interval (minutes)")
        .description("How often, in minutes, to report progress.")
        .defaultValue(5)
        .min(1)
        .visible(doProgressReporting::get)
        .build()
    );

    // ============================= RECOVERY SETTINGS ============================================
    public final Setting<Boolean> doStuckResolution = sgRecovery.add(new BoolSetting.Builder()
        .name("Enable Stuck Detection/Resolution")
        .description("If enabled, attempts to detect when Baritone gets stuck and resolve by moving a little bit. Not recommended for staircased builds.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> maxStuckResolutions = sgRecovery.add(new IntSetting.Builder()
        .name("Max Stuck Resolutions")
        .description("When Baritone gets stuck, determines the amount of resolutions to attempt before canceling the build.")
        .defaultValue(5)
        .visible(doStuckResolution::get)
        .build()
    );

    public final Setting<RecoveryAction> recoveryAction = sgRecovery.add(new EnumSetting.Builder<RecoveryAction>()
        .name("Failure Action")
        .description("Determines the action that should be performed if the module fails for whatever reason")
        .defaultValue(RecoveryAction.Idle)
        .build()
    );

    public final Setting<Integer> maxRetries = sgRecovery.add(new IntSetting.Builder()
        .name("Max Retries")
        .description("Maximum number of retries to perform before admitting defeat")
        .defaultValue(3)
        .visible(() -> recoveryAction.get() == RecoveryAction.Retry)
        .build()
    );

    // ============================= AVOIDANCE SETTINGS ============================================
    public final Setting<List<String>> otherBots = sgAvoidance.add(new StringListSetting.Builder()
        .name("Accounts to Avoid")
        .description("Names of accounts to avoid being near. Case sensitive.")
        .build()
    );

    public final Setting<Integer> avoidanceDistance = sgAvoidance.add(new IntSetting.Builder()
        .name("Avoidance Distance Threshold")
        .description("How close accounts have to be to trigger avoidance mechanics")
        .sliderRange(1, 100)
        .defaultValue(4)
        .build()
    );

    public final Setting<Integer> avoidanceTime = sgAvoidance.add(new IntSetting.Builder()
        .name("Avoidance Time Threshold (ticks)")
        .description("How long accounts must be within the avoidance distance threshold to trigger avoidance mechanics")
        .sliderRange(20, 100)
        .defaultValue(20)
        .build()
    );

    // ============================= RESTOCK SETTINGS ============================================
    public final Setting<Boolean> useTrash = sgRestock.add(new BoolSetting.Builder()
        .name("Dump Unneeded Items in Trash")
        .description("Only blocks will be dumped. Items, gear, etc. will remain in inventory")
        .defaultValue(true)
        .build()
    );
    public final Setting<Integer> excessMaterialsThreshold = sgRestock.add(new IntSetting.Builder()
        .name("Material Cap (stacks)")
        .description("The largest amount of a single material the account should hold. Excess stacks will be dumped in the trash chest.")
        .defaultValue(6)
        .range(1,27)
        .visible(useTrash::get)
        .build()
    );
    public final Setting<Boolean> swapStacks = sgRestock.add(new BoolSetting.Builder()
        .name("Swap Lesser Stacks for Better Stacks")
        .description("Dumps small stacks of items for bigger stacks of items.")
        .defaultValue(false)
        .build()
    );
    public final Setting<Integer> swapStackThreshold = sgRestock.add(new IntSetting.Builder()
        .name("Small Stack Threshold (blocks)")
        .description("Stacks containing <= this number of items will be swapped if dumpSmallStacks = true.")
        .defaultValue(16)
        .range(1,64)
        .visible(swapStacks::get)
        .build()
    );
    public final Setting<Boolean> stopOnMissingMaterial = sgRestock.add(new BoolSetting.Builder()
        .name("Stop On Missing Materials")
        .description("Controls whether the module stops building if it can't find a restock chest with a required material")
        .defaultValue(true)
        .build()
    );
    public final Setting<Integer> maxClicksPerTick = sgRestock.add(new IntSetting.Builder()
        .name("Max Clicks Per Tick")
        .description("Maximum number of clicks per tick when moving items.")
        .defaultValue(2)
        .sliderRange(1, 500)
        .build()
    );
    public final Setting<Integer> containerOpenDelay = sgRestock.add(new IntSetting.Builder()
        .name("Container Interact Delay")
        .description("Delay in ticks between opening chests.")
        .defaultValue(5)
        .sliderRange(0, 20)
        .min(0)
        .build()
    );
    public final Setting<Integer> remainOpenDelay = sgRestock.add(new IntSetting.Builder()
        .name("Container Close Delay")
        .description("Delay in ticks for how long the chest is held open.")
        .defaultValue(10)
        .sliderRange(0, 20)
        .min(0)
        .build()
    );
    public final Setting<ReachModes> reachMode = sgRestock.add(new EnumSetting.Builder<ReachModes>()
        .name("Reach Shape")
        .description("The shape of your reach")
        .defaultValue(ReachModes.Sphere)
        .build());
    public final Setting<Double> sphereReachRange = sgRestock.add(new DoubleSetting.Builder()
        .name("Sphere Range")
        .description("Your Range, in blocks.")
        .defaultValue(4)
        .sliderRange(1, 5)
        .min(1)
        .visible(() -> reachMode.get() == ReachModes.Sphere)
        .build()
    );
    public final Setting<Integer> boxReachRange = sgRestock.add(new IntSetting.Builder()
        .name("Box Range")
        .description("Your Range, in blocks.")
        .defaultValue(4)
        .sliderRange(1, 4)
        .min(1)
        .visible(() -> reachMode.get() == ReachModes.Box)
        .build()
    );
    public final Setting<Boolean> doHandSwing = sgRestock.add(new BoolSetting.Builder()
        .name("Swing Hand When Opening")
        .description("Do or Do Not swing hand when opening chests.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Boolean> rotateToFaceContainer = sgRestock.add(new BoolSetting.Builder()
        .name("Face Container When Opening")
        .description("Faces the containers being opened server side.")
        .defaultValue(true)
        .build()
    );
    public final Setting<Integer> autoStealDelay = sgRestock.add(new IntSetting.Builder()
        .name("AutoSteal Delay")
        .description("Delay in ticks between stealing items from open containers.")
        .defaultValue(1)
        .sliderRange(0, 20)
        .build()
    );

    // ============================= REGION SETTINGS ============================================
    public final Setting<Vector3d> storageP0 = sgRegions.add(new Vector3dSetting.Builder()
        .name("Chest Region P0")
        .description("First corner of the restock region.")
        .onChanged(value -> {
            updateChestRegion();
        })
        .noSlider()
        .build()
    );
    public final Setting<Vector3d> storageP1 = sgRegions.add(new Vector3dSetting.Builder()
        .name("Chest Region P1")
        .description("Second corner of the restock region.")
        .onChanged(value -> {
            updateChestRegion();
        })
        .noSlider()
        .build()
    );
    public final Setting<Vector3d> platformOrigin = sgRegions.add(new Vector3dSetting.Builder()
        .name("Platform Origin")
        .description("Top-left corner of the mapart platform.")
        .noSlider()
        .build()
    );

    // ============================= RENDER SETTINGS ============================================
    private final Setting<Boolean> renderVolumes = sgRender.add(new BoolSetting.Builder()
        .name("Render Volumes")
        .description("Toggles visibility of debug volume rendering.")
        .defaultValue(false)
        .build()
    );
    private final Setting<SettingColor> corner1Color = sgRender.add(new ColorSetting.Builder()
        .name("Corner 1 Color")
        .description("Color to render first corner of regions.")
        .defaultValue(Color.BLUE)
        .visible(renderVolumes::get)
        .build()
    );
    private final Setting<SettingColor> corner2Color = sgRender.add(new ColorSetting.Builder()
        .name("Corner 2 Color")
        .description("Color to render second corner of regions.")
        .defaultValue(Color.RED)
        .visible(renderVolumes::get)
        .build()
    );
    private final Setting<SettingColor> originColor = sgRender.add(new ColorSetting.Builder()
        .name("Platform Origin Color")
        .description("Color to render the platform origin.")
        .defaultValue(Color.CYAN)
        .visible(renderVolumes::get)
        .build()
    );
    private final Setting<SettingColor> volumeColor = sgRender.add(new ColorSetting.Builder()
        .name("Volume Color")
        .description("Color to render volumes.")
        .defaultValue(Color.LIGHT_GRAY)
        .visible(renderVolumes::get)
        .build()
    );
    private final Setting<Integer> cornerOpacity = sgRender.add(new IntSetting.Builder()
        .name("Corner Opacity")
        .description("Opacity for drawing corners.")
        .defaultValue(64)
        .range(0,128)
        .visible(renderVolumes::get)
        .build()
    );
    private final Setting<Integer> volumeOpacity = sgRender.add(new IntSetting.Builder()
        .name("Volume Opacity")
        .description("Opacity for drawing volumes.")
        .defaultValue(64)
        .range(0,128)
        .visible(renderVolumes::get)
        .build()
    );

    public final StateMachine stateMachine = new StateMachine(this);
    public final RecencyTracker recentItems = new RecencyTracker();
    public StorageRegion activeStorage;
    private final Map<String, Object> settingsPreTrigger = new HashMap<>();

    private static final double DEF_BPT_VAL = 0.1;  // assume ~0.1 b/t placement rate. basically a number i pulled out of my ass.
    private static final int BPT_MEASUREMENT_INC = 1200;    // 1 minute
    private static final int MAX_BPT_SAMPLES = 100;

    private QPrintData data;
    private int bptTicksCounter;
    private int lastBlockCount;
    private long lastReportTime;
    private int initialBlocksPlaced;

    private String schematic;
    private int failureCount;

    public void print(String schematic) {
        if (activeStorage == null) {
            error("No storage region defined! Run .qp loc set <chestP1|chestP2> [pos] to define a region.");
            return;
        }

        if (platformOrigin == null) {
            error("No platform origin set! Run .qp loc set platformOrigin [pos] to define a region.");
            return;
        }

        updateSettings();
        MessageUtils.clearMessageQueue();

        if (!stateMachine.isComplete()) {
            cancel(false);
        }

        failureCount = 0;
        initialBlocksPlaced = getPlatformBlockCount();
        this.schematic = schematic;

        stateMachine.push(new PrintState(this, schematic));
    }

    public void fail() {
        if (recoveryAction.get() == RecoveryAction.Disconnect) {
            mc.world.disconnect();
        } else if (recoveryAction.get() == RecoveryAction.Retry) {
            MessageUtils.clearMessageQueue();
            stateMachine.reset();
            failureCount++;

            if (failureCount > maxRetries.get()) {
                error("Maximum retries exceeded. Canceling.");
                cancel(false);
                return;
            }

            info("Trying the print again (attempt " + failureCount + "/" + maxRetries + ")...");

            initialBlocksPlaced = getPlatformBlockCount();
            stateMachine.push(new PrintState(this, schematic));
        } else {
            cancel(false);
        }
    }

    public void pause(boolean userRequested) {
        if (stateMachine.isComplete() && userRequested) {
            error("Not building!");
            return;
        } else if (userRequested) {
            info("Paused. .qp resume to resume; .qp cancel to cancel");
        }

        stateMachine.isPaused = true;
    }

    public void resume(boolean userRequested) {
        if (stateMachine.isComplete() && userRequested) {
            error("Not building!");
            return;
        } else if (!stateMachine.isPaused && userRequested) {
            error("Not paused!");
            return;
        } else if (userRequested) {
            info("Resumed.");
        }

        stateMachine.isPaused = false;
    }

    public void cancel(boolean userRequested) {
        if (stateMachine.isComplete() && userRequested) {
            error("Not building!");
            return;
        } else if (userRequested) {
            info("Canceled.");
        }

        onTaskFinish();
    }

    public void printETA() {
        if (stateMachine.isComplete()) {
            error("Not building!");
            return;
        }

        var overallPlaced = getPlatformBlockCount();
        var remainingBlocks = (128 * 128) - overallPlaced;
        var avgBpt = data.bptSamples.stream().mapToDouble(d -> d).average().orElse(DEF_BPT_VAL);

        if (avgBpt == 0)
            avgBpt = DEF_BPT_VAL;

        var remainingTicks = remainingBlocks / avgBpt;

        var completionPct = Math.floor((((float)overallPlaced) / (128 * 128)) * 100);
        var timeRemaining = Math.round(remainingTicks / 20);

        info(completionPct + "%% complete. ETA: " + formatTime(timeRemaining) + " (" + String.format("%.5f", avgBpt) + " blocks/tick)");
    }

    private void onTaskFinish() {
        restoreSettings();
        MessageUtils.clearMessageQueue();
        stateMachine.reset();
        saveData(data);
    }

    private void updateSettings() {
        settingsPreTrigger.clear();
        settingsPreTrigger.put("allowInventory", BaritoneAPI.getSettings().allowInventory.value);
        settingsPreTrigger.put("allowBreak", BaritoneAPI.getSettings().allowBreak.value);
        settingsPreTrigger.put("allowPlace", BaritoneAPI.getSettings().allowPlace.value);
        BaritoneAPI.getSettings().allowInventory.value = true;
        BaritoneAPI.getSettings().allowBreak.value = true;
        BaritoneAPI.getSettings().allowPlace.value = true;
    }

    private void restoreSettings() {
        BaritoneAPI.getSettings().allowInventory.value = (boolean)settingsPreTrigger.get("allowInventory");
        BaritoneAPI.getSettings().allowBreak.value = (boolean)settingsPreTrigger.get("allowBreak");
        BaritoneAPI.getSettings().allowPlace.value = (boolean)settingsPreTrigger.get("allowPlace");
    }

    private void updateChestRegion() {
        var p0 = vecToPos(storageP0.get());
        var p1 = vecToPos(storageP1.get());

        if (p0.equals(BlockPos.ORIGIN) || p1.equals(BlockPos.ORIGIN))
            return;

        activeStorage = new StorageRegion("Storage", p0, p1);
    }

    private void printStorageContents() {
        printContents.set(false);

        if (activeStorage == null) {
            error("Storage region not defined! Use the .qp command to set positions.");
            return;
        }

        activeStorage.printContents();
    }

    private int getPlatformBlockCount() {
        var farCorner = new Vector3d(platformOrigin.get());
        farCorner.x += 127;
        farCorner.z += 127;

        return countNonAirBlocks(vecToPos(platformOrigin.get()), vecToPos(farCorner));
    }

    @Override
    public void onActivate() {
        updateChestRegion();

        resume(false);
    }

    @Override
    public void onDeactivate() {
        pause(false);

        saveData(data);
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (stateMachine.isPaused || stateMachine.isComplete()) {
            if (hasDoneBuildingMessage() && disconnectOnFinish.get()) {
                mc.world.disconnect();
            }

            return;
        }

        bptTicksCounter++;
        if (bptTicksCounter >= BPT_MEASUREMENT_INC) {
            bptTicksCounter = 0;

            int currentBlockCount = data.bptSamples.isEmpty() ? getPlatformBlockCount() - initialBlocksPlaced : getPlatformBlockCount();
            int placedThisPeriod = currentBlockCount - lastBlockCount;
            lastBlockCount = currentBlockCount;

            double bpt = placedThisPeriod / (double)(progReportInterval.get() * 1200);
            data.bptSamples.addLast(bpt);
            if (data.bptSamples.size() > MAX_BPT_SAMPLES)
                data.bptSamples.removeFirst();
        }

        if (doProgressReporting.get()) {
            if (lastReportTime == 0)
                lastReportTime = System.currentTimeMillis();
            if (((System.currentTimeMillis() - lastReportTime) / 60000) >= progReportInterval.get()) {
                printETA();
                lastReportTime = System.currentTimeMillis();
            }
        }
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen) {
            MessageUtils.clearMessageQueue();
            stateMachine.reset();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        MessageUtils.clearMessageQueue();
        stateMachine.reset();
    }

    @EventHandler
    private void onGameMessage(ReceiveMessageEvent event) {
        MessageUtils.addMessage(event.getMessage().getString());
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (!renderVolumes.get())
            return;

        Color p0Color = new Color(corner1Color.get());
        p0Color.a = cornerOpacity.get();

        Color p1Color = new Color(corner2Color.get());
        p1Color.a = cornerOpacity.get();

        Color boxColor = new Color(volumeColor.get());
        boxColor.a = volumeOpacity.get();

        Color platformColor = new Color(this.originColor.get());
        platformColor.a = cornerOpacity.get();

        if (activeStorage != null) {
            Box p0Box = new Box(activeStorage.getP0());
            Box p1Box = new Box(activeStorage.getP1());
            Box regionBox = new Box(activeStorage.getMinX(), activeStorage.getMinY(), activeStorage.getMinZ(),
                activeStorage.getMaxX(), activeStorage.getMaxY(), activeStorage.getMaxZ());

            event.renderer.box(p0Box, boxColor, p0Color, ShapeMode.Both, 0);
            event.renderer.box(p1Box, boxColor, p1Color, ShapeMode.Both, 0);
            event.renderer.box(regionBox, boxColor, boxColor, ShapeMode.Both, 0);
        }
        else {
            if (!storageP0.get().equals(new Vector3d(0,0,0))) {
                Box p0Box = new Box(vecToPos(storageP0.get()));
                event.renderer.box(p0Box, boxColor, p0Color, ShapeMode.Both, 0);
            }
            if (!storageP1.get().equals(new Vector3d(0,0,0))) {
                Box p1Box = new Box(vecToPos(storageP1.get()));
                event.renderer.box(p1Box, boxColor, p1Color, ShapeMode.Both, 0);
            }
        }

        if (platformOrigin != null) {
            var originPos = vecToPos(platformOrigin.get());
            Box originBox = new Box(originPos);
            Box regionBox = new Box(originPos.getX(), originPos.getY(), originPos.getZ(),
                originPos.getX() + 127, originPos.getY(), originPos.getZ() + 127);

            event.renderer.box(originBox, boxColor, platformColor, ShapeMode.Both, 0);
            event.renderer.box(regionBox, boxColor, boxColor, ShapeMode.Both, 0);
        }
    }
}
