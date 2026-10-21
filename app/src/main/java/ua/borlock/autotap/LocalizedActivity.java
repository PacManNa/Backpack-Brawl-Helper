package ua.borlock.autotap;

import android.app.Activity;
import android.content.Context;

/** Keeps activity resources and native dialogs in the selected app language. */
abstract class LocalizedActivity extends Activity {
    private String appliedLanguage;

    @Override protected void attachBaseContext(Context base) {
        appliedLanguage = AppLanguage.language(base);
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override protected void onResume() {
        super.onResume();
        if (!AppLanguage.language(this).equals(appliedLanguage)) recreate();
    }
}
