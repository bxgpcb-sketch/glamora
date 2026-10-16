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
            "com.sonyericsson.conversations", "com.motorola.messaging", "com.huawei.message", "com.hihonor.mms",
            "com.android.mms.service", "com.vivo.message", "com.transsion.message", "com.whatsapp", "com.whatsapp.w4b"));

    @Override
    public void onListenerConnected() {
        Fb.prefs(this).edit().putLong("chatListenerAt", System.currentTimeMillis()).apply();
        Sender.log(getApplicationContext(), "Citirea răspunsurilor din notificări e activă.");
    }

    @Override
    public void onListenerDisconnected() {
        Sender.log(getApplicationContext(), "Citirea răspunsurilor din notificări s-a oprit (telefonul a închis-o).");
        try { requestRebind(new android.content.ComponentName(this, ReplyListener.class)); } catch (Exception ignored) { }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle ex = n.extras;
            boolean msgApp = MESSAGING_APPS.contains(sbn.getPackageName())
                    || Notification.CATEGORY_MESSAGE.equals(n.category)
                    || ex.containsKey(Notification.EXTRA_MESSAGES);
            if (!msgApp) return;
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
            final String b = body, s = sender, app = sbn.getPackageName();
            final long when = sbn.getPostTime();
            new Thread(() -> {
                android.content.Context c = getApplicationContext();
                String r = Sender.handleReplyFromChat(c, s, b, when);
                Sender.log(c, r != null ? r : "Notificare „" + b.trim() + "” de la " + s + " (" + app + "): fără programare care așteaptă răspuns.");
            }).start();
        } catch (Exception ignored) { }
    }
}
