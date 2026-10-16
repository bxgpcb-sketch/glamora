package ro.glamora.sms;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Catches client replies that arrive as chat (RCS) instead of SMS, by reading the new-message
 * notification of the phone's messaging app. Only "1"/"0"-style answers are used; everything else is ignored.
 * Works only after the colleague turns on Notification access for Glamora.
 */
public class ReplyListener extends NotificationListenerService {
    static final Set<String> MESSAGING_APPS = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "com.android.messaging", "com.oneplus.mms", "com.coloros.mms", "com.oplus.mms", "com.miui.mms",
            "com.sonyericsson.conversations", "com.motorola.messaging"));

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || !MESSAGING_APPS.contains(sbn.getPackageName())) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle ex = n.extras;
            String body = null, sender = null;
            Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (msgs != null && msgs.length > 0 && msgs[msgs.length - 1] instanceof Bundle) {
                Bundle last = (Bundle) msgs[msgs.length - 1];
                CharSequence t = last.getCharSequence("text");
                body = t == null ? null : t.toString();
                Object p = last.get("sender_person");
                if (p instanceof android.app.Person) {
                    android.app.Person person = (android.app.Person) p;
                    if (person.getUri() != null) sender = person.getUri();
                    if (sender == null && person.getName() != null) sender = person.getName().toString();
                }
                if (sender == null) { CharSequence s = last.getCharSequence("sender"); if (s != null) sender = s.toString(); }
            }
            if (body == null) { CharSequence t = ex.getCharSequence(Notification.EXTRA_TEXT); body = t == null ? null : t.toString(); }
            if (sender == null) { CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE); sender = t == null ? null : t.toString(); }
            if (body == null || sender == null || Sender.replyStatus(body) == null) return;
            final String b = body, s = sender;
            final long when = sbn.getPostTime();
            new Thread(() -> Sender.log(getApplicationContext(), Sender.handleReplyFromChat(getApplicationContext(), s, b, when))).start();
        } catch (Exception ignored) { }
    }
}
