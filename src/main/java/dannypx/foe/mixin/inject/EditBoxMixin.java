package dannypx.foe.mixin.inject;

import dannypx.foe.config.Configs;
import dannypx.foe.handler.logic.ConnectionHandler;
import dannypx.foe.handler.store.ProfileDataHandler;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.interfaces.ICrewInputHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditBox.class)
public abstract class EditBoxMixin extends AbstractWidget implements ICrewInputHolder {
    @Final
    @Shadow
    private Font font;

    @Unique
    private boolean chatInput;

    @Unique
    private int origX;

    @Unique
    private int origW;

    @Unique
    private boolean shifted;

    @Unique
    private final Component badge = Component.literal(TextHelper.smallCaps("crew"));

    @Shadow
    public abstract String getValue();

    public EditBoxMixin(int i, int j, int k, int l, Component component) {
        super(i, j, k, l, component);
    }

    @Unique
    private int badgeWidth() {
        return this.font.width(badge) + 6;
    }

    @Unique
    private boolean shouldShowBadge() {
        return this.chatInput
                && ProfileDataHandler.instance().getProfileData().isInCrewChat
                && !this.getValue().startsWith("/");
    }

    @Unique
    @Override
    public void foer$setChatInput(boolean v) {
        this.chatInput = v;
    }

    @Unique
    private void push() {
        if (!this.shouldShowBadge() || this.shifted) return;

        int off = this.badgeWidth() + 3;
        this.origX = this.getX();
        this.origW = this.getWidth();
        this.setX(this.origX + off);
        this.setWidth(this.origW - off);
        this.shifted = true;
    }

    @Unique
    private void pop() {
        if (!this.shifted) return;
        this.setX(this.origX);
        this.setWidth(this.origW);
        this.shifted = false;
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void injectRenderWidgetHead(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.editBoxMixinRenderWidgetHead.get()
                    && Configs.rendererConfig.showCrewChatMarker.get()
                    && ProfileDataHandler.instance().getProfileData().isInCrewChat
            ) {
                this.push();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("EditBoxMixin.injectRenderWidgetHead", foe$t);
        }
    }

    @Inject(method = "renderWidget", at = @At("RETURN"))
    private void injectRenderWidgetReturn(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.editBoxMixinRenderWidgetReturn.get()
                    && Configs.rendererConfig.showCrewChatMarker.get()
                    && ProfileDataHandler.instance().getProfileData().isInCrewChat
            ) {
                boolean was = this.shifted;
                this.pop();

                if (!was) return;

                int x = this.getX() - 2, y = this.getY() - 2;

                guiGraphics.fill(x, y, x + this.badgeWidth(), y + this.getHeight(), 0xFF4B724A);
                guiGraphics.drawString(this.font, this.badge, x + 3, getY(), CommonColors.WHITE, false);
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("EditBoxMixin.injectRenderWidgetReturn", foe$t);
        }
    }

    @Inject(method = "onClick", at = @At("HEAD"))
    private void injectOnClickHead(MouseButtonEvent mouseButtonEvent, boolean bl, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.editBoxMixinOnClickHead.get()
                    && Configs.rendererConfig.showCrewChatMarker.get()
                    && ProfileDataHandler.instance().getProfileData().isInCrewChat
            ) {
                this.push();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("EditBoxMixin.injectOnClickHead", foe$t);
        }
    }
    @Inject(method = "onClick", at = @At("RETURN"))
    private void injectOnClickReturn(MouseButtonEvent mouseButtonEvent, boolean bl, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.editBoxMixinOnClickReturn.get()
                    && Configs.rendererConfig.showCrewChatMarker.get()
                    && ProfileDataHandler.instance().getProfileData().isInCrewChat
            ) {
                this.pop();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("EditBoxMixin.injectOnClickReturn", foe$t);
        }
    }

    @Inject(method = "onDrag", at = @At("HEAD"))
    private void injectOnDragHead(MouseButtonEvent e, double dx, double dy, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.editBoxMixinOnDragHead.get()
                    && Configs.rendererConfig.showCrewChatMarker.get()
                    && ProfileDataHandler.instance().getProfileData().isInCrewChat
            ) {
                this.push();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("EditBoxMixin.injectOnDragHead", foe$t);
        }
    }
    @Inject(method = "onDrag", at = @At("RETURN"))
    private void injectOnDragReturn(MouseButtonEvent e, double dx, double dy, CallbackInfo ci) {
        try {
            if(ConnectionHandler.instance().isOnServer()
                    && Configs.mainConfig.enableMod.get()
                    && Configs.mixinConfig.editBoxMixinOnDragReturn.get()
                    && Configs.rendererConfig.showCrewChatMarker.get()
                    && ProfileDataHandler.instance().getProfileData().isInCrewChat
            ) {
                this.pop();
            }
    
        } catch (Throwable foe$t) {
            if (foe$t instanceof VirtualMachineError foe$e) throw foe$e;
            dannypx.foe.helper.Safe.report("EditBoxMixin.injectOnDragReturn", foe$t);
        }
    }
}
