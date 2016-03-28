package app;

import android.app.Application;

import utils.NetLoader;

/**
 * Created by nanchaodong on 16/3/28.
 */
public class App extends Application{
    @Override
    public void onCreate() {
        super.onCreate();
        NetLoader.getInstance(3, NetLoader.Type.LIFO);
    }
}
