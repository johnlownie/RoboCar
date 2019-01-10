package ca.jetsphere.robocar.devices;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

public class Joystick
{
    public enum Stick { NONE, UP, UPRIGHT, RIGHT, DOWNRIGHT, DOWN, DOWNLEFT, LEFT, UPLEFT }

    private Context mContext;
    private ViewGroup mLayout;
    private LayoutParams params;
    private int stick_width, stick_height;

    private int position_x = 0, position_y = 0, min_distance = 0;
    private float distance = 0, angle = 0;

    private int stick_alpha = 200;
    private int layout_alpha = 200;
    private int offset = 0;

    private DrawCanvas mDraw;
    private Paint mPaint;
    private Bitmap mStick;

    private boolean touch_state = false;

    /**
     *
     */
    public Joystick (Context context, ViewGroup layout, int stick_res_id) {
        mContext = context;

        mStick = BitmapFactory.decodeResource(mContext.getResources(), stick_res_id);

        stick_width = mStick.getWidth();
        stick_height = mStick.getHeight();

        mDraw = new DrawCanvas(mContext);
        mPaint = new Paint();
        mLayout = layout;
        params = mLayout.getLayoutParams();
    }

    /**
     *
     */
    public void drawStick(MotionEvent arg1) {
        position_x = (int) (arg1.getX() - (params.width / 2));
        position_y = (int) (arg1.getY() - (params.height / 2));
        distance = (float) Math.sqrt(Math.pow(position_x, 2) + Math.pow(position_y, 2));
        angle = (float) cal_angle(position_x, position_y);

        if (arg1.getAction() == MotionEvent.ACTION_DOWN) {
            if (distance <= (params.width / 2) - offset) {
                mDraw.position(arg1.getX(), arg1.getY());
                draw();
                touch_state = true;
            }
        } else if (arg1.getAction() == MotionEvent.ACTION_MOVE && touch_state) {
            if (distance <= (params.width / 2) - offset) {
                mDraw.position(arg1.getX(), arg1.getY());
                draw();
            } else if (distance > (params.width / 2) - offset) {
                float x = (float) (Math.cos(Math.toRadians(cal_angle(position_x, position_y))) * ((params.width / 2) - offset));
                float y = (float) (Math.sin(Math.toRadians(cal_angle(position_x, position_y))) * ((params.height / 2) - offset));
            } else {
                mLayout.removeView(mDraw);
            }
        } else if (arg1.getAction() == MotionEvent.ACTION_UP) {
            mLayout.removeView(mDraw);
            touch_state = false;
        }
    }

    /**
     *
     */
    public double cal_angle(float x, float y) {
        if (x >= 0 && y >= 0) return Math.toDegrees(Math.atan(y / x)); else
        if (x <  0 && y >= 0) return Math.toDegrees(Math.atan(y / x)) + 180; else
        if (x <  0 && y <  0) return Math.toDegrees(Math.atan(y / x)) + 180; else
        if (x >= 0 && y <  0) return Math.toDegrees(Math.atan(y / x)) + 360;

        return 0;
    }

    /**
     *
     */
    private void draw() {
        try {
            mLayout.removeView(mDraw);
        } catch (Exception e) {}

        mLayout.addView(mDraw);
    }

    /**
     *
     */
    public int   getX       () { return (distance > min_distance && touch_state) ? position_x : 0; }
    public int   getY       () { return (distance > min_distance && touch_state) ? position_y : 0; }
    public float getAngle   () { return (distance > min_distance && touch_state) ? angle      : 0; }
    public float getDistance() { return (distance > min_distance && touch_state) ? distance   : 0; }

    /**
     *
     */
    public int get8Direction() {
        if (!touch_state) return 0;

        if (distance <= min_distance) return Stick.NONE.ordinal();

        if (angle >= 247.5 && angle < 292.5) return Stick.UP       .ordinal(); else
        if (angle >= 292.5 && angle < 337.5) return Stick.UPRIGHT  .ordinal(); else
        if (angle >= 337.5 && angle <  22.5) return Stick.RIGHT    .ordinal(); else
        if (angle >=  22.5 && angle <  67.5) return Stick.DOWNRIGHT.ordinal(); else
        if (angle >=  67.5 && angle < 112.5) return Stick.DOWN     .ordinal(); else
        if (angle >= 112.5 && angle < 157.5) return Stick.DOWNLEFT .ordinal(); else
        if (angle >= 157.5 && angle < 202.5) return Stick.LEFT     .ordinal(); else
        if (angle >= 202.5 && angle < 247.5) return Stick.UPLEFT   .ordinal();

        return 0;
    }

    /**
     *
     */
    public int get4Direction() {
        if (!touch_state) return 0;

        if (distance <= min_distance) return Stick.NONE.ordinal();

        if (angle >= 225 && angle < 315) return Stick.UP   .ordinal(); else
        if (angle >= 315 && angle <  45) return Stick.RIGHT.ordinal(); else
        if (angle >=  45 && angle < 135) return Stick.DOWN .ordinal(); else
        if (angle >= 135 && angle < 225) return Stick.LEFT .ordinal();

        return 0;
    }

    /**
     *
     */
    public void setLayoutAlpha(int layout_alpha) { this.layout_alpha = layout_alpha; mLayout.getBackground().setAlpha(layout_alpha); }
    public void setStickAlpha (int stick_alpha ) { this.stick_alpha  = stick_alpha; mPaint.setAlpha(stick_alpha); }


    public void setMinDistance( int min_distance) { this.min_distance = min_distance; }
    public void setOffset     ( int  offset     ) { this.offset       = offset      ; }

    public void setLayoutSize(int width, int height) {
        params.width = width;
        params.height = height;
    }

    public void setStickSize(int width, int height) {
        mStick = Bitmap.createScaledBitmap(mStick, width, height, false);
        stick_width = mStick.getWidth();
        stick_height = mStick.getHeight();
    }

    /**
     *
     */
    private class DrawCanvas extends View
    {
        float x, y;

        private DrawCanvas(Context mContext) {
            super(mContext);
        }

        public void onDraw(Canvas canvas) {
            canvas.drawBitmap(mStick, x, y, mPaint);
        }

        private void position(float pos_x, float pos_y) {
            x = pos_x - (stick_width / 2);
            y = pos_y - (stick_height / 2);
        }
    }
}
