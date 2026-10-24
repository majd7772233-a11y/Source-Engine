package org.libsdl.app;
import java.util.HashMap;
import java.util.Map;

/** Platform-independent ownership tracking for keys synthesized by the touch overlay. */
final class TouchInputState {
    interface Sink { void keyDown(int keyCode); void keyUp(int keyCode); }
    private final Sink sink;
    private final Map<Integer,Integer> pointerKeys = new HashMap<Integer,Integer>();
    private final Map<Integer,Integer> buttonCounts = new HashMap<Integer,Integer>();
    private final Map<Integer,Boolean> joystickKeys = new HashMap<Integer,Boolean>();
    private final Map<Integer,Boolean> nativeKeys = new HashMap<Integer,Boolean>();

    TouchInputState(Sink sink) {
        if (sink == null) throw new IllegalArgumentException("sink must not be null");
        this.sink = sink;
    }

    void pressButton(int pointerId, int keyCode) {
        Integer pointer = Integer.valueOf(pointerId);
        Integer previous = pointerKeys.get(pointer);
        if (previous != null) {
            if (previous.intValue() == keyCode) return;
            releaseButtonKey(previous.intValue());
        }
        pointerKeys.put(pointer, Integer.valueOf(keyCode));
        Integer key = Integer.valueOf(keyCode);
        Integer count = buttonCounts.get(key);
        buttonCounts.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        updateNativeKey(keyCode);
    }

    void releaseButton(int pointerId) {
        Integer key = pointerKeys.remove(Integer.valueOf(pointerId));
        if (key != null) releaseButtonKey(key.intValue());
    }

    private void releaseButtonKey(int keyCode) {
        Integer key = Integer.valueOf(keyCode);
        Integer count = buttonCounts.get(key);
        if (count == null || count.intValue() <= 1) buttonCounts.remove(key);
        else buttonCounts.put(key, Integer.valueOf(count.intValue() - 1));
        updateNativeKey(keyCode);
    }

    void setJoystickKey(int keyCode, boolean pressed) {
        Integer key = Integer.valueOf(keyCode);
        if (pressed) joystickKeys.put(key, Boolean.TRUE);
        else joystickKeys.remove(key);
        updateNativeKey(keyCode);
    }

    private void updateNativeKey(int keyCode) {
        Integer key = Integer.valueOf(keyCode);
        Integer count = buttonCounts.get(key);
        boolean wanted = (count != null && count.intValue() > 0) || Boolean.TRUE.equals(joystickKeys.get(key));
        boolean wasDown = Boolean.TRUE.equals(nativeKeys.get(key));
        if (wasDown == wanted) return;
        if (wanted) {
            nativeKeys.put(key, Boolean.TRUE);
            sink.keyDown(keyCode);
        } else {
            nativeKeys.remove(key);
            sink.keyUp(keyCode);
        }
    }

    /** Balance every emitted key-up exactly once and discard active ownership. */
    void releaseAll() {
        for (Integer key : nativeKeys.keySet().toArray(new Integer[nativeKeys.size()])) {
            if (Boolean.TRUE.equals(nativeKeys.get(key))) sink.keyUp(key.intValue());
        }
        nativeKeys.clear();
        buttonCounts.clear();
        joystickKeys.clear();
        pointerKeys.clear();
    }

    boolean isKeyDown(int keyCode) { return Boolean.TRUE.equals(nativeKeys.get(Integer.valueOf(keyCode))); }
    int activeButtonPointerCount() { return pointerKeys.size(); }
}
