package ca.jetsphere.robocar;

import android.app.Application;

public class RoboCarApplication extends Application {

    private static RoboCarApplication s_instance;

    public RoboCarApplication() {
        s_instance = this;
    }

    public static RoboCarApplication getInstance() {
        return s_instance;
    }
}
