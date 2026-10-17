package me.nillerusr;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

/** API-17+ Liquid Glass surfaces and low-cost animated ambient light. */
public final class LiquidGlassTheme {
    private static final int GLASS = 0xB91A2B3B;
    private static final int TEXT = 0xFFF2F7FC;

    private LiquidGlassTheme() { }

    public static void apply(Activity activity, View root, boolean motionEnabled) {
        if (activity == null || root == null) return;
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
            View screen = ((ViewGroup) content).getChildAt(0);
            if (screen instanceof RelativeLayout) screen.setBackgroundDrawable(new AmbientBackdrop(motionEnabled));
        }
        styleTree(root, motionEnabled);
    }

    public static void pause(Activity activity) {
        if (activity == null) return;
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
            View screen = ((ViewGroup) content).getChildAt(0);
            Drawable background = screen.getBackground();
            if (background != null) background.setVisible(false, false);
        }
    }

    public static void styleLibraryCard(View card, boolean motionEnabled) {
        if (card == null) return;
        card.setBackgroundDrawable(panel(GLASS, 0x995DE6DF, 20));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(card, 5));
        if (motionEnabled && Build.VERSION.SDK_INT >= 12 && card.getVisibility() == View.VISIBLE) {
            card.setAlpha(0.0f);
            card.animate().alpha(1.0f).setDuration(220L).start();
        }
    }

    private static void styleTree(View view, boolean motionEnabled) {
        if (view == null) return;
        int id = view.getId();
        if (id == com.magd.source.engine.R.id.header || id == com.magd.source.engine.R.id.footer) {
            view.setBackgroundDrawable(panel(0xAA162939, 0x665DE6DF, 22));
        } else if ("advanced_settings".equals(view.getTag())) {
            view.setBackgroundDrawable(panel(GLASS, 0x665DE6DF, 18));
        } else if ("liquid_glass_card".equals(view.getTag())) {
            styleLibraryCard(view, motionEnabled);
        } else if (view instanceof Button) {
            Button button = (Button) view;
            button.setBackgroundDrawable(buttonStates());
            button.setTextColor(TEXT);
            button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
            button.setMinHeight(Math.max(button.getMinimumHeight(), dp(button, 48)));
            if (Build.VERSION.SDK_INT >= 21) button.setElevation(dp(button, 2));
        } else if (view instanceof EditText) {
            EditText edit = (EditText) view;
            edit.setBackgroundDrawable(panel(0xB8182634, 0x665BCFCB, 12));
            edit.setTextColor(TEXT);
            edit.setHintTextColor(0xFF91A6B8);
            edit.setPadding(dp(edit, 12), dp(edit, 9), dp(edit, 12), dp(edit, 9));
        } else if (view instanceof LinearLayout && view.getParent() instanceof LinearLayout) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent.getId() == com.magd.source.engine.R.id.body && view.getTag() == null)
                view.setBackgroundDrawable(panel(GLASS, 0x555DE6DF, 22));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleTree(group.getChildAt(i), motionEnabled);
        }
    }

    public static GradientDrawable panel(int fill, int edge, int radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { blend(fill, 0x183E8290), fill, blend(fill, 0xD008111B) });
        d.setCornerRadius(radiusDp * android.content.res.Resources.getSystem().getDisplayMetrics().density);
        d.setStroke(1, edge);
        return d;
    }

    private static StateListDrawable buttonStates() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { android.R.attr.state_enabled, android.R.attr.state_pressed },
                panel(0xE53D7885, 0xFFE2FFFB, 16));
        s.addState(new int[] { android.R.attr.state_enabled, android.R.attr.state_focused },
                panel(0xD92D5969, 0xFF8CF5EB, 16));
        s.addState(new int[] { -android.R.attr.state_enabled }, panel(0x6630404E, 0x335E7889, 16));
        s.addState(new int[0], panel(0xB9274051, 0x885DE6DF, 16));
        return s;
    }

    private static int blend(int fg, int bg) {
        int a = Color.alpha(fg), inv = 255 - a;
        return Color.argb(255,
                (Color.red(fg) * a + Color.red(bg) * inv) / 255,
                (Color.green(fg) * a + Color.green(bg) * inv) / 255,
                (Color.blue(fg) * a + Color.blue(bg) * inv) / 255);
    }

    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Translucent colored light, with a static low-motion fallback. */
    private static final class AmbientBackdrop extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean animate;
        private final long start = SystemClock.uptimeMillis();
        private final Runnable frame = new Runnable() {
            @Override public void run() {
                invalidateSelf();
                if (isVisible() && animate) scheduleSelf(this, SystemClock.uptimeMillis() + 80L);
            }
        };
        AmbientBackdrop(boolean motionEnabled) { animate = motionEnabled; }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            canvas.drawColor(0xFF09121D);
            float t = animate ? ((SystemClock.uptimeMillis() - start) % 20000L) / 20000.0f : 0.25f;
            float w = b.width(), h = b.height(), size = Math.max(w, h);
            glow(canvas, w * (0.18f + 0.55f * (0.5f + 0.5f * (float)Math.sin(t * 6.2831853f))),
                    h * 0.18f, size * 0.82f, 0x5538D8CE);
            glow(canvas, w * (0.82f - 0.42f * (0.5f + 0.5f * (float)Math.cos(t * 6.2831853f))),
                    h * 0.68f, size * 0.92f, 0x553A72D8);
            glow(canvas, w * 0.50f, h * 1.02f, size * 0.76f, 0x3328B9A7);
            }

        private void glow(Canvas c, float x, float y, float radius, int color) {
            paint.setShader(new RadialGradient(x, y, radius,
                    new int[] { color, color & 0x00FFFFFF }, null, Shader.TileMode.CLAMP));
            c.drawRect(getBounds(), paint);
            paint.setShader(null);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
        @Override public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            if (visible && animate) scheduleSelf(frame, SystemClock.uptimeMillis() + 80L);
            else unscheduleSelf(frame);
            return changed;
        }
    }
}
