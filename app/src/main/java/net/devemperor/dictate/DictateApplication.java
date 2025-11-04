package net.devemperor.dictate;

import android.app.Application;

public class DictateApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DictateUtils.applyApplicationLocale(this);
    }
}
