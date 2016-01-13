package io.socketio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
    // Sensor stuff
    SensorManager mSensorManager;
    Sensor accel;
    Sensor compass;
    Sensor gyro;
    float[] orientation;
    float[] Rot;
    float[] mMagneticValues;
    float[] mAccelerometerValues;

    // socket.io stuff
    JSONArray json;
    private int statusThreadInterval = 1000; // 1 second by default, can be changed later
    private Handler statusThreadHandler;
    private Handler emitterThreadHandler;
    public TextView directionText = null;// = (TextView) findViewById(R.id.direction_text);;

    private Socket mServerSocket;
    {
        try {
            mServerSocket = IO.socket("http://192.168.1.134:3000");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    // thread for sending sensor data to server (periodic, controlled by statusThreadInterval)
    Runnable mStatusSenderRunnable = new Runnable() {
        @Override
        public void run() {
            // convert data to JSON Array
            try {
                json = new JSONArray(Arrays.toString(orientation));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            mServerSocket.emit("chat message", json);
            statusThreadHandler.postDelayed(mStatusSenderRunnable, statusThreadInterval);
        }
    };

    // function to start status thread
    void startStatusTask() {
        mStatusSenderRunnable.run();
    };

    // function to stop status thread
    void stopStatusTask() {
        statusThreadHandler.removeCallbacks(mStatusSenderRunnable);
    };

    protected Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            //Log.d("NSY",(String) msg.obj);
            TextView tv = (TextView) findViewById(R.id.direction_text);
            tv.setText((String) msg.obj);

            //Log.d("NSY2",directionText.getText().toString());
            //directionText.setText((String) msg.obj);
            // Set a switch statement to toggle it on or off.
            /*
            switch(msg.what)
            {
                case SHOW_LOG:
                {
                    ads.setVisibility(View.VISIBLE);
                    break;
                }
                case HIDE_LOG:
                {
                    ads.setVisibility(View.GONE);
                    break;
                }
            }
            */
        }
    };

    public void showDirection(String direction)
    {
        //Log.d("NSY2",direction);
        Message msg = Message.obtain();
        msg.obj = direction;
        handler.sendMessage(msg);
    }

    // socket.io listener - extract info, update UI (will control ioio in the future)
    private Emitter.Listener displayDirection = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            JSONObject data = (JSONObject) args[0];
            final String direction;
            try {
                direction = data.getString("direction");
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            //updateView(direction);
            //Toast.makeText(MainActivity.this, direction, Toast.LENGTH_SHORT).show();
            //TextView tv = (TextView) findViewById(R.id.direction_text);
            //tv.setText(direction);

            /*
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    directionText.setText(direction);
                }
            });
            */

            showDirection(direction);
            System.out.println("direction:"+direction);
            //Log.d("direction",direction);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // update UI when we receive a command
        mServerSocket.on("display direction", displayDirection);

        // start socket.io socket
        mServerSocket.connect();

        // start sensor manager
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        compass = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        statusThreadHandler = new Handler();

        // start thread for sending status data to server
        startStatusTask();

        // set up UI
        directionText = (TextView) findViewById(R.id.direction_text);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mServerSocket.emit("chat message", "hello from android!");
                //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                //        .setAction("Action", null).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, compass, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);

        Rot = new float[9];
        orientation = new float[3];
        mMagneticValues = new float[3];
        mAccelerometerValues = new float[3];
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        mServerSocket.disconnect();
        stopStatusTask();
        mSensorManager.unregisterListener(this);
    }


    /***************************************************************  sensors  ***************************************************************/
    @Override
    public void onSensorChanged(SensorEvent event)
    {
        switch (event.sensor.getType())
        {
            case Sensor.TYPE_MAGNETIC_FIELD:
                mMagneticValues[0] = event.values[0];
                mMagneticValues[1] = event.values[1];
                mMagneticValues[2] = event.values[2];
                break;

            case Sensor.TYPE_GYROSCOPE:
                //gyroscope_values[0] = event.values[0];
                //gyroscope_values[1] = event.values[1];
                //gyroscope_values[2] = event.values[2];
                //gyroView.setText("Gyro: " + "\n"+ gyroscope_values[0] + "\n" + gyroscope_values[1] +"\n"+ gyroscope_values[2]);
                break;

            case Sensor.TYPE_ACCELEROMETER:
                mAccelerometerValues[0] = event.values[0];
                mAccelerometerValues[1] = event.values[1];
                mAccelerometerValues[2] = event.values[2];
                //accelView.setText("Accel: " + "\n"+ mAccelerometerValues[0] + "\n" + mAccelerometerValues[1] +"\n"+ mAccelerometerValues[2]);

                break;

            case Sensor.TYPE_PROXIMITY:
                //proxValue[0] = event.values[0];
                //proxView.setText("Prox: " + "\n"+ proxValue[0]);

                break;
        }
        SensorManager.getRotationMatrix(Rot, null, mAccelerometerValues, mMagneticValues);
        SensorManager.getOrientation(Rot, orientation);
        orientation[0] = (float) Math.toDegrees(orientation[0]);
        orientation[1] = (float) Math.toDegrees(orientation[1]);
        orientation[2] = (float) Math.toDegrees(orientation[2]);
        //mServerSocket.emit("chat message", "orientation:" + orientation[0] + "," + orientation[1] + "," + orientation[2] + "\n");
        /*
        try {
            json = new JSONObject(Arrays.toString(orientation));
        } catch (JSONException e) {
            e.printStackTrace();
        }
*/
        //json = new JSONObject(Arrays.toString(orientation));


        //mServerSocket.emit("chat message", Arrays.toString(orientation));
        //compassView.setText("Compass: " + "\n"+ orientation[0] + "\n" + orientation[1] +"\n"+ orientation[2]);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
