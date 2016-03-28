package com.wolf.net;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import utils.NetLoader;

public class MainActivity extends AppCompatActivity {
    private TextView tv_net;
    private String url = "https://www.baidu.com/baidu";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_net = (TextView) findViewById(R.id.tv_net);
        NetLoader.getInstance().loadGetData(url, new NetLoader.NetResponseListener() {

            @Override
            public void success(String resultString) {
                // TODO Auto-generated method stub
                tv_net.setText(resultString);
                Log.i("tag+ 成功", resultString);
            }

            @Override
            public void fail(String failString, Exception e) {
                // TODO Auto-generated method stub
                Log.i("tag + 失败", failString);
            }
        });


    }
}
