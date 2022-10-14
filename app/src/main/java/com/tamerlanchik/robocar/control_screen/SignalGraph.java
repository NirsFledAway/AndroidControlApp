package com.tamerlanchik.robocar.control_screen;

import android.content.Context;
import android.view.View;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class SignalGraph {
    private GraphView mGraph;
    private LineGraphSeries<DataPoint> mSeries;

    final int mTimeWindowWidth = 3;
    final int maxDataPoints = 3000;

    public SignalGraph(int viewId, String title, View ctx){
        mGraph = (GraphView) ctx.findViewById(viewId);
        mSeries = new LineGraphSeries<>();
        mGraph.addSeries(mSeries);
        mGraph.getViewport().setXAxisBoundsManual(true);
        mGraph.getViewport().setMinX(-1*mTimeWindowWidth*1000);
        mGraph.getViewport().setMaxX(0);
        mGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);

        GridLabelRenderer gridLabel = mGraph.getGridLabelRenderer();
        gridLabel.setVerticalAxisTitle(title);
//            mGraph.setTitle(title);

        GridLabelRenderer glr = mGraph.getGridLabelRenderer();
        glr.setPadding(40); // should allow for 3 digits to fit on screen
        glr.setTextSize(20);
    }

    void add(long time, double value) {
        mSeries.appendData(new DataPoint((new Long(time)).doubleValue(), value), true, maxDataPoints);
    }
}
