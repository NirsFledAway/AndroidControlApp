package com.tamerlanchik.robocar.control_screen;

import android.content.Context;
import android.graphics.PointF;

public class Model {
    private PointF linearSpeed;
    private PointF angularSpeed;
    private static Model mModel;

    private Model(Context context){
        linearSpeed = new PointF(0,0);
        angularSpeed = new PointF(0,0);
    }
    public static Model get(Context context){
        if(mModel == null){
            mModel = new Model(context);
        }
        return mModel;
    }

    public PointF getLinearSpeed() {
        return linearSpeed;
    }

    public void setLinearSpeed(PointF linearSpeed) {
        this.linearSpeed = linearSpeed;
    }

    public PointF getAngularSpeed() {
        return angularSpeed;
    }

    public void setAngularSpeed(PointF angularSpeed) {
        this.angularSpeed = angularSpeed;
    }
}