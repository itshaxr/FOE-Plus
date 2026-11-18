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
 * (AGPL-3.0), rewritten for Mojang mappings. The background drifts across each picture and fades into the next; F5 moves on to the next one.
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

    /// The background drifts slowly across each picture, then fades into the next, like a moving panorama.
    static final long SHOW_MS = 22_000L, FADE_MS = 2_500L;
    static final float ZOOM = 1.14f;
    private static int shown = -1;
    private static long shownSince;

    /// Latest ping of play.fishonmc.net; null while the first ping is running. Shared across
    /// title screen visits so it shows straight away when you come back.
    private static volatile dannypx.foe.helper.ServerPing.Result pingResult = null;
    private static volatile long lastPing = 0L;

    public FoeTitleScreen() {
        super(Component.literal("Title Screen"));
    }

    /// Pings on a background thread at most every 30 seconds.
    private static void pingIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastPing < 30_000L) return;
        lastPing = now;
        Thread thread = new Thread(() -> pingResult = dannypx.foe.helper.ServerPing.ping(SERVER_ADDRESS, 5000), "FOE title screen ping");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void tick() {
        super.tick();
        pingIfNeeded();
    }

    @Override
    protected void init() {
        if (shown < 0) { shown = new Random().nextInt(BACKGROUNDS.size()); shownSince = System.currentTimeMillis(); }

        pingIfNeeded();

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

        dannypx.foe.helper.ServerPing.Result result = pingResult;

        Component line;
        if (result == null) {
            line = Component.literal("Pinging FishOnMC...").withStyle(ChatFormatting.GRAY);
        } else if (!result.reachable()) {
            line = Component.literal("Can't reach FishOnMC right now.").withStyle(ChatFormatting.RED);
        } else {
            line = Component.literal("There are ")
                    .append(Component.literal(String.valueOf(result.online())).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW))
                    .append(Component.literal(" players online."));
        }
        guiGraphics.drawCenteredString(this.font, line, centerX, textY, 0xFFFFFFFF);

        Identifier pingIcon;
        if (result == null) pingIcon = null;
        else if (!result.reachable()) pingIcon = UNREACHABLE_TEXTURE;
        else if (result.pingMs() < 50L) pingIcon = PING_5_TEXTURE;
        else if (result.pingMs() < 100L) pingIcon = PING_4_TEXTURE;
        else if (result.pingMs() < 175L) pingIcon = PING_3_TEXTURE;
        else if (result.pingMs() < 300L) pingIcon = PING_2_TEXTURE;
        else pingIcon = PING_1_TEXTURE;

        if (pingIcon != null) {
            // Inside the right end of the Join button.
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, pingIcon,
                    columnLeft() + BUTTON_FULL_WIDTH - 10 - 8, height / 2 + 6, 10, 8);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (shown < 0) { shown = 0; shownSince = now; }
        // On to the next picture once this one has had its turn (the next began fading in before it ended).
        while (now - shownSince >= SHOW_MS) {
            shown = (shown + 1) % BACKGROUNDS.size();
            shownSince += SHOW_MS - FADE_MS;
        }
        drawPicture(guiGraphics, shown, now - shownSince, 1f);
        long fadeStart = shownSince + SHOW_MS - FADE_MS;
        if (now >= fadeStart) {
            drawPicture(guiGraphics, (shown + 1) % BACKGROUNDS.size(), now - fadeStart, fade(now - fadeStart));
        }
    }

    /// One picture, covering the screen a little zoomed in, at its place in the drift.
    private void drawPicture(GuiGraphics g, int index, long sinceMs, float alpha) {
        // Cover the screen at 16:9, cropping whichever side is too long, then zoom in so there is room to move.
        float drawWidth = width, drawHeight = width * 9f / 16f;
        if (drawHeight < height) { drawHeight = height; drawWidth = height * 16f / 9f; }
        drawWidth *= ZOOM; drawHeight *= ZOOM;
        float[] at = drift(sinceMs, index % 2 == 0);
        float ox = (drawWidth - width) * at[0], oy = (drawHeight - height) * at[1];
        int w = Math.round(drawWidth), h = Math.round(drawHeight);
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        g.pose().pushMatrix();
        g.pose().translate(-ox, -oy);
        g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUNDS.get(index), 0, 0, 0, 0, w, h, w, h, (a << 24) | 0xFFFFFF);
        g.pose().popMatrix();
    }

    /// Package-private for tests: where the view is across a picture (0-1 of the room to move, x and y),
    /// `sinceMs` into its turn: across from one side to the other (rightward or leftward), easing at both ends,
    /// with a gentle rise and fall.
    static float[] drift(long sinceMs, boolean rightward) {
        float t = Math.max(0f, Math.min(1f, sinceMs / (float) SHOW_MS));
        float e = t * t * (3f - 2f * t);
        float x = rightward ? e : 1f - e;
        float y = 0.5f + 0.35f * (float) Math.sin(t * Math.PI * 2.0) * 0.5f;
        return new float[]{x, y};
    }

    /// Package-private for tests: how far the next picture has faded in (0-1), `sinceMs` into the fade.
    static float fade(long sinceMs) {
        float t = Math.max(0f, Math.min(1f, sinceMs / (float) FADE_MS));
        return t * t * (3f - 2f * t);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) return true;
        if (event.key() == GLFW.GLFW_KEY_F5) {
            // On to the next picture: it starts fading in now.
            long now = System.currentTimeMillis();
            if (now - shownSince < SHOW_MS - FADE_MS) shownSince = now - (SHOW_MS - FADE_MS);
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
