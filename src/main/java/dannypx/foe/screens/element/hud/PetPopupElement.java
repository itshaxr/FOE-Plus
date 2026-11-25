package dannypx.foe.screens.element.hud;

/**
 * The pet popup (a pet you fish up, in FOE style): drawn like the catch popup, but in its own place and size, so it
 * has its own box in Move HUD Elements.
 */
public class PetPopupElement extends CatchPopupElement {
    public PetPopupElement() {
        this(false);
    }

    public PetPopupElement(boolean isCopy) {
        super(isCopy, true);
    }
}
