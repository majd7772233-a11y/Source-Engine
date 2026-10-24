package org.libsdl.app;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Stage 15 regression tests for multitouch key ownership and cancellation. */
public final class TouchInputStateTestSuite {
    private static int assertions;
    public static void main(String[] args) {
        testTwoPointersSharingAKey();
        testJoystickAndButtonShareAKey();
        testPointerReuseReleasesPreviousKey();
        testCancelReleasesEveryHeldKeyExactlyOnce();
        testRepeatedJoystickStateIsIdempotent();
        System.out.println("Stage 15 touch-input state tests passed: " + assertions + " assertions.");
    }
    private static void testTwoPointersSharingAKey() {
        Recorder recorder = new Recorder(); TouchInputState state = new TouchInputState(recorder);
        state.pressButton(1, 32); state.pressButton(2, 32);
        equal(Arrays.asList("down:32"), recorder.events, "shared key is emitted down once");
        state.releaseButton(1);
        check(state.isKeyDown(32), "first pointer release keeps shared key down");
        equal(Arrays.asList("down:32"), recorder.events, "no early key-up after first pointer");
        state.releaseButton(2);
        check(!state.isKeyDown(32), "last pointer release clears shared key");
        equal(Arrays.asList("down:32", "up:32"), recorder.events, "last release emits one key-up");
        state.releaseButton(2); equal(2, recorder.events.size(), "duplicate release is harmless");
    }
    private static void testJoystickAndButtonShareAKey() {
        Recorder recorder = new Recorder(); TouchInputState state = new TouchInputState(recorder);
        state.setJoystickKey(87, true); state.pressButton(4, 87); state.setJoystickKey(87, false);
        check(state.isKeyDown(87), "button ownership keeps key down after joystick releases");
        equal(Arrays.asList("down:87"), recorder.events, "overlapping sources do not duplicate key-down");
        state.releaseButton(4);
        equal(Arrays.asList("down:87", "up:87"), recorder.events, "key-up happens after both sources release");
    }
    private static void testPointerReuseReleasesPreviousKey() {
        Recorder recorder = new Recorder(); TouchInputState state = new TouchInputState(recorder);
        state.pressButton(8, 65); state.pressButton(8, 69);
        equal(Arrays.asList("down:65", "up:65", "down:69"), recorder.events,
            "reassigning a pointer balances its previous key before pressing the next key");
        equal(1, state.activeButtonPointerCount(), "pointer ownership is not duplicated");
        state.releaseButton(8);
        equal(Arrays.asList("down:65", "up:65", "down:69", "up:69"), recorder.events,
            "reused pointer releases its current key");
    }
    private static void testCancelReleasesEveryHeldKeyExactlyOnce() {
        Recorder recorder = new Recorder(); TouchInputState state = new TouchInputState(recorder);
        state.setJoystickKey(87, true); state.setJoystickKey(65, true);
        state.pressButton(10, 32); state.pressButton(11, 69); state.releaseAll();
        check(!state.isKeyDown(87) && !state.isKeyDown(65) && !state.isKeyDown(32) && !state.isKeyDown(69),
            "cancel clears all held keys");
        equal(0, state.activeButtonPointerCount(), "cancel clears pointer ownership");
        int releases=0; for (String event:recorder.events) if(event.startsWith("up:")) releases++;
        equal(4, releases, "cancel releases each emitted key once");
        int count=recorder.events.size(); state.releaseAll();
        equal(count, recorder.events.size(), "repeated cancel does not emit duplicate key-ups");
    }

    private static void testRepeatedJoystickStateIsIdempotent() {
        Recorder recorder = new Recorder();
        TouchInputState state = new TouchInputState(recorder);
        state.setJoystickKey(68, true);
        state.setJoystickKey(68, true);
        equal(Arrays.asList("down:68"), recorder.events, "repeated direction state does not duplicate key-down");
        state.setJoystickKey(68, false);
        state.setJoystickKey(68, false);
        equal(Arrays.asList("down:68", "up:68"), recorder.events, "repeated direction release does not duplicate key-up");
    }

    private static final class Recorder implements TouchInputState.Sink {
        final List<String> events = new ArrayList<String>();
        @Override public void keyDown(int keyCode) { events.add("down:" + keyCode); }
        @Override public void keyUp(int keyCode) { events.add("up:" + keyCode); }
    }
    private static void check(boolean condition,String label) { assertions++; if(!condition) throw new AssertionError(label); }
    private static void equal(Object expected,Object actual,String label) {
        assertions++; if(expected==null ? actual!=null : !expected.equals(actual))
            throw new AssertionError(label+": expected <"+expected+"> but was <"+actual+">");
    }
}
