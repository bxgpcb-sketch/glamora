package ro.glamora.sms;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Hands the downloaded update to Android's installer (read-only, only update.apk from the app cache). */
public class ApkProvider extends ContentProvider {
    static final String AUTHORITY = "ro.glamora.sms.apk";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/update.apk");

    File file() { return new File(getContext().getCacheDir(), "update.apk"); }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"update.apk".equals(uri.getLastPathSegment())) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{"glamora.apk", file().length()});
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
