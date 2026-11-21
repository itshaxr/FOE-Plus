package dannypx.foe.mixin.inject;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * The words of a private message you're typing ("/msg Steve hi there") in the green the server shows messages in,
 * not the game's yellow for a command's second part.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow @Final EditBox input;

    @Inject(method = "formatChat", at = @At("RETURN"), cancellable = true)
    private void foe$messageGreen(String shown, int offset, CallbackInfoReturnable<FormattedCharSequence> cir) {
        try {
            if (!dannypx.foe.config.Configs.mainConfig.enableMod.get()) return;
            int from = dannypx.foe.helper.MessageCommands.messageStart(input.getValue());
            if (from < 0 || cir.getReturnValue() == null) return;
            List<Style> styles = new ArrayList<>();
            List<Integer> chars = new ArrayList<>();
            cir.getReturnValue().accept((index, style, cp) -> { styles.add(style); chars.add(cp); return true; });
            Style green = Style.EMPTY.withColor(ChatFormatting.GREEN);
            cir.setReturnValue(sink -> {
                int at = offset;
                for (int k = 0; k < chars.size(); k++) {
                    int cp = chars.get(k);
                    if (!sink.accept(k, at >= from ? green : styles.get(k), cp)) return false;
                    at += Character.charCount(cp);
                }
                return true;
            });
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError e) throw e;
            dannypx.foe.helper.Safe.report("CommandSuggestionsMixin.messageGreen", t);
        }
    }
}
