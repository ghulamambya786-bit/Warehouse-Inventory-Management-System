import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.util.function.DoubleConsumer;

/**
 * Small animation helper used all over the application (no external libraries).
 * An animation is a javax.swing.Timer that ticks every ~16 ms on the Swing thread and hands a progress value
 * from 0.0 to 1.0 to the caller, who then repaints or moves something.
 */
public final class Anim {
    private Anim() { }

    /** Starts fast and slows down at the end (smooth stop). */
    public static double easeOut(double p) {
        double q = 1 - clamp(p);
        return 1 - q * q * q;
    }

    /** Overshoots a little and settles back (a small bounce). */
    public static double easeOutBack(double p) {
        double c1 = 1.70158, c3 = c1 + 1, q = clamp(p) - 1;
        return 1 + c3 * q * q * q + c1 * q * q;
    }

    /** Slow start and slow end. */
    public static double easeInOut(double p) {
        p = clamp(p);
        return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
    }

    public static double clamp(double p) { return p < 0 ? 0 : p > 1 ? 1 : p; }

    /**
     * Plays an animation: calls frame.accept(p) with p going from 0 to 1 over the given time, the last call
     * always has p == 1. The returned timer can be stopped early.
     */
    public static Timer play(int durationMs, int delayMs, DoubleConsumer frame) {
        final long[] start = {0};
        Timer t = new Timer(16, null);
        t.addActionListener(e -> {
            double p = (System.currentTimeMillis() - start[0]) / (double) durationMs;
            if (p >= 1) {
                t.stop();
                p = 1;
            }
            frame.accept(p);
        });
        t.setInitialDelay(Math.max(0, delayMs));
        t.setCoalesce(true);
        start[0] = System.currentTimeMillis() + Math.max(0, delayMs);
        t.start();
        return t;
    }

    /** A panel that can be drawn transparent (alpha 0..1). Used to fade things in. */
    public static class FadePanel extends JPanel {
        private float alpha = 1f;
        private Timer running;

        public FadePanel(LayoutManager layout) { super(layout); }

        public void setAlpha(float a) {
            alpha = (float) clamp(a);
            repaint();
        }

        /** Fades in after delayMs, taking durationMs. */
        public void fadeIn(int delayMs, int durationMs) {
            if (running != null) running.stop();
            setAlpha(0f);
            running = play(durationMs, delayMs, p -> setAlpha((float) easeOut(p)));
        }

        @Override
        public void paint(Graphics g) {
            if (alpha >= 0.999f) {
                super.paint(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            super.paint(g2);
            g2.dispose();
        }
    }
}
