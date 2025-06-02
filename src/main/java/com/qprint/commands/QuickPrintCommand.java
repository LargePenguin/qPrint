package com.qprint.commands;

import com.qprint.QPrintAddon;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.PosArgument;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

import static com.qprint.utils.Utilities.posToVec;
import static com.qprint.utils.Utilities.vecToPos;

public class QuickPrintCommand extends Command {

    public QuickPrintCommand() {
        super("quickPrint", "Command for configuring and running the QuickPrint mapart printer", "qPrint", "qp");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("print")
            .then(argument("schematic", StringArgumentType.greedyString())
                 .executes(ctx -> {
                     var schematic = ctx.getArgument("schematic", String.class);
                     QPrintAddon.printModule.print(schematic);
                     return SINGLE_SUCCESS;
                 })
            )
        );

        builder.then(literal("eta")
            .executes(ctx -> {
                QPrintAddon.printModule.printETA();
                return SINGLE_SUCCESS;
            })
        );

        builder.then(literal("cancel")
            .executes(ctx -> {
                QPrintAddon.printModule.cancel(true);
                return SINGLE_SUCCESS;
            })
        );

        builder.then(literal("stop")
            .executes(ctx -> {
                QPrintAddon.printModule.cancel(true);
                return SINGLE_SUCCESS;
            })
        );

        builder.then(literal("pause")
            .executes(ctx -> {
                QPrintAddon.printModule.pause(true);
                return SINGLE_SUCCESS;
            })
        );

        builder.then(literal("resume")
            .executes(ctx -> {
                QPrintAddon.printModule.resume(true);
                return SINGLE_SUCCESS;
            })
        );

        BlockPos platformOrigin = vecToPos(QPrintAddon.printModule.platformOrigin.get());
        BlockPos chestP0 = vecToPos(QPrintAddon.printModule.storageP0.get());
        BlockPos chestP1 = vecToPos(QPrintAddon.printModule.storageP1.get());

        builder.then(literal("loc")
            .then(literal("list")
                .executes(ctx -> {
                    info("platformOrigin: " + (platformOrigin.equals(BlockPos.ORIGIN) ? "NOT SET" : platformOrigin.toShortString()));
                    info("chestP0: " + (chestP0.equals(BlockPos.ORIGIN) ? "NOT SET" : chestP0.toShortString()));
                    info("chestP1: " + (chestP1.equals(BlockPos.ORIGIN) ? "NOT SET" : chestP1.toShortString()));

                    return SINGLE_SUCCESS;
                })
            )
            .then(literal("set")
                .then(literal("platformOrigin")
                    .then(argument("pos", Vec3ArgumentType.vec3())
                        .executes(ctx -> setLocation(ctx, "platformOrigin", true))
                    )
                    .executes(ctx -> setLocation(ctx, "platformOrigin", false))
                )

                .then(literal("chestP1")
                    .then(argument("pos", Vec3ArgumentType.vec3())
                        .executes(ctx -> setLocation(ctx, "chestP1", true))
                    )
                    .executes(ctx -> setLocation(ctx, "chestP1", false))
                )

                .then(literal("chestP2")
                    .then(argument("pos", Vec3ArgumentType.vec3())
                        .executes(ctx -> setLocation(ctx, "chestP2", true))
                    )
                    .executes(ctx -> setLocation(ctx, "chestP2", false))
                )
            )

            .then(literal("get")
                .then(literal("platformOrigin")
                    .executes(ctx -> {
                        if (platformOrigin.equals(BlockPos.ORIGIN))
                            error("platformOrigin not set");
                        else
                            info("platformOrigin: " + platformOrigin.toShortString());
                        return SINGLE_SUCCESS;
                    })
                )

                .then(literal("chestP0")
                    .executes(ctx -> {
                        if (chestP0.equals(BlockPos.ORIGIN))
                            error("chestP0 not set");
                        else
                            info("chestP0: " + chestP0.toShortString());
                        return SINGLE_SUCCESS;
                    })
                )

                .then(literal("chestP1")
                    .executes(ctx -> {
                        if (chestP1.equals(BlockPos.ORIGIN))
                            error("chestP1 not set");
                        else
                            info("chestP1: " + chestP1.toShortString());
                        return SINGLE_SUCCESS;
                    })
                )
            )

            .then(literal("clear")
                .then(literal("platformOrigin")
                    .executes(ctx -> {
                        QPrintAddon.printModule.platformOrigin.set(new Vector3d(0, 0, 0));
                        info("platformOrigin cleared");
                        return SINGLE_SUCCESS;
                    })
                )

                .then(literal("chestP0")
                    .executes(ctx -> {
                        QPrintAddon.printModule.storageP0.set(new Vector3d(0,0,0));
                        info("chestP0 cleared");
                        return SINGLE_SUCCESS;
                    })
                )

                .then(literal("chestP1")
                    .executes(ctx -> {
                        QPrintAddon.printModule.storageP1.set(new Vector3d(0,0,0));
                        info("chestP1 cleared");
                        return SINGLE_SUCCESS;
                    })
                )

                .executes(ctx -> {
                    QPrintAddon.printModule.platformOrigin.set(new Vector3d(0, 0, 0));
                    QPrintAddon.printModule.storageP0.set(new Vector3d(0,0,0));
                    QPrintAddon.printModule.storageP1.set(new Vector3d(0,0,0));
                    info("All map positions cleared");
                    return SINGLE_SUCCESS;
                })
            )
        );
    }

    private int setLocation(CommandContext<CommandSource> context, String type, boolean withCoords) {
        if (mc.player == null) return -1;

        BlockPos pos = withCoords ?
            context.getArgument("pos", PosArgument.class).toAbsoluteBlockPos(mc.player.getCommandSource()) :
            mc.player.getBlockPos();

        switch (type) {
            case "platformOrigin" -> QPrintAddon.printModule.platformOrigin.set(posToVec(pos));
            case "chestP1" -> QPrintAddon.printModule.storageP0.set(posToVec(pos));
            case "chestP2" -> QPrintAddon.printModule.storageP1.set(posToVec(pos));
        }

        info("Set location " + type + " to " + pos.toShortString());

        return SINGLE_SUCCESS;
    }
}
