package com.tamerlanchik.robocar.control_screen;

import android.util.Log;

import androidx.arch.core.util.Function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TaskScheduler {
    public enum TaskName {JOYSTICKS, PING, PING_WATCHDOG};
    private List<Task> mSchedule;
    private static TaskScheduler mInstance;

    @FunctionalInterface
    public interface IJob {
        void run();
    }

    private TaskScheduler() {
        mSchedule = new ArrayList<>(Collections.nCopies(TaskName.values().length, null));
    }

    public static TaskScheduler get() {
        if (mInstance == null) {
            mInstance = new TaskScheduler();
        }
        return mInstance;
    }

    public void addTask(TaskName name, int period, IJob job) {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                job.run();
            }
        };
        addTask(name, new Task(timerTask, period));
    }

    public void addTask(TaskName name, Task task) {
        cancelTask(name);
        mSchedule.set(name.ordinal(), task);
        task.start();
    }

    public void cancelTask(TaskName name) {
        Task task = mSchedule.get(name.ordinal());
        if (task == null) {
            return;
        }
        task.stop();
    }
//    private Timer mSendControlTimer;
//
//    private void setTimerTask(TimerTask work, int period) {
//        if (mSendControlTimer == null) {
//            return;
//        }
//        TimerTask task = new TimerTask() {
//            @Override
//            public void run() {
//                work.run();
//                setTimerTask(work, period);
//            }
//        };
//        mSendControlTimer.schedule(task, period);  // 50ms
//    }
//
//    public interface FunctionTask {
//        void work();
//    }
    public class Task {
        Timer mTimer;
        int mPeriod;
        TimerTask mJob;

        public Task(TimerTask job, int millisInterval) {
           setJob(job);
           setPeriod(millisInterval);
            mTimer = new Timer();
        }

        public void setJob(TimerTask job) {
            mJob = job;
        }
        public void setPeriod(int period) {
            mPeriod = period;
        }

        public void start() {
            mTimer.schedule(mJob, mPeriod, mPeriod);
//          scheduleAtFixedRate() - выполняется с заданным интервалом относительно времени первого вызова. Если задача выполняется долго, их в памяти появляется несколько
//          schedule() - выполнение с заданным временем между завершением предыдущей и запуском новой задачи
        }

        public void stop() {
            mTimer.cancel();
        }
}
}
