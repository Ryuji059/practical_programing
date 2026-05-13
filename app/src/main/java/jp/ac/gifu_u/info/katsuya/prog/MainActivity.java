package jp.ac.gifu_u.info.katsuya.prog;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {
    // センサを管理するマネージャ
    private SensorManager manager;
    // センサを管理するマネージャ
    private LocationManager locationManager;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // マネージャを取得
        manager = (SensorManager)getSystemService(SENSOR_SERVICE);
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
    }

    @Override
    public void onResume(){
        super.onResume();
        // 明るさセンサ(TYPE_LIGHT)のリストを取得
//        List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_LIGHT);
        List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);
        // ひとつ以上見つかったら、最初のセンサを取得してリスナーに登録
        if (sensors.size() != 0) {
            Sensor sensor = sensors.get(0);
            manager.registerListener(
                    this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // GPS から 1000msec または 10m 移動するごとにリスナーを呼び出し
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // 位置情報を使う処理
            // GPS から 1000msec または 10m 移動するごとにリスナーを呼び出し
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000, 10, this);
        } else {
            requestPermissions(
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    100
            );
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 一時停止の際にリスナー登録を解除
        manager.unregisterListener(this);
        locationManager.removeUpdates(this);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
//        // 明るさセンサが変化したとき
//        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
//            // 明るさの値（単位ルクス）を取得
//            float intensity = event.values[0];
//            // 結果をテキストとして表示
//            String str = Float.toString(intensity) + "ルクス";
//            TextView textview =
//                    (TextView) findViewById(R.id.status_text);
//            textview.setText(str);
//        }
        // 地磁気センサが変化したとき
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            // 地磁気の値を X・Y・Z 方向ごとに取得
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            // 結果をテキストとして表示
            String str = Float.toString(x) + ","
                    + Float.toString(y) + "," + Float.toString(z);
            TextView textview =
                    (TextView) findViewById(R.id.status_text);
            textview.setText(str);
        }

    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        // 得られた緯度経度の情報を表示
        double lat = location.getLatitude(); // 緯度
        double lng = location.getLongitude(); // 経度
        Toast.makeText(this,
                String.format("%.3f %.3f", lat, lng),
                Toast.LENGTH_SHORT).show();
    }
}