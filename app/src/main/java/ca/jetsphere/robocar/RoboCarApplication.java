package ca.jetsphere.robocar;

import android.app.Application;
import android.os.Handler;

public class RoboCarApplication extends Application {

    private static RoboCarApplication s_instance;
    Handler.Callback realCallback = null;

    /**
     *
     */
    public RoboCarApplication() {
        s_instance = this;
    }

    /**
     *
     */
    public static RoboCarApplication getInstance() {
        return s_instance;
    }

    /**
     *
     */
    Handler handler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (realCallback != null) {
                realCallback.handleMessage(msg);
            }
        };
    };

    /**
     *
     */
    public Handler getHandler() {
        return handler;
    }

    /**
     *
     */
    public void setCallBack(Handler.Callback callback) {
        this.realCallback = callback;
    }
}
