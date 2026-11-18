package dannypx.foe.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// The item models loaded (the server's resource pack's too), to find a bait's picture by its name.
@Mixin(net.minecraft.client.resources.model.ModelManager.class)
public interface ModelManagerAccessor {
    @Accessor("bakedItemStackModels")
    java.util.Map<net.minecraft.resources.Identifier, net.minecraft.client.renderer.item.ItemModel> foe$itemModels();
}
