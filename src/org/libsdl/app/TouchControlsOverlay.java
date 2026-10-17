package org.libsdl.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Optional keyboard-mapped touch overlay for Source games whose controls use
 * the normal WASD/Space/Ctrl/E/R keyboard bindings. This does not emulate a
 * gamepad axis or promise that every mod uses these bindings.
 */
final class TouchControlsOverlay extends View {
    private static final String PREFS = "srceng_touch_controls";
    private static final int CONTROL_JOYSTICK = 0;
    private static final int CONTROL_ATTACK = 1;
    private static final int CONTROL_JUMP = 2;
    private static final int CONTROL_USE = 3;
    private static final int CONTROL_RELOAD = 4;
    private static final int CONTROL_MENU = 5;
    private static final int[] PRESET_NAMES = {
        R.string.srceng_controls_preset_classic,
        R.string.srceng_controls_preset_compact,
        R.string.srceng_controls_preset_right
    };

    private final Paint paint = new Paint(3);
    private final SharedPreferences prefs;
    private final String prefix;
    private final float[] cx = new float[5];
    private final float[] cy = new float[5];
    private final Map<Integer, Integer> buttonCounts = new HashMap<Integer, Integer>();
    private final Map<Integer, Boolean> joystickKeys = new HashMap<Integer, Boolean>();
    private final Map<Integer, Boolean> nativeKeys = new HashMap<Integer, Boolean>();
    private final Map<Integer, Integer> pointerKeys = new HashMap<Integer, Integer>();
    private final Map<Integer, Boolean> pointerMouse = new HashMap<Integer, Boolean>();
    private final Map<Integer, Integer> pointerControls = new HashMap<Integer, Integer>();

    private float size = 1.0f;
    private float opacity = 0.58f;
    private float sensitivity = 0.72f;
    private int preset = 0;
    private boolean controlsVisible = true;
    private boolean editMode = false;
    private int joystickPointer = -1;
    private int dragControl = -1;
    private float joystickX;
    private float joystickY;
    private boolean joyUp;
    private boolean joyDown;
    private boolean joyLeft;
    private boolean joyRight;

    TouchControlsOverlay(Context context, String profile) {
        super(context);
        prefix = "profile_" + Integer.toHexString((profile == null ? "default" : profile).hashCode()) + "_";
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(false);
        load();
        placeDefaults();
    }

    private void load() {
        size = clamp(prefs.getFloat(prefix + "size", 1.0f), 0.70f, 1.45f);
        opacity = clamp(prefs.getFloat(prefix + "opacity", 0.58f), 0.18f, 0.90f);
        sensitivity = clamp(prefs.getFloat(prefix + "sensitivity", 0.72f), 0.35f, 1.0f);
        preset = prefs.getInt(prefix + "preset", 0);
        if (preset < 0 || preset > 2) preset = 0;
        controlsVisible = prefs.getBoolean(prefix + "visible", true);
        for (int i = 0; i < 5; i++) {
            cx[i] = prefs.getFloat(prefix + "x" + i, -1.0f);
            cy[i] = prefs.getFloat(prefix + "y" + i, -1.0f);
        }
    }

    private void save() {
        SharedPreferences.Editor e = prefs.edit();
        e.putFloat(prefix + "size", size);
        e.putFloat(prefix + "opacity", opacity);
        e.putFloat(prefix + "sensitivity", sensitivity);
        e.putInt(prefix + "preset", preset);
        e.putBoolean(prefix + "visible", controlsVisible);
        for (int i = 0; i < 5; i++) {
            e.putFloat(prefix + "x" + i, cx[i]);
            e.putFloat(prefix + "y" + i, cy[i]);
        }
        e.commit();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void placeDefaults() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (cx[CONTROL_JOYSTICK] < 0 || cy[CONTROL_JOYSTICK] < 0) {
            cx[CONTROL_JOYSTICK] = preset == 2 ? 0.82f : 0.16f;
            cy[CONTROL_JOYSTICK] = 0.76f;
        }
        if (cx[CONTROL_ATTACK] < 0 || cy[CONTROL_ATTACK] < 0) {
            cx[CONTROL_ATTACK] = preset == 2 ? 0.17f : 0.83f;
            cy[CONTROL_ATTACK] = 0.70f;
        }
        if (cx[CONTROL_JUMP] < 0 || cy[CONTROL_JUMP] < 0) {
            cx[CONTROL_JUMP] = preset == 2 ? 0.28f : 0.92f;
            cy[CONTROL_JUMP] = 0.82f;
        }
        if (cx[CONTROL_USE] < 0 || cy[CONTROL_USE] < 0) {
            cx[CONTROL_USE] = preset == 2 ? 0.17f : 0.78f;
            cy[CONTROL_USE] = 0.88f;
        }
        if (cx[CONTROL_RELOAD] < 0 || cy[CONTROL_RELOAD] < 0) {
            cx[CONTROL_RELOAD] = preset == 2 ? 0.28f : 0.94f;
            cy[CONTROL_RELOAD] = 0.61f;
        }
    }

    private float radius() {
        return Math.max(24.0f, Math.min(getWidth(), getHeight()) * 0.075f * size);
    }

    private float x(int control) { return cx[control] * getWidth(); }
    private float y(int control) { return cy[control] * getHeight(); }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        placeDefaults();
        for (int i = 0; i < 5; i++) {
            cx[i] = clamp(cx[i], 0.06f, 0.94f);
            cy[i] = clamp(cy[i], 0.12f, 0.92f);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!controlsVisible && !editMode) {
            drawMenu(canvas);
            return;
        }
        placeDefaults();
        float r = radius();
        int alpha = (int)(255 * opacity);
        if (editMode) {
            paint.setColor(0xAA59D9FF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.0f);
            canvas.drawRect(4, 4, getWidth() - 4, getHeight() - 4, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        drawStick(canvas, x(CONTROL_JOYSTICK), y(CONTROL_JOYSTICK), r, alpha);
        drawButton(canvas, CONTROL_ATTACK, getResources().getString(R.string.srceng_controls_attack), r, alpha);
        drawButton(canvas, CONTROL_JUMP, getResources().getString(R.string.srceng_controls_jump), r, alpha);
        drawButton(canvas, CONTROL_USE, getResources().getString(R.string.srceng_controls_use), r * 0.82f, alpha);
        drawButton(canvas, CONTROL_RELOAD, getResources().getString(R.string.srceng_controls_reload), r * 0.82f, alpha);
        drawMenu(canvas);
        if (editMode) {
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(Math.max(12.0f, r * 0.35f));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(getResources().getString(R.string.srceng_controls_edit_hint), 18, 32, paint);
        }
    }

    private void drawStick(Canvas c, float px, float py, float r, int alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor((alpha << 24) | 0x18344A);
        c.drawCircle(px, py, r * 1.35f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor((alpha << 24) | 0xB7D9F3);
        c.drawCircle(px, py, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor((alpha << 24) | 0xA9D7F2);
        float dx = joystickPointer < 0 ? 0 : (joystickX - px) * 0.34f;
        float dy = joystickPointer < 0 ? 0 : (joystickY - py) * 0.34f;
        c.drawCircle(px + dx, py + dy, r * 0.38f, paint);
    }

    private void drawButton(Canvas c, int control, String label, float r, int alpha) {
        float px = x(control), py = y(control);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor((alpha << 24) | (editMode ? 0x286F94 : 0x102C40));
        c.drawCircle(px, py, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setColor((alpha << 24) | 0xC1E5FA);
        c.drawCircle(px, py, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(Math.max(10.0f, r * 0.31f));
        c.drawText(label, px, py + paint.getTextSize() * 0.35f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawMenu(Canvas c) {
        float r = Math.max(18.0f, radius() * 0.46f);
        float px = getWidth() - r - 14;
        float py = r + 12;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB8223545);
        c.drawCircle(px, py, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(0xDDE5F6FF);
        c.drawCircle(px, py, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(r * 1.1f);
        c.drawText("⚙", px, py + r * 0.38f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private boolean near(float px, float py, float tx, float ty, float r) {
        float dx = px - tx, dy = py - ty;
        return dx * dx + dy * dy <= r * r;
    }

    private int hitControl(float px, float py) {
        float r = radius();
        if (near(px, py, getWidth() - r * 0.46f - 14, r * 0.46f + 12, Math.max(24, r * 0.65f))) return CONTROL_MENU;
        if (!controlsVisible && !editMode) return -1;
        if (near(px, py, x(CONTROL_JOYSTICK), y(CONTROL_JOYSTICK), r * 1.35f)) return CONTROL_JOYSTICK;
        if (near(px, py, x(CONTROL_ATTACK), y(CONTROL_ATTACK), r)) return CONTROL_ATTACK;
        if (near(px, py, x(CONTROL_JUMP), y(CONTROL_JUMP), r)) return CONTROL_JUMP;
        if (near(px, py, x(CONTROL_USE), y(CONTROL_USE), r * 0.9f)) return CONTROL_USE;
        if (near(px, py, x(CONTROL_RELOAD), y(CONTROL_RELOAD), r * 0.9f)) return CONTROL_RELOAD;
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointer = event.getPointerId(index);
        float px = event.getX(index), py = event.getY(index);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int control = hitControl(px, py);
            if (control < 0) return false;
            if (control == CONTROL_MENU) {
                showSettings();
                return true;
            }
            if (editMode) {
                dragControl = control;
                pointerControls.put(pointer, Integer.valueOf(control));
                return true;
            }
            if (control == CONTROL_JOYSTICK) {
                joystickPointer = pointer;
                joystickX = px;
                joystickY = py;
                updateJoystick();
            } else {
                pointerControls.put(pointer, Integer.valueOf(control));
                if (control == CONTROL_ATTACK) {
                    pointerMouse.put(Integer.valueOf(pointer), Boolean.TRUE);
                    SDLActivity.onNativeMouse(1, MotionEvent.ACTION_DOWN, 0.0f, 0.0f, true);
                } else {
                    int key = keyForControl(control);
                    pointerKeys.put(Integer.valueOf(pointer), Integer.valueOf(key));
                    pressButtonKey(key);
                }
            }
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (editMode && dragControl >= 0) {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    if (pointerControls.containsKey(Integer.valueOf(id))) {
                        cx[dragControl] = clamp(event.getX(i) / Math.max(1, getWidth()), 0.06f, 0.94f);
                        cy[dragControl] = clamp(event.getY(i) / Math.max(1, getHeight()), 0.12f, 0.92f);
                        invalidate();
                        break;
                    }
                }
            } else if (joystickPointer >= 0) {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    if (event.getPointerId(i) == joystickPointer) {
                        joystickX = event.getX(i);
                        joystickY = event.getY(i);
                        updateJoystick();
                        invalidate();
                        break;
                    }
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            if (action == MotionEvent.ACTION_CANCEL) {
                releaseAll();
                dragControl = -1;
                pointerControls.clear();
                return true;
            }
            Integer key = pointerKeys.remove(Integer.valueOf(pointer));
            if (key != null) releaseButtonKey(key.intValue());
            if (pointerMouse.remove(Integer.valueOf(pointer)) != null) {
                SDLActivity.onNativeMouse(1, MotionEvent.ACTION_UP, 0.0f, 0.0f, true);
            }
            pointerControls.remove(Integer.valueOf(pointer));
            if (pointer == joystickPointer) {
                joystickPointer = -1;
                joystickX = 0;
                joystickY = 0;
                setJoystickDirections(false, false, false, false);
            }
            if (pointerControls.isEmpty()) dragControl = -1;
            if (editMode) save();
            invalidate();
            return true;
        }
        return true;
    }

    private int keyForControl(int control) {
        if (control == CONTROL_ATTACK) return KeyEvent.KEYCODE_UNKNOWN;
        if (control == CONTROL_JUMP) return KeyEvent.KEYCODE_SPACE;
        if (control == CONTROL_USE) return KeyEvent.KEYCODE_E;
        if (control == CONTROL_RELOAD) return KeyEvent.KEYCODE_R;
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    private void updateJoystick() {
        float dx = (joystickX - x(CONTROL_JOYSTICK)) / (radius() * sensitivity);
        float dy = (joystickY - y(CONTROL_JOYSTICK)) / (radius() * sensitivity);
        setJoystickDirections(dy < -0.35f, dy > 0.35f, dx < -0.35f, dx > 0.35f);
    }

    private void setJoystickDirections(boolean up, boolean down, boolean left, boolean right) {
        joyUp = up; joyDown = down; joyLeft = left; joyRight = right;
        setJoystickKey(KeyEvent.KEYCODE_W, up);
        setJoystickKey(KeyEvent.KEYCODE_S, down);
        setJoystickKey(KeyEvent.KEYCODE_A, left);
        setJoystickKey(KeyEvent.KEYCODE_D, right);
    }

    private void setJoystickKey(int key, boolean pressed) {
        joystickKeys.put(Integer.valueOf(key), Boolean.valueOf(pressed));
        updateNativeKey(key);
    }

    private void pressButtonKey(int key) {
        Integer old = buttonCounts.get(Integer.valueOf(key));
        buttonCounts.put(Integer.valueOf(key), Integer.valueOf(old == null ? 1 : old.intValue() + 1));
        updateNativeKey(key);
    }

    private void releaseButtonKey(int key) {
        Integer old = buttonCounts.get(Integer.valueOf(key));
        if (old == null || old.intValue() <= 1) buttonCounts.remove(Integer.valueOf(key));
        else buttonCounts.put(Integer.valueOf(key), Integer.valueOf(old.intValue() - 1));
        updateNativeKey(key);
    }

    private void updateNativeKey(int key) {
        Integer k = Integer.valueOf(key);
        Integer count = buttonCounts.get(k);
        Boolean joy = joystickKeys.get(k);
        boolean wanted = (count != null && count.intValue() > 0) || (joy != null && joy.booleanValue());
        Boolean old = nativeKeys.get(k);
        if (old == null || old.booleanValue() != wanted) {
            nativeKeys.put(k, Boolean.valueOf(wanted));
            if (wanted) SDLActivity.onNativeKeyDown(key);
            else SDLActivity.onNativeKeyUp(key);
        }
    }

    void releaseAll() {
        if (!pointerMouse.isEmpty()) SDLActivity.onNativeMouse(1, MotionEvent.ACTION_UP, 0.0f, 0.0f, true);
        for (Integer key : nativeKeys.keySet().toArray(new Integer[nativeKeys.size()])) {
            Boolean down = nativeKeys.get(key);
            if (down != null && down.booleanValue()) SDLActivity.onNativeKeyUp(key.intValue());
        }
        nativeKeys.clear();
        buttonCounts.clear();
        joystickKeys.clear();
        pointerKeys.clear();
        pointerMouse.clear();
        pointerControls.clear();
        joystickPointer = -1;
        dragControl = -1;
        setJoystickDirections(false, false, false, false);
        invalidate();
    }

    private void showSettings() {
        final String[] items = new String[] {
            getResources().getString(R.string.srceng_controls_layout),
            getResources().getString(R.string.srceng_controls_size),
            getResources().getString(R.string.srceng_controls_opacity),
            getResources().getString(R.string.srceng_controls_sensitivity),
            getResources().getString(editMode ? R.string.srceng_controls_finish_edit : R.string.srceng_controls_edit),
            getResources().getString(controlsVisible ? R.string.srceng_controls_hide : R.string.srceng_controls_show),
            getResources().getString(R.string.srceng_controls_reset)
        };
        new AlertDialog.Builder(getContext())
            .setTitle(R.string.srceng_controls_title)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) choosePreset();
                    else if (which == 1) adjustValue(R.string.srceng_controls_size, size, 70, 145, 100, 0);
                    else if (which == 2) adjustValue(R.string.srceng_controls_opacity, opacity, 18, 90, 100, 1);
                    else if (which == 3) adjustValue(R.string.srceng_controls_sensitivity, sensitivity, 35, 100, 100, 2);
                    else if (which == 4) {
                        editMode = !editMode;
                        if (!editMode) save();
                        invalidate();
                    } else if (which == 5) {
                        controlsVisible = !controlsVisible;
                        save();
                        invalidate();
                    } else if (which == 6) resetControls();
                }
            }).show();
    }

    private void choosePreset() {
        String[] names = new String[] {
            getResources().getString(R.string.srceng_controls_preset_classic),
            getResources().getString(R.string.srceng_controls_preset_compact),
            getResources().getString(R.string.srceng_controls_preset_right)
        };
        new AlertDialog.Builder(getContext()).setTitle(R.string.srceng_controls_layout)
            .setSingleChoiceItems(names, preset, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    preset = which;
                    size = which == 1 ? 0.82f : 1.0f;
                    for (int i = 0; i < 5; i++) { cx[i] = -1.0f; cy[i] = -1.0f; }
                    placeDefaults();
                    save();
                    invalidate();
                    dialog.dismiss();
                }
            }).setNegativeButton(R.string.srceng_controls_cancel, null).show();
    }

    private void adjustValue(int title, float value, int min, int max, int multiplier, final int type) {
        final SeekBar seek = new SeekBar(getContext());
        seek.setMax(max - min);
        float normalized = type == 0 ? size : (type == 1 ? opacity : sensitivity);
        seek.setProgress(Math.round(normalized * multiplier) - min);
        final TextView valueText = new TextView(getContext());
        valueText.setPadding(18, 6, 18, 6);
        valueText.setText(String.valueOf(Math.round(normalized * multiplier)) + "%");
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20, 12, 20, 4);
        box.addView(valueText);
        box.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int actual = progress + (type == 0 ? 70 : (type == 1 ? 18 : 35));
                valueText.setText(String.valueOf(actual) + "%");
                if (type == 0) size = actual / 100.0f;
                else if (type == 1) opacity = actual / 100.0f;
                else sensitivity = actual / 100.0f;
                invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { save(); }
        });
        new AlertDialog.Builder(getContext()).setTitle(title).setView(box)
            .setPositiveButton(R.string.srceng_controls_done, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { save(); invalidate(); }
            }).setNegativeButton(R.string.srceng_controls_cancel, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { load(); invalidate(); }
            }).show();
    }

    private void resetControls() {
        size = 1.0f;
        opacity = 0.58f;
        sensitivity = 0.72f;
        preset = 0;
        editMode = false;
        controlsVisible = true;
        for (int i = 0; i < 5; i++) { cx[i] = -1.0f; cy[i] = -1.0f; }
        placeDefaults();
        save();
        invalidate();
    }
}
