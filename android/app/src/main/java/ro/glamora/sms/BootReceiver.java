package ro.glamora.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try { ReminderJob.schedule(context.getApplicationContext()); } catch (Throwable t) { Sender.log(context.getApplicationContext(), "Eroare la pornire: " + t); }
    }
}
