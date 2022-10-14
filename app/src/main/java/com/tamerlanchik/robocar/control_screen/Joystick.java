package com.tamerlanchik.robocar.control_screen;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar;

import com.tamerlanchik.robocar.R;

public class Joystick extends SeekBar {
    private static final String TAG = "JoystickView";

    private PointF mCurrent;
    private PointF mCenterPoint = new PointF(0, 0);
    private PointF mTouchShift;
    private PointF mPreTouchEvent;
    private boolean mIsToggled = false;
    private Paint mHandlePaint;
    private Paint mGridPaint;

    //design attributes
    private boolean mAreGlobalAxisOnFlag;
    private boolean mAreButtonAxisOnFlag;
    private float mButtonAxisSurplus;
    private int mHandleColor;
    private float mHandleRadius;
    public int mID = 0;

    private OnJoystickChangeListener mOnJoystickChangeListener;

    public interface OnJoystickChangeListener{
        void onStartTrackingTouch(Joystick g);
        void onValueChanged(Joystick g, Point value);
        void onStopTrackingTouch(Joystick g);
    }

    public void setOnJoystickChangeListener(OnJoystickChangeListener listener){
        mOnJoystickChangeListener = listener;
    }
    public Joystick(Context context, AttributeSet attrs, int id) {
        this(context, attrs);
        mID = id;
    }
    public Joystick(Context context, AttributeSet attrs) {
        super(context, attrs);
        parseAttributes(context,attrs);

        mHandlePaint = new Paint();
        mHandlePaint.setColor(mHandleColor);

        mGridPaint = new Paint();
        mGridPaint.setColor(Color.BLACK);

        mCenterPoint = new PointF(0,0);

        mCurrent = new PointF();
        mCurrent.set(mCenterPoint);

        mPreTouchEvent = new PointF(0,0);



        mTouchShift = new PointF(0,0);
    }

    private void parseAttributes(Context context, AttributeSet attrs){
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.graphAttrs);
        mHandleRadius = attributes.getDimension(R.styleable.graphAttrs_handle_radius, 50);
        mHandleColor = attributes.getColor(R.styleable.graphAttrs_handle_color, getResources().getColor(R.color.colorAccent));
        mAreGlobalAxisOnFlag = attributes.getBoolean(R.styleable.graphAttrs_axis_on, false);
        mAreButtonAxisOnFlag = attributes.getBoolean(R.styleable.graphAttrs_handle_are_axis_on, false);
        mButtonAxisSurplus = attributes.getDimension(R.styleable.graphAttrs_handle_axis_surplus, 0);
    }
    public Joystick(Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mCenterPoint.set(w/2, h/2);
        mCurrent.set(mCenterPoint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(mAreGlobalAxisOnFlag) {
            //vertical axis
            canvas.drawLine(mCenterPoint.x, 0, mCenterPoint.x, getHeight(), mGridPaint);
            //horizontal axis
            canvas.drawLine(0, mCenterPoint.y, getWidth(), mCenterPoint.y, mGridPaint);
        }

        canvas.drawCircle(mCurrent.x, mCurrent.y, mHandleRadius, mHandlePaint);
        if(mAreButtonAxisOnFlag) {
            //vertical axis
            canvas.drawLine(mCurrent.x, mCurrent.y - mHandleRadius - mButtonAxisSurplus, mCurrent.x,
                    mCurrent.y + mHandleRadius + mButtonAxisSurplus, mGridPaint);
            //horizontal axis
            canvas.drawLine(mCurrent.x - mHandleRadius - mButtonAxisSurplus, mCurrent.y,
                    mCurrent.x + mHandleRadius + mButtonAxisSurplus, mCurrent.y, mGridPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch(event.getAction()){
            case MotionEvent.ACTION_DOWN:
                if(Math.abs(event.getX()-mPreTouchEvent.x)*Math.abs(event.getY()-mPreTouchEvent.y) < 1 ) {return true;}
                mTouchShift.set(event.getX( ) - mCenterPoint.x, event.getY() - mCenterPoint.y);
                if((mTouchShift.x)*(mTouchShift.x) > mHandleRadius*mHandleRadius ||
                        (mTouchShift.y)*(mTouchShift.y) > mHandleRadius*mHandleRadius){
                    mTouchShift.set(0,0);
                    mIsToggled = false;
                }else{
                    mIsToggled = true;
                    onStartTrackingTouch();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if(mIsToggled) {
                    mCurrent.set(event.getX() - mTouchShift.x, event.getY() - mTouchShift.y);
                    onValueChanged();
                }
                break;
            case MotionEvent.ACTION_UP:
                mCurrent.set(mCenterPoint);
                mTouchShift.set(0,0);
                mIsToggled = false;
                onStopTrackingTouch();
                break;
            default:
                break;
        }

        invalidate();
        return true;
    }

    public Point getValue(){
        Point value = new Point();
        value.set((int)(mCurrent.x-mCenterPoint.x), (int)(mCurrent.y-mCenterPoint.y));
        if(getWidth()*getHeight() > 0) {
            value.x = (int)(value.x * (float)(255.0 / (getWidth()/2.0)));
            value.y *= (float)(255.0 / (getHeight()/2.0));
        }
        return value;
    }

    private void onStartTrackingTouch(){
        if(mOnJoystickChangeListener != null){
            mOnJoystickChangeListener.onStartTrackingTouch(this);
        }
    }
    private void onValueChanged(){
        if(mOnJoystickChangeListener!=null){
            mOnJoystickChangeListener.onValueChanged(this, getValue());
        }
    }

    private void onStopTrackingTouch(){
        if(mOnJoystickChangeListener!=null){
            mOnJoystickChangeListener.onStopTrackingTouch(this);
        }
    }

    public PointF getValuesRange(){
        PointF range = new PointF();
        range.set(getWidth(), getHeight());
        return range;
    }
    @Override
    public void setId(int id) {
        mID = id;
    }
}