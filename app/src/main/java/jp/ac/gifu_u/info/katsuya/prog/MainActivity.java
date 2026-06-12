package jp.ac.gifu_u.info.katsuya.prog;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.osmdroid.config.Configuration;//osmdroidの設定を行うクラス。
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;//地図データを使うため
import org.osmdroid.util.GeoPoint;//緯度経度を扱うクラス
import org.osmdroid.views.MapView;//地図表示
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private MapView map;//地図のインスタンス
    private LocationManager locationManager;//位置管理用
    private Marker currentMarker;//現在位置のピン
    private GeoPoint currentPoint;//現在位置の保存
    private ArrayList<GeoPoint> routePoints = new ArrayList<>();
    private Polyline routeLine;
    private boolean isRecording = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());//OpenStreetMapのサーバーへ「このアプリがアクセスしています」と名乗る
        //レイアウト読み込み
        setContentView(R.layout.activity_main);



        map = findViewById(R.id.map);//MapViewの取得
        map.setTileSource(TileSourceFactory.MAPNIK);//地図の種類を設定(今回はOpenStreetMap標準地図)
        map.setMultiTouchControls(true);//拡大縮小の許可

        GeoPoint startPoint = new GeoPoint(35.464, 136.735); // 岐阜駅付近を表示
        map.getController().setZoom(19.0);//地図の倍率設定
        map.getController().setCenter(startPoint);//startPointを中心に表示
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        startLocationUpdates();

        //現在地を画面の中心に持ってくるボタン
        Button btnCurrentLocation = findViewById(R.id.btnCurrentLocation);//ボタンのID取得
        //押されたときの処理
        btnCurrentLocation.setOnClickListener(v -> {
            if (currentPoint != null) {//現在地が記録されている場合
                map.getController().animateTo(currentPoint);//現在地を画面の中心に移動
                map.getController().setZoom(19.0);//ズームする
            } else {//現在地が保存されていない場合
                Toast.makeText(this, "現在地を取得中です", Toast.LENGTH_SHORT).show();//取得中であることを表示
            }
        });

        routeLine = new Polyline();
        map.getOverlays().add(routeLine);

        //記録開始、停止ボタン
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        btnStart.setOnClickListener(v -> {
            isRecording = true;
            routePoints.clear();//今までの記録を破棄
            if (currentPoint != null) {
                routePoints.add(currentPoint);//初期地点を記録
            }
            routeLine.setPoints(routePoints);//点の追加
            Toast.makeText(this, "記録を開始しました", Toast.LENGTH_SHORT).show();//記録開始の通知
        });

        btnStop.setOnClickListener(v -> {
            isRecording = false;
            Toast.makeText(this, "記録を停止しました", Toast.LENGTH_SHORT).show();//記録終了通知

            // 後でここに保存処理を書く
        });
    }


    @Override
    protected void onResume() {
        super.onResume();//地図通信の再開
        if (map != null) {
            map.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();//地図通信の停止
        if (map != null) {
            map.onPause();
        }
    }

    private void startLocationUpdates() {
        //権限確認
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {//権限を与えられてなかった場合
            //権限を要求する
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    100
            );
            return;
        }

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                //現在の緯度経度を取得
                double lat = location.getLatitude();
                double lon = location.getLongitude();

                currentPoint = new GeoPoint(lat, lon);//現在地を更新
                //記録中はroutePointsに現在地を記録
                if (isRecording) {
                    routeLine.setColor(Color.GREEN);//古い書き方(推奨されているのはpaint)
                    routeLine.setWidth(12f);//古い書き方(推奨されているのはpaint)
                    routePoints.add(currentPoint);//現在地を追加
                    routeLine.setPoints(routePoints);//点を追加
                    map.invalidate();//可視化
                }

                // 現在地マーカーの位置を更新
                if (currentMarker == null) {//ない場合は作成
                    currentMarker = new Marker(map);
                    currentMarker.setTitle("現在地");
                    map.getOverlays().add(currentMarker);
                }

                currentMarker.setPosition(currentPoint);
                currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                map.invalidate();
            }
        };
        //3秒または5メートル進んだらGPS情報を取得
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                3000,
                5,
                listener
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "位置情報の権限が必要です", Toast.LENGTH_SHORT).show();
            }
        }
    }
}