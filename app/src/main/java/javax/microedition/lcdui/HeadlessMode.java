package javax.microedition.lcdui;

/**
 * Runtime state for the original NST Headless rendering mode.
 *
 * Headless suppresses screen flush requests only; it does not pause the MIDlet
 * or its event/game logic. The force flag is used for the one-shot redraw when
 * the game activity resumes.
 */
public final class HeadlessMode {
	private static volatile boolean enabled;
	private static volatile boolean forceFlush;

	private HeadlessMode() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static boolean toggle() {
		enabled = !enabled;
		return enabled;
	}

	static boolean isForceFlush() {
		return forceFlush;
	}

	static void setForceFlush(boolean value) {
		forceFlush = value;
	}
}
