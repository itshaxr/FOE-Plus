package dannypx.foe.screens;

import dannypx.foe.FishOnMCExtras;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

/**
 * The FOE title screen: a FishOnMC background, the logo, a "Join FishOnMC" button with the live
 * player count and ping, and the usual menu buttons. Ported from FOECollab's FoETitleScreen
 * (AGPL-3.0), rewritten for Mojang mappings. Press F5 to pick a new background.
 */
public class FoeTitleScreen extends Screen {
    private static final String SERVER_ADDRESS = "play.fishonmc.net";

    private static final Identifier UNREACHABLE_TEXTURE = Identifier.withDefaultNamespace("server_list/unreachable");
    private static final Identifier PING_1_TEXTURE = Identifier.withDefaultNamespace("server_list/ping_1");
    private static final Identifier PING_2_TEXTURE = Identifier.withDefaultNamespace("server_list/ping_2");
    private static final Identifier PING_3_TEXTURE = Identifier.withDefaultNamespace("server_list/ping_3");
    private static final Identifier PING_4_TEXTURE = Identifier.withDefaultNamespace("server_list/ping_4");
    private static final Identifier PING_5_TEXTURE = Identifier.withDefaultNamespace("server_list/ping_5");
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "title/icon");
    private static final List<Identifier> BACKGROUNDS = List.of(
            Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/title/title_1.png"),
            Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/title/title_2.png"),
            Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/gui/title/title_3.png"));

    private static final Component JOIN_SERVER = Component.literal("Join FishOnMC");
    private static final Component SINGLEPLAYER = Component.literal("Singleplayer");
    private static final Component MULTIPLAYER = Component.literal("Multiplayer");
    private static final Component MODS = Component.literal("Mods");
    private static final Component OPTIONS = Component.literal("Options");
    private static final Component QUIT = Component.literal("Quit Game");
    private static final Component COPYRIGHT = Component.translatable("title.credits");

    private static final int BUTTON_FULL_WIDTH = 180;
    private static final int BUTTON_HALF_WIDTH = 89;
    private static final int BUTTON_SPLIT = (BUTTON_FULL_WIDTH / 2) - BUTTON_HALF_WIDTH;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_LINE_HEIGHT = 4 + BUTTON_HEIGHT;

    private static Identifier background;

    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private ServerData serverData;

    public FoeTitleScreen() {
        super(Component.literal("Title Screen"));
    }

    @Override
    public void tick() {
        super.tick();
        pinger.tick();
    }

    @Override
    protected void init() {
        if (background == null) background = BACKGROUNDS.get(new Random().nextInt(BACKGROUNDS.size()));

        serverData = new ServerData("FishOnMC", SERVER_ADDRESS, ServerData.Type.OTHER);
        boolean nativeTransport = this.minecraft.options.useNativeTransport();
        Thread pingThread = new Thread(() -> {
            try {
                pinger.pingServer(serverData, () -> {}, () -> {}, EventLoopGroupHolder.remote(nativeTransport));
            } catch (Exception e) {
                serverData.setState(ServerData.State.UNREACHABLE);
                serverData.ping = -1L;
            }
        }, "FOE title screen ping");
        pingThread.setDaemon(true);
        pingThread.start();

        this.initButtons();
    }

    private int columnLeft() {
        return Math.max(width / 4 - BUTTON_FULL_WIDTH / 2, 40);
    }

    private int columnRightHalf() {
        return Math.max(width / 4 + BUTTON_SPLIT, 40 + BUTTON_HALF_WIDTH + BUTTON_SPLIT * 2);
    }

    private void initButtons() {
        int y = height / 2;

        this.addRenderableWidget(Button.builder(JOIN_SERVER, b -> {
            ServerData data = new ServerData("FishOnMC", SERVER_ADDRESS, ServerData.Type.OTHER);
            data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
            ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(SERVER_ADDRESS), data, false, null);
        }).bounds(columnLeft(), y, BUTTON_FULL_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(SINGLEPLAYER, b -> this.minecraft.setScreen(new SelectWorldScreen(this)))
                .bounds(columnLeft(), y + BUTTON_LINE_HEIGHT, BUTTON_HALF_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(MULTIPLAYER, b -> this.minecraft.setScreen(
                        this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this)))
                .bounds(columnRightHalf(), y + BUTTON_LINE_HEIGHT, BUTTON_HALF_WIDTH, BUTTON_HEIGHT).build());

        Button mods = Button.builder(MODS, b -> {
                    Screen modsScreen = createModMenuScreen();
                    if (modsScreen != null) this.minecraft.setScreen(modsScreen);
                })
                .bounds(columnLeft(), y + BUTTON_LINE_HEIGHT * 2, BUTTON_FULL_WIDTH, BUTTON_HEIGHT).build();
        mods.active = FabricLoader.getInstance().isModLoaded("modmenu");
        this.addRenderableWidget(mods);

        int bottomRow = (int) (y + BUTTON_LINE_HEIGHT * 3.5);
        this.addRenderableWidget(Button.builder(OPTIONS, b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)))
                .bounds(columnLeft(), bottomRow, BUTTON_HALF_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(QUIT, b -> this.minecraft.stop())
                .bounds(columnRightHalf(), bottomRow, BUTTON_HALF_WIDTH, BUTTON_HEIGHT).build());

        int copyrightWidth = this.font.width(COPYRIGHT);
        this.addRenderableWidget(new PlainTextButton(this.width - copyrightWidth - 2, this.height - 10, copyrightWidth, 10, COPYRIGHT,
                b -> this.minecraft.setScreen(new CreditsAndAttributionScreen(this)), this.font));
    }

    /// Mod Menu is optional, so open its screen by reflection instead of depending on it.
    private Screen createModMenuScreen() {
        try {
            Class<?> modsScreen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
            return (Screen) modsScreen.getConstructor(Screen.class).newInstance(this);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        this.renderLogo(guiGraphics);
        this.renderServerStatus(guiGraphics);
    }

    private void renderLogo(GuiGraphics guiGraphics) {
        int size = 512 / 4;
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOGO,
                Math.max(width / 4 - size / 2, 40 + (BUTTON_FULL_WIDTH - size) / 2), height / 2 - size, size, size);
    }

    private void renderServerStatus(GuiGraphics guiGraphics) {
        int centerX = Math.max(width / 4, 90 + 40);
        int textY = height / 2 - 11;

        Component line;
        if (serverData.state() == ServerData.State.SUCCESSFUL && serverData.players != null) {
            line = Component.literal("There are ")
                    .append(Component.literal(String.valueOf(serverData.players.online())).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW))
                    .append(Component.literal(" players online."));
        } else if (serverData.state() == ServerData.State.UNREACHABLE) {
            line = Component.literal("Can't reach FishOnMC right now.").withStyle(ChatFormatting.RED);
        } else {
            line = Component.literal("Pinging FishOnMC...").withStyle(ChatFormatting.GRAY);
        }
        guiGraphics.drawCenteredString(this.font, line, centerX, textY, 0xFFFFFFFF);

        Identifier pingIcon;
        if (serverData.state() == ServerData.State.UNREACHABLE || serverData.ping < 0L) pingIcon = UNREACHABLE_TEXTURE;
        else if (serverData.state() != ServerData.State.SUCCESSFUL) pingIcon = null;
        else if (serverData.ping < 50L) pingIcon = PING_5_TEXTURE;
        else if (serverData.ping < 100L) pingIcon = PING_4_TEXTURE;
        else if (serverData.ping < 175L) pingIcon = PING_3_TEXTURE;
        else if (serverData.ping < 300L) pingIcon = PING_2_TEXTURE;
        else pingIcon = PING_1_TEXTURE;

        if (pingIcon != null) {
            // Inside the right end of the Join button.
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, pingIcon,
                    columnLeft() + BUTTON_FULL_WIDTH - 10 - 8, height / 2 + 6, 10, 8);
        }
    }

    @Override
    public void removed() {
        super.removed();
        pinger.removeAll();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // Cover the screen at 16:9, cropping whichever side is too long.
        int drawWidth = width;
        int drawHeight = (int) (width * 9f / 16f);
        if (drawHeight < height) {
            drawHeight = height;
            drawWidth = (int) (height * 16f / 9f);
        }
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, background, 0, 0, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) return true;
        if (event.key() == GLFW.GLFW_KEY_F5) {
            background = BACKGROUNDS.get(new Random().nextInt(BACKGROUNDS.size()));
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
