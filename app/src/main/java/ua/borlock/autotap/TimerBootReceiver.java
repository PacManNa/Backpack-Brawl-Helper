package ua.borlock.autotap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Відновлює локальні відліки після перезапуску пристрою. */
public final class TimerBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            TimerStore.handleBootCompleted(context);
        }
    }
}
