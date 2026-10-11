package ro.glamora.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Listens for client replies (1 / 0) and updates the appointment status. */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;
        final Map<String, StringBuilder> bySender = new LinkedHashMap<>();
        final long when = msgs[0] != null ? msgs[0].getTimestampMillis() : System.currentTimeMillis();
        for (SmsMessage m : msgs) {
            if (m == null) continue;
            String from = m.getDisplayOriginatingAddress();
            if (from == null) continue;
            StringBuilder sb = bySender.get(from);
            if (sb == null) { sb = new StringBuilder(); bySender.put(from, sb); }
            sb.append(m.getDisplayMessageBody());
        }
        final Context app = context.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                for (Map.Entry<String, StringBuilder> e : bySender.entrySet()) {
                    Sender.log(app, Sender.handleReply(app, e.getKey(), e.getValue().toString(), when));
                }
            } finally {
                pr.finish();
            }
        }).start();
    }
}
