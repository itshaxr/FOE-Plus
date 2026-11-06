package dannypx.foe.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dannypx.foe.FishOnMCExtras;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.ChatHandler;
import dannypx.foe.handler.fetch.StatsScreenHandler;
import dannypx.foe.handler.io.DataFileHandler;
import dannypx.foe.handler.logic.TimerHandler;
import dannypx.foe.handler.store.*;
import dannypx.foe.helper.GlyphHelper;
import dannypx.foe.helper.ItemStackHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.screens.MainScreen;
import dannypx.foe.type.custom_value.*;
import dannypx.foe.type.tracker.TrackerAction;
import dannypx.foe.type.tracker.TrackerType;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public class CommandRegistry {
    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                command("foe")
                        .then(command("config").executes(Command.Foe::openConfig))
                        .then(command("main").executes(Command.Foe::openMainScreen))
                        .then(command("users").executes(context -> {
                            dannypx.foe.handler.logic.TextDisplayHandler.instance().printDetectedUsers();
                            return 1;
                        }))
                        .then(command("export_placeholder_data").executes(Command.Foe::exportPlaceholderData))
                        .then(command("stats")
                                .then(command("import").executes(Command.Stats::importStats))
                                .then(command("cancel").executes(Command.Stats::cancelStats))
                                .then(command("reset").executes(Command.Stats::resetStats))
                        )
                        .then(command("friend")
                                .then(command("add").then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            if (Minecraft.getInstance().getConnection() != null) {
                                                Minecraft.getInstance().getConnection().getOnlinePlayers().forEach(p -> builder.suggest(p.getProfile().name()));
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(Command.Friend::add)))
                                .then(command("remove").then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            dannypx.foe.handler.logic.FriendsHandler.instance().all().values().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(Command.Friend::remove)))
                                .then(command("list").executes(Command.Friend::list))
                        )
                        .then(command("item").executes(Command.Foe::copyHeldItem))
                        .then(command("crew")
                                .then(command("import").executes(Command.Crew::importCrew))
                                .then(command("cancel").executes(Command.Crew::cancelCrew))
                                .then(command("dump").executes(Command.Crew::dumpCrew))
                        )
                        .then(command("reset_to_defaults")
                                .then(command("button").executes(Command.Reset::resetButton))
                                .then(command("chat_trigger").executes(Command.Reset::resetChatTrigger))
                                .then(command("event_trigger").executes(Command.Reset::resetEventTrigger))
                                .then(command("notification").executes(Command.Reset::resetNotification))
                                .then(command("chat_notification").executes(Command.Reset::resetChatNotification))
                                .then(command("timer").executes(Command.Reset::resetTimer))
                                .then(command("hud").executes(Command.Reset::resetHud))
                                .then(command("tracker").executes(Command.Reset::resetTracker))
                                .then(command("snippet").executes(Command.Reset::resetSnippet))
                        )
                        .then(command("fix_defaults")
                                .then(command("chat_trigger").executes(Command.Fix::fixChatTrigger))
                                .then(command("event_trigger").executes(Command.Fix::fixEventTrigger))
                                .then(command("notification").executes(Command.Fix::fixNotification))
                                .then(command("chat_notification").executes(Command.Fix::fixChatNotification))
                                .then(command("timer").executes(Command.Fix::fixTimer))
                                .then(command("hud").executes(Command.Fix::fixHud))
                                .then(command("tracker").executes(Command.Fix::fixTracker))
                                .then(command("snippet").executes(Command.Fix::fixSnippet))
                        )
                        .then(command("toggle")
                                .then(command("render")
                                        .then(command("armor").executes(Command.Toggle::toggleArmor))
                                        .then(command("pet").executes(Command.Toggle::togglePet))
                                        .then(command("pet_names").executes(Command.Toggle::togglePetNames))
                                        .then(command("name_plates").executes(Command.Toggle::toggleNamePlates))
                                        .then(command("fishingHook_model").executes(Command.Toggle::toggleFishingHookModel))
                                        .then(command("bait_on_fishing_hook").executes(Command.Toggle::toggleBaitOnFishingHook))
                                        .then(command("hold_fish_first_person").executes(Command.Toggle::toggleHoldFishFirstPerson))
                                        .then(command("hold_fish_third_person").executes(Command.Toggle::toggleHoldFishThirdPerson))
                                )
                        )
                        .then(command("tracker")
                                .then(command("set")
                                        .then(ClientCommandManager.argument("tracker", StringArgumentType.string()).then(
                                                ClientCommandManager.argument("value", StringArgumentType.string())
                                                        .executes(Command.DataTracker::setValue)
                                        ))
                                )
                                .then(command("toggle")
                                        .then(ClientCommandManager.argument("tracker", StringArgumentType.string())
                                                .executes(Command.DataTracker::toggleValue)
                                        )
                                )
                                .then(command("add")
                                        .then(ClientCommandManager.argument("tracker", StringArgumentType.string()).then(
                                                ClientCommandManager.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(Command.DataTracker::addValue)
                                        ))
                                )
                                .then(command("subtract")
                                        .then(ClientCommandManager.argument("tracker", StringArgumentType.string()).then(
                                                ClientCommandManager.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(Command.DataTracker::subtractValue)
                                        ))
                                )
                        )
                        .then(command("stats_data")
                                .then(command("set")
                                        .then(command("fish")
                                                .then(command("total")
                                                        .then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                context -> Command.DataStats.updateTotal(context, "fish")
                                                        ))
                                                )
                                                .then(command("size")
                                                        .then(ClientCommandManager.argument("field", StringArgumentType.string())
                                                                .then(command("amount").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                        context -> Command.DataStats.updateField(context, "fish", "size", "amount")
                                                                )))
                                                                .then(command("caught_on").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                        context -> Command.DataStats.updateField(context, "fish", "size", "caught_on")
                                                                )))
                                                        )
                                                )
                                                .then(command("variant")
                                                        .then(ClientCommandManager.argument("field", StringArgumentType.string())
                                                                .then(command("amount").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                        context -> Command.DataStats.updateField(context, "fish", "variant", "amount")
                                                                )))
                                                                .then(command("caught_on").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                        context -> Command.DataStats.updateField(context, "fish", "variant", "caught_on")
                                                                )))
                                                        )
                                                )
                                                .then(command("rarity")
                                                        .then(ClientCommandManager.argument("field", StringArgumentType.string())
                                                                .then(command("amount").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                        context -> Command.DataStats.updateField(context, "fish", "rarity", "amount")
                                                                )))
                                                                .then(command("caught_on").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                        context -> Command.DataStats.updateField(context, "fish", "rarity", "caught_on")
                                                                )))
                                                        )
                                                )
                                        )
                                        .then(command("pet")
                                                .then(command("total")
                                                        .then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(context -> Command.DataStats.updateTotal(context, "pet")))
                                                )
                                                .then(command("rarity")
                                                        .then(ClientCommandManager.argument("field", StringArgumentType.string())
                                                                .then(command("amount").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                        context -> Command.DataStats.updateField(context, "pet", "rarity", "amount")
                                                                )))
                                                                .then(command("caught_on").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                        context -> Command.DataStats.updateField(context, "pet", "rarity", "caught_on")
                                                                )))
                                                        )
                                                )
                                                .then(command("rating")
                                                        .then(ClientCommandManager.argument("field", StringArgumentType.string())
                                                                .then(command("amount").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                        context -> Command.DataStats.updateField(context, "pet", "rating", "amount")
                                                                )))
                                                                .then(command("caught_on").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                        context -> Command.DataStats.updateField(context, "pet", "rating", "caught_on")
                                                                )))
                                                        )
                                                )
                                        )
                                        .then(command("item")
                                                .then(ClientCommandManager.argument("field", StringArgumentType.string())
                                                        .then(command("amount").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                context -> Command.DataStats.updateField(context, "item","amount")
                                                        )))
                                                        .then(command("caught_on").then(ClientCommandManager.argument("value", IntegerArgumentType.integer()).executes(
                                                                context -> Command.DataStats.updateField(context, "item","caught_on")
                                                        )))
                                                )
                                        )
                                )
                        )
                        .then(command("get_color")
                                .then(ClientCommandManager.argument("glyph", StringArgumentType.string())
                                        .executes(Command.Helper::getColor))
                        )
                        .executes(Command.Foe::openMainScreen)
        );
    }

    private static class Command {
        static class Foe {
            /// Copies the held item's data (name, lore and FOE's custom data) to the clipboard, so
            /// it can be shared when something about an item needs fixing.
            public static int copyHeldItem(CommandContext<FabricClientCommandSource> context) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return 0;
                ItemStack stack = mc.player.getMainHandItem();
                if (stack.isEmpty()) {
                    dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("ITEM", dannypx.foe.helper.FoeChat.RED).append(dannypx.foe.helper.FoeChat.text("Hold the item first.")));
                    return 1;
                }
                StringBuilder out = new StringBuilder();
                out.append("Name: ").append(stack.getHoverName().getString()).append('\n');
                var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
                if (lore != null) lore.lines().forEach(l -> out.append("Lore: ").append(l.getString()).append('\n'));
                var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                out.append("Data: ").append(data == null ? "(none)" : data.copyTag().toString());
                mc.keyboardHandler.setClipboard(out.toString());
                dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("ITEM", dannypx.foe.helper.FoeChat.GREEN)
                        .append(stack.getHoverName().copy()).append(dannypx.foe.helper.FoeChat.text(" copied to your clipboard")));
                return 1;
            }

            public static int openConfig(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(() -> ConfigApiJava.INSTANCE.openScreen(FishOnMCExtras.MOD_ID));
            }

            public static int openMainScreen(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(() -> Minecraft.getInstance().setScreen(new dannypx.foe.screens.FoeMainScreen(Minecraft.getInstance().screen)));
            }

            public static int exportPlaceholderData(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Exported placeholder schema and list to file").withStyle(ChatFormatting.GREEN), () -> DataFileHandler.instance().saveSchemaToFile());
            }
        }

        static class Stats {
            public static int importStats(CommandContext<FabricClientCommandSource> context) {
                StatsScreenHandler.instance().setImportStats(true);
                return executeCommand(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.connection.sendCommand("stats");
                    }
                });
            }

            public static int cancelStats(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(() -> ProfileDataHandler.instance().updateImportStats(true));
            }

            public static int resetStats(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(() -> StatsDataHandler.instance().resetStats());
            }
        }

        static class Friend {
            private static void say(Component line) {
                dannypx.foe.helper.FoeChat.send(line);
            }

            public static int add(CommandContext<FabricClientCommandSource> context) {
                String name = StringArgumentType.getString(context, "name");
                String added = dannypx.foe.handler.logic.FriendsHandler.instance().add(name);
                say(added == null
                        ? dannypx.foe.helper.FoeChat.line("FRIEND", dannypx.foe.helper.FoeChat.RED).append(dannypx.foe.helper.FoeChat.white(name)).append(dannypx.foe.helper.FoeChat.text(" isn't online. Add them while they're on."))
                        : dannypx.foe.helper.FoeChat.line("FRIEND", dannypx.foe.helper.FoeChat.AQUA).append(dannypx.foe.helper.FoeChat.white(added)).append(dannypx.foe.helper.FoeChat.text(" added to your friends")));
                return 1;
            }

            public static int remove(CommandContext<FabricClientCommandSource> context) {
                String name = StringArgumentType.getString(context, "name");
                String removed = dannypx.foe.handler.logic.FriendsHandler.instance().remove(name);
                say(removed == null
                        ? dannypx.foe.helper.FoeChat.line("FRIEND", dannypx.foe.helper.FoeChat.RED).append(dannypx.foe.helper.FoeChat.white(name)).append(dannypx.foe.helper.FoeChat.text(" isn't on your friends list"))
                        : dannypx.foe.helper.FoeChat.line("FRIEND", dannypx.foe.helper.FoeChat.AQUA).append(dannypx.foe.helper.FoeChat.white(removed)).append(dannypx.foe.helper.FoeChat.text(" removed from your friends")));
                return 1;
            }

            public static int list(CommandContext<FabricClientCommandSource> context) {
                var friends = dannypx.foe.handler.logic.FriendsHandler.instance().all();
                var line = dannypx.foe.helper.FoeChat.line("FRIENDS", dannypx.foe.helper.FoeChat.AQUA);
                if (friends.isEmpty()) {
                    line.append(dannypx.foe.helper.FoeChat.text("None yet. Add one with /foe friend add <name>"));
                } else {
                    boolean first = true;
                    for (var e : friends.entrySet()) {
                        if (!first) line.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                        first = false;
                        line.append(Component.literal(e.getValue()).withStyle(dannypx.foe.handler.logic.FriendsHandler.isOnline(e.getKey()) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                    }
                }
                say(line);
                return 1;
            }
        }

        static class Crew {
            public static int importCrew(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.connection.sendCommand("c info");
                    }
                });
            }

            /// Copies what the last /crew screen held (heads, names, lore, profiles) to the clipboard.
            public static int dumpCrew(CommandContext<FabricClientCommandSource> context) {
                String dump = dannypx.foe.handler.fetch.CrewScreenHandler.instance().lastDump();
                if (dump.isEmpty()) {
                    dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("CREW", dannypx.foe.helper.FoeChat.RED)
                            .append(dannypx.foe.helper.FoeChat.text("Open /crew first, then run this again.")));
                    return 1;
                }
                Minecraft.getInstance().keyboardHandler.setClipboard(dump);
                dannypx.foe.helper.FoeChat.send(dannypx.foe.helper.FoeChat.line("CREW", dannypx.foe.helper.FoeChat.GREEN)
                        .append(dannypx.foe.helper.FoeChat.text("/crew details copied to your clipboard")));
                return 1;
            }

            public static int cancelCrew(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(() -> ProfileDataHandler.instance().updateImportCrew(true));
            }
        }

        static class Reset {
            public static int resetButton(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Reset buttons to default config").withStyle(ChatFormatting.GREEN), () -> CustomButtonDataHandler.instance().resetButtons());
            }

            public static int resetChatTrigger(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Reset chat triggers to default config").withStyle(ChatFormatting.GREEN), () -> {
                    CustomChatTriggerDataHandler.instance().resetChatTriggers();
                    ChatHandler.instance().initChatTrigger();
                });
            }

            public static int resetEventTrigger(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Reset event triggers to default config").withStyle(ChatFormatting.GREEN), () -> {
                    CustomEventTriggerDataHandler.instance().resetEventTrigger();
                });
            }

            public static int resetNotification(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Reset notifications to default config").withStyle(ChatFormatting.GREEN), () ->
                        CustomNotificationDataHandler.instance().resetNotifications());
            }

            public static int resetChatNotification(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Reset chat notifications to default config").withStyle(ChatFormatting.GREEN), () ->
                        CustomChatNotificationDataHandler.instance().resetChatNotifications());
            }

            public static int resetTimer(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Reset timers to default config").withStyle(ChatFormatting.GREEN), () -> {
                    CustomTimerDataHandler.instance().resetTimers();
                    TimerHandler.instance().initTimers();
                });
            }

            public static int resetHud(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Reset HUDs to default config").withStyle(ChatFormatting.GREEN), () -> CustomHudDataHandler.instance().resetHuds());
            }

            public static int resetTracker(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Reset trackers to default config").withStyle(ChatFormatting.GREEN), () -> CustomTrackerDataHandler.instance().resetTrackers());
            }

            public static int resetSnippet(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Reset snippets to default config").withStyle(ChatFormatting.GREEN), () -> CustomSnippetDataHandler.instance().resetSnippets());
            }
        }

        static class Fix {
            public static int fixChatTrigger(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Fixed default chat triggers").withStyle(ChatFormatting.GREEN), () -> {
                    CustomChatTriggerDataHandler.instance().fixDefault();
                    ChatHandler.instance().initChatTrigger();
                });
            }

            public static int fixEventTrigger(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Fixed default event triggers").withStyle(ChatFormatting.GREEN), () -> {
                    CustomEventTriggerDataHandler.instance().fixDefault();
                });
            }

            public static int fixNotification(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Fixed default notifications").withStyle(ChatFormatting.GREEN), () ->
                        CustomNotificationDataHandler.instance().fixDefault());
            }

            public static int fixChatNotification(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Fixed default chat notifications").withStyle(ChatFormatting.GREEN), () ->
                        CustomChatNotificationDataHandler.instance().fixDefault());
            }

            public static int fixTimer(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Fixed default timers").withStyle(ChatFormatting.GREEN), () -> {
                    CustomTimerDataHandler.instance().fixDefault();
                    TimerHandler.instance().initTimers();
                });
            }

            public static int fixHud(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Fixed default HUDs").withStyle(ChatFormatting.GREEN), () -> CustomHudDataHandler.instance().fixDefault());
            }

            public static int fixTracker(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Fixed default trackers").withStyle(ChatFormatting.GREEN), () -> CustomTrackerDataHandler.instance().fixDefault());
            }

            public static int fixSnippet(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Fixed default snippets").withStyle(ChatFormatting.GREEN), () -> CustomSnippetDataHandler.instance().fixDefault());
            }
        }

        static class Toggle {
            public static int toggleArmor(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Toggled Armor"), () -> {
                    Configs.rendererConfig.hideArmor.accept(!Configs.rendererConfig.hideArmor.get());
                    Configs.rendererConfig.save();
                });
            }

            public static int togglePet(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Toggled Pets"), () -> {
                    Configs.rendererConfig.showPet.accept(!Configs.rendererConfig.showPet.get());
                    Configs.rendererConfig.save();
                });
            }

            public static int togglePetNames(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Toggled Pet Names"), () -> {
                    Configs.rendererConfig.showPetName.accept(!Configs.rendererConfig.showPetName.get());
                    Configs.rendererConfig.save();
                });
            }

            public static int toggleNamePlates(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Toggled Name Plates"), () -> {
                    Configs.rendererConfig.showPlayerNamePlate.accept(!Configs.rendererConfig.showPlayerNamePlate.get());
                    Configs.rendererConfig.save();
                });
            }

            public static int toggleFishingHookModel(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Toggled Fishing Hook Model"), () -> {
                    Configs.rendererConfig.showNewFishingHook.accept(!Configs.rendererConfig.showNewFishingHook.get());
                    Configs.rendererConfig.save();
                });
            }

            public static int toggleBaitOnFishingHook(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Toggled Bait on Fishing Hook"), () -> {
                    Configs.rendererConfig.showBaitOnFishingHook.accept(!Configs.rendererConfig.showBaitOnFishingHook.get());
                    Configs.rendererConfig.save();
                });
            }

            public static int toggleHoldFishFirstPerson(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Toggled Hold Fish in First Person"), () -> {
                    Configs.rendererConfig.useBothHandsHoldingFishFirstPerson.accept(!Configs.rendererConfig.useBothHandsHoldingFishFirstPerson.get());
                    Configs.rendererConfig.save();
                });
            }

            public static int toggleHoldFishThirdPerson(CommandContext<FabricClientCommandSource> context) {
                return executeCommand(context, Component.literal("Toggled Hold Fish in Third Person"), () -> {
                    Configs.rendererConfig.useBothHandsHoldingFishThirdPerson.accept(!Configs.rendererConfig.useBothHandsHoldingFishThirdPerson.get());
                    Configs.rendererConfig.save();
                });
            }
        }

        static class DataTracker {
            public static int setValue(CommandContext<FabricClientCommandSource> context) {
                String tracker = StringArgumentType.getString(context, "tracker");
                String value = StringArgumentType.getString(context, "value");

                if(CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.containsKey(tracker)) {
                    if (("true".equals(value) || "false".equals(value))
                            && CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.get(tracker).getTrackerType() == TrackerType.BOOLEAN
                    ) {
                        boolean parsed = Boolean.parseBoolean(value);
                        TrackerValue trackerValue = BooleanValue.of(parsed);
                        return updateValue(context, TrackerAction.SET, tracker, trackerValue);
                    }

                    try {
                        if(CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.get(tracker).getTrackerType() == TrackerType.INTEGER) {
                            float parsed = Float.parseFloat(value);
                            TrackerValue trackerValue = NumberValue.of(parsed);
                            return updateValue(context, TrackerAction.SET, tracker, trackerValue);
                        }

                    } catch (Exception ignored) {}

                    if(!value.isBlank()
                            && CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.get(tracker).getTrackerType() == TrackerType.ITEMSTACK
                    ) {
                        ItemStack itemStack = ItemStackHelper.valueOf(value);

                        if(!itemStack.isEmpty()) {
                            TrackerValue trackerValue = ItemStackValue.of(itemStack);
                            return updateValue(context, TrackerAction.SET, tracker, trackerValue);
                        } else {
                            try {
                                Float index = Float.valueOf(value);
                                Minecraft minecraft = Minecraft.getInstance();

                                if(index >= 0) {
                                    if(minecraft.screen instanceof ContainerScreen genericContainerScreen) {
                                        TrackerValue trackerValue = ItemStackValue.of(genericContainerScreen.getMenu().slots.get(index.intValue()).getItem());
                                        return updateValue(context, TrackerAction.SET, tracker, trackerValue);
                                    } else {
                                        TrackerValue trackerValue = ItemStackValue.of(minecraft.player.getInventory().getItem(index.intValue()));
                                        return updateValue(context, TrackerAction.SET, tracker, trackerValue);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    return sendFeedback(context, Component.literal("Could not parse value").withStyle(ChatFormatting.RED));
                } else {
                    return sendFeedback(context, Component.literal("Could not find tracker").withStyle(ChatFormatting.RED));
                }
            }

            public static int toggleValue(CommandContext<FabricClientCommandSource> context) {
                String tracker = StringArgumentType.getString(context, "tracker");

                if(CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.containsKey(tracker)) {
                    if(CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.get(tracker).getTrackerType() == TrackerType.BOOLEAN) {
                        return updateValue(context, TrackerAction.TOGGLE, tracker, EmptyValue.getDefault());
                    } else {
                        return sendFeedback(context, Component.literal("Tracker is not a boolean").withStyle(ChatFormatting.RED));
                    }
                } else {
                    return sendFeedback(context, Component.literal("Could not find tracker").withStyle(ChatFormatting.RED));
                }
            }

            public static int addValue(CommandContext<FabricClientCommandSource> context) {
                String tracker = StringArgumentType.getString(context, "tracker");
                int value = IntegerArgumentType.getInteger(context, "value");

                if(CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.containsKey(tracker)) {
                    if(CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.get(tracker).getTrackerType() == TrackerType.INTEGER) {
                        TrackerValue trackerValue = NumberValue.of((float) value);
                        return updateValue(context, TrackerAction.ADD, tracker, trackerValue);
                    } else {
                        return sendFeedback(context, Component.literal("Tracker must be a integer").withStyle(ChatFormatting.RED));
                    }
                } else {
                    return sendFeedback(context, Component.literal("Could not find tracker").withStyle(ChatFormatting.RED));
                }
            }

            public static int subtractValue(CommandContext<FabricClientCommandSource> context) {
                String tracker = StringArgumentType.getString(context, "tracker");
                int value = IntegerArgumentType.getInteger(context, "value");

                if(CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.containsKey(tracker)) {
                    if(CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.get(tracker).getTrackerType() == TrackerType.INTEGER) {
                        TrackerValue trackerValue = NumberValue.of((float) value);
                        return updateValue(context, TrackerAction.SUBTRACT, tracker, trackerValue);
                    } else {
                        return sendFeedback(context, Component.literal("Tracker must be a integer").withStyle(ChatFormatting.RED));
                    }
                } else {
                    return sendFeedback(context, Component.literal("Could not find tracker").withStyle(ChatFormatting.RED));
                }
            }

            private static int updateValue(CommandContext<FabricClientCommandSource> context, TrackerAction action, String tracker, TrackerValue value) {
                return switch (value) {
                    case BooleanValue booleanValue -> switch (action) {
                        case SET -> executeCommand(context, Component.literal("Set " + tracker + " to " + booleanValue.value()), () -> {
                            CustomTrackerDataHandler.instance().updateTracker(tracker, action, booleanValue);
                        });
                        default -> sendFeedback(context, Component.literal("Error").withStyle(ChatFormatting.RED));
                    };
                    case NumberValue numberValue -> switch (action) {
                        case SET -> executeCommand(context, Component.literal("Set " + tracker + " to " + numberValue.value()), () -> {
                            CustomTrackerDataHandler.instance().updateTracker(tracker, action, numberValue);
                        });
                        case ADD -> executeCommand(context, Component.literal("Add " + numberValue.value() + " to " + tracker), () -> {
                            CustomTrackerDataHandler.instance().updateTracker(tracker, action, numberValue);
                        });
                        case SUBTRACT -> executeCommand(context, Component.literal("Subtract " + numberValue.value() + " to " + tracker), () -> {
                            CustomTrackerDataHandler.instance().updateTracker(tracker, action, numberValue);
                        });
                        default -> sendFeedback(context, Component.literal("Error").withStyle(ChatFormatting.RED));
                    };
                    case ItemStackValue itemStackValue -> switch (action) {
                        case SET -> executeCommand(context, Component.literal("Set " + tracker + " to ").append(Objects.requireNonNull(itemStackValue.value().value1().getHoverName())), () -> {
                            CustomTrackerDataHandler.instance().updateTracker(tracker, action, itemStackValue);
                        });
                        default -> sendFeedback(context, Component.literal("Error").withStyle(ChatFormatting.RED));
                    };
                    case EmptyValue ignored -> switch (action) {
                        case TOGGLE -> executeCommand(context, Component.literal("Toggle " + tracker), () -> {
                            CustomTrackerDataHandler.instance().updateTracker(tracker, action, EmptyValue.getDefault());
                        });
                        default -> sendFeedback(context, Component.literal("Error").withStyle(ChatFormatting.RED));
                    };
                    default -> sendFeedback(context, Component.literal("Error").withStyle(ChatFormatting.RED));
                };
            }
        }

        static class DataStats {
            public static int updateTotal(CommandContext<FabricClientCommandSource> context, String set) {
                int value = IntegerArgumentType.getInteger(context, "value");
                return executeCommand(context, Component.literal("Updated " + set + " total to " + value), () -> {
                    StatsDataHandler.instance().updateData(set, value);
                });
            }

            public static int updateField(CommandContext<FabricClientCommandSource> context, String set, String category, String type) {
                String field = StringArgumentType.getString(context, "field");
                int value = IntegerArgumentType.getInteger(context, "value");

                return switch (set) {
                    case "fish" -> {
                        Map<String, Map<String, StatsDataHandler.Stat<Integer, Integer>>> fishData = StatsDataHandler.instance().getStatsData().fishData;
                        Map<String, StatsDataHandler.Stat<Integer, Integer>> statsData = fishData.get(category);
                        if(statsData.containsKey(field)) {
                            yield executeCommand(context, Component.literal("Updated " + category + " " + field + " " + type + " to " + value), () ->
                                    StatsDataHandler.instance().updateData(set, category, field, type, value));
                        } else yield sendFeedback(context, Component.literal("Field does not exist").withStyle(ChatFormatting.RED));
                    }
                    case "pet" -> {
                        Map<String, Map<String, StatsDataHandler.Stat<Integer, Integer>>> petData = StatsDataHandler.instance().getStatsData().petData;
                        Map<String, StatsDataHandler.Stat<Integer, Integer>> statsData = petData.get(category);
                        if(statsData.containsKey(field)) {
                            yield executeCommand(context, Component.literal("Updated " + category + " " + field + " " + type + " to " + value), () ->
                                    StatsDataHandler.instance().updateData(set, category, field, type, value));
                        } else yield sendFeedback(context, Component.literal("Field does not exist").withStyle(ChatFormatting.RED));
                    }
                    default -> sendFeedback(context, Component.literal("Error").withStyle(ChatFormatting.RED));
                };
            }

            public static int updateField(CommandContext<FabricClientCommandSource> context, String set, String type) {
                String field = StringArgumentType.getString(context, "field");
                int value = IntegerArgumentType.getInteger(context, "value");

                Map<String, StatsDataHandler.Stat<Integer, Integer>> statsData = StatsDataHandler.instance().getStatsData().itemData;
                if(statsData.containsKey(field)) {
                    return executeCommand(context, Component.literal("Updated " + field + " " + type + " to " + value), () ->
                            StatsDataHandler.instance().updateData(set, field, type, value));
                } else return sendFeedback(context, Component.literal("Field does not exist").withStyle(ChatFormatting.RED));
            }
        }

        static class Helper {
            public static int getColor(CommandContext<FabricClientCommandSource> context) {
                String glyph = StringArgumentType.getString(context, "glyph");
                int hex = GlyphHelper.getColor(glyph).orElse(-1);

                if(hex != -1) {
                    return sendFeedback(context, Component.literal(String.format("#%06X", hex & 0xFFFFFF)));
                } else {
                    return sendFeedback(context, Component.literal("No color found").withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    //region Command Builder
    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String command) {
        return ClientCommandManager.literal(command);
    }

    private static int executeCommand(CommandContext<FabricClientCommandSource> context, List<Component> feedback, ExecuteCallback executeCallback) {
        return executeCommand(context, TextHelper.concat(feedback.toArray(new Component[]{})), executeCallback);
    }

    private static int executeCommand(CommandContext<FabricClientCommandSource> context, String feedback, ExecuteCallback executeCallback) {
        return executeCommand(context, Component.literal(feedback), executeCallback);
    }

    private static int executeCommand(ExecuteCallback executeCallback) {
        Minecraft.getInstance().schedule(executeCallback::execute);
        return 1;
    }

    private static int executeCommand(CommandContext<FabricClientCommandSource> context, Component feedback, ExecuteCallback executeCallback) {
        Minecraft.getInstance().schedule(executeCallback::execute);
        return sendFeedback(context, feedback);
    }

    private static int sendFeedback(CommandContext<FabricClientCommandSource> context, Component feedback) {
        context.getSource().sendFeedback(
                TextHelper.concat(
                        Component.literal("FoER ").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD),
                        Component.literal("» ").withStyle(ChatFormatting.DARK_GRAY),
                        feedback
                )

        );
        return 1;
    }

    private interface ExecuteCallback {
        void execute();
    }
    //endregion
}
