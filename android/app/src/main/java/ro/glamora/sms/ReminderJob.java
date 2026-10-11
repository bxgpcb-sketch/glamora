package ro.glamora.sms;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Runs about every hour; sends tomorrow's reminders once the chosen hour has passed. */
public class ReminderJob extends JobService {
    static final int JOB_ID = 4202;

    public static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        js.cancel(4201); // old hourly job from the first version
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(c, ReminderJob.class))
                .setPeriodic(15 * 60 * 1000L, 5 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();
        js.schedule(info);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context app = getApplicationContext();
        new Thread(() -> {
            Sender.scanInbox(app);
            Sender.sendPendingConfirmations(app);
            Sender.publishAuto(app);
            Sender.log(app, Sender.run(app, false));
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
