package dannypx.foe.screens.element.hud;

import dannypx.foe.handler.logic.LoadingHandler;
import dannypx.foe.handler.logic.NotifierHandler;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.config.Configs;
import dannypx.foe.screens.element.Element;
import dannypx.foe.screens.element.NotificationElement;
import dannypx.foe.screens.interfaces.ScreenConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class NotifierElement extends Element implements ScreenConstants {
    //region Fields
    private final List<NotificationElement> notificationElements = new ArrayList<>();

    private static final int WIDTH = 200;
    //endregion

    public NotifierElement() {
        super(WIDTH,
                50,
                Configs.hudConfig.notifierElementXPosition.get() / 100f,
                Configs.hudConfig.notifierElementYPosition.get() / 100f,
                Configs.hudConfig.notifierElementAlignment.get(),
                Configs.hudConfig.notifierElementGroup.translation("Notifier Element"),
                false);
    }

    public NotifierElement(boolean isCopy) {
        super(WIDTH,
                50,
                Configs.hudConfig.notifierElementXPosition.get() / 100f,
                Configs.hudConfig.notifierElementYPosition.get() / 100f,
                Configs.hudConfig.notifierElementAlignment.get(),
                Configs.hudConfig.notifierElementGroup.translation("Notifier Element"),
                isCopy);
    }

    //region Methods
    @Override
    public void render(GuiGraphics guiGraphics) {
        int scaledWidth = (int) (Minecraft.getInstance().getWindow().getGuiScaledWidth() * (1 / dannypx.foe.utils.HudScale.crisp(Configs.hudConfig.notifierElementScale.get())));
        int scaledHeight = (int) (Minecraft.getInstance().getWindow().getGuiScaledHeight() * (1 / dannypx.foe.utils.HudScale.crisp(Configs.hudConfig.notifierElementScale.get())));

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(dannypx.foe.utils.HudScale.crisp(Configs.hudConfig.notifierElementScale.get()), dannypx.foe.utils.HudScale.crisp(Configs.hudConfig.notifierElementScale.get()));
        if(LoadingHandler.instance().isLoadingDone()
                && Configs.hudConfig.showNotifierElement.get()
        ) {
            // Position
            if(!isCopy) {
                xPos = Configs.hudConfig.notifierElementXPosition.get() / 100f;
                yPos = Configs.hudConfig.notifierElementYPosition.get() / 100f;
            }

            int x = switch (Configs.hudConfig.notifierElementAlignment.get()) {
                case TOP_LEFT, BOTTOM_LEFT -> Math.round(scaledWidth * xPos);
                case TOP_RIGHT, BOTTOM_RIGHT -> scaledWidth
                        - Math.round(scaledWidth * xPos);
                default -> 0;
            };

            int y = switch (Configs.hudConfig.notifierElementAlignment.get()) {
                case TOP_LEFT, TOP_RIGHT -> Math.round(scaledHeight * yPos);
                case BOTTOM_LEFT, BOTTOM_RIGHT -> scaledHeight
                        - Math.round(scaledHeight * yPos);
                default -> 0;
            };

            Pair<Integer, Integer> dimensions = this.assembleNotificationElements();

            x = switch (Configs.hudConfig.notifierElementAlignment.get()) {
                case TOP_RIGHT, BOTTOM_RIGHT -> x - dimensions.value1();
                default -> x;
            };

            y = switch (Configs.hudConfig.notifierElementAlignment.get()) {
                case BOTTOM_LEFT, BOTTOM_RIGHT -> y - dimensions.value2();
                default -> y;
            };

            this.renderNotifications(guiGraphics, x, y);
        }
        guiGraphics.pose().popMatrix();
    }

    private void renderNotifications(GuiGraphics guiGraphics, int x, int y) {
        AtomicInteger yTranslation = new AtomicInteger(y);
        for (NotificationElement notificationElement : notificationElements) {
            notificationElement.setX(x);
            notificationElement.setY(yTranslation.get());
            notificationElement.render(guiGraphics);

            yTranslation.addAndGet(notificationElement.height + PADDING_QUART);
        }
    }

    private Pair<Integer, Integer> assembleNotificationElements() {
        notificationElements.clear();
        NotifierHandler.instance().getNotifications().forEach(notification -> notificationElements.add(new NotificationElement(
                Minecraft.getInstance(),
                WIDTH,
                notification.item,
                notification.rows,
                notification.columns,
                notification.componentList
        )));

        return Pair.of(WIDTH, notificationElements.stream().mapToInt(n -> n.height).sum() + PADDING_QUART * notificationElements.size() - PADDING_QUART);
    }
    //endregion
}
