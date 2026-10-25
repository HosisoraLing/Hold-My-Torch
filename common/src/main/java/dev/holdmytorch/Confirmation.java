package dev.holdmytorch;

/** One physical gesture; no game actions, inventory edits or network packets. */
public final class Confirmation {
    public enum Mode { DOUBLE_TAP, HOLD, CONFIRM_KEY }

    private boolean down;
    private boolean approved;
    private boolean used;
    private boolean click;
    private long pressedAt;
    private long firstTap = Long.MIN_VALUE;

    public void update(boolean pressed, boolean confirm, long now, Mode mode, int holdMs, int doubleMs) {
        boolean rising = pressed && !down;
        if (rising) {
            pressedAt = now;
            approved = false;
            used = false;
            if (mode == Mode.DOUBLE_TAP) {
                if (firstTap != Long.MIN_VALUE && now - firstTap <= doubleMs) {
                    approve();
                    firstTap = Long.MIN_VALUE;
                } else firstTap = now;
            }
        }
        down = pressed;
        if (!pressed) approved = false;
        else if (!used && ((mode == Mode.HOLD && now - pressedAt >= holdMs)
                || (mode == Mode.CONFIRM_KEY && confirm))) approve();
        if (mode == Mode.CONFIRM_KEY && !confirm) approved = false;
    }

    private void approve() {
        approved = true;
        used = true;
        click = true;
    }

    public boolean isDown() { return down && approved; }
    public boolean consumeClick() {
        boolean result = click;
        click = false;
        return result;
    }
}
