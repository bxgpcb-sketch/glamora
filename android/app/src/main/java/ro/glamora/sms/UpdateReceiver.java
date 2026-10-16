package ro.glamora.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Shows Android's confirmation screen for the update and records the result. */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(confirm); } catch (Exception e) { Sender.log(c, "Nu pot deschide ecranul de instalare."); }
            }
        } else if (st == PackageInstaller.STATUS_SUCCESS) {
            Sender.log(c, "Actualizare instalată.");
        } else if (st != PackageInstaller.STATUS_FAILURE_ABORTED) {
            Sender.log(c, "Actualizarea nu s-a instalat: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        }
    }
}
