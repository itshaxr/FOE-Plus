package dannypx.foe.mixin.inject;

import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.network.chat.contents.objects.ObjectInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/// A little picture in chat (the fish on FOE+'s catch lines) adds nothing to the line's plain
/// text, so copying a line gives clean text instead of "[item/fish/normal/whitesucker@items]".
/// Only the plain text changes: the picture still draws.
@Mixin(ObjectContents.class)
public class ObjectContentsMixin {
    @Shadow @Final private ObjectInfo contents;

    @Inject(method = "visit(Lnet/minecraft/network/chat/FormattedText$ContentConsumer;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private <T> void foePlusNoSpriteText(FormattedText.ContentConsumer<T> consumer, CallbackInfoReturnable<Optional<T>> cir) {
        if (this.contents instanceof AtlasSprite) cir.setReturnValue(Optional.empty());
    }
}
