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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    private MapView map;//地図のインスタンス
    private LocationManager locationManager;//位置管理用
    private Marker currentMarker;//現在位置のピン
    private GeoPoint currentPoint;//現在位置の保存
    private Polyline routeLine;
    private boolean isRecording = false;//記録中かどうかのフラグ
    private ArrayList<RoutePoint> routePoints = new ArrayList<>();//記録したポイントの配列
    private long startTime = 0;//記録開始の時刻
    private long endTime = 0;//記録終了の時刻
    private double totalDistance = 0.0;//記録中の走行距離
    private RoutePoint lastRoutePoint = null;

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

        //線を引くための準備
        routeLine = new Polyline();
        map.getOverlays().add(routeLine);

        //記録開始、停止ボタン
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        //記録開始ボタン
        btnStart.setOnClickListener(v -> {
            isRecording = true;

            //初期化
            routePoints.clear();
            totalDistance = 0.0;//走行距離を0に
            lastRoutePoint = null;//最新のポイントを消す
            startTime = System.currentTimeMillis();//開始時刻の記録

            routeLine.setPoints(new ArrayList<>());
            map.invalidate();
            //現在地の記録
            if (currentPoint != null) {
                RoutePoint firstPoint = new RoutePoint(
                        currentPoint.getLatitude(),
                        currentPoint.getLongitude(),
                        startTime,
                        0.0f,
                        0.0
                );

                routePoints.add(firstPoint);
                lastRoutePoint = firstPoint;
                updateRouteLine();//ルートラインの更新
            }
            //開始を通知
            Toast.makeText(this, "記録を開始しました", Toast.LENGTH_SHORT).show();
        });

        //終了ボタン
        btnStop.setOnClickListener(v -> {
            isRecording = false;
            endTime = System.currentTimeMillis();//終了時刻を記録

            saveRouteToJson();//JSONファイルに保存

            //終了を通知
            Toast.makeText(this, "記録を停止して保存しました", Toast.LENGTH_SHORT).show();
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
                    long time = System.currentTimeMillis();//記録時刻を記録
                    float speed = location.hasSpeed() ? location.getSpeed() : 0.0f;//GPSから速度を取得

                    //走行距離の更新
                    if (lastRoutePoint != null) {
                        float[] result = new float[1];//答えを入れるための配列
                        //直前の地点から今の地点の距離を計算
                        Location.distanceBetween(
                                lastRoutePoint.lat,
                                lastRoutePoint.lon,
                                lat,
                                lon,
                                result
                        );
                        //増えた分を加算
                        totalDistance += result[0];
                    }
                    //RoutePointクラスに格納
                    RoutePoint routePoint = new RoutePoint(
                            lat,
                            lon,
                            time,
                            speed,
                            totalDistance
                    );
                    //配列に追加
                    routePoints.add(routePoint);
                    lastRoutePoint = routePoint;//lastRoutePointを更新

                    updateRouteLine();//ルートラインを更新
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

    /**
     * 線を引けるような形にする関数
     */
    private void updateRouteLine() {
        //routePointからgeoPointに変換
        ArrayList<GeoPoint> geoPoints = new ArrayList<>();
        //
        for (RoutePoint p : routePoints) {//各routePointから緯度経度を取り出してgeoPointを作る
            geoPoints.add(new GeoPoint(p.lat, p.lon));
        }
        //線を引く
        routeLine.setPoints(geoPoints);
        map.invalidate();
    }

    /**
     * 記録した走行ルートをJSONファイルとして保存する関数
     * JSONファイルの構造例
     * {
     *   "startTime": 123456789,
     *   "endTime": 123456999,
     *   "totalDistance": 350.5,
     *   "averageSpeed": 4.2,
     *   "points": [
     *     {
     *       "lat": 35.464,
     *       "lon": 136.735,
     *       "time": 123456789,
     *       "speed": 3.5,
     *       "distance": 0
     *     }
     *   ]
     * }
     */
    private void saveRouteToJson() {
        try {
            if (routePoints.size() < 2) {//保存する点が1つ以下場合は保存しない
                Toast.makeText(this, "記録点が少なすぎます", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject routeJson = new JSONObject();//JSONObjectを作る {}<-これを作ってると考えてよし

            routeJson.put("startTime", startTime);//開始時刻をJSONに入れる
            routeJson.put("endTime", endTime);//終了時刻をJSONに入れる
            routeJson.put("totalDistance", totalDistance);//走行距離をJSONに入れる
            //平均速度を計算
            double elapsedSec = (endTime - startTime) / 1000.0;
            double averageSpeed = 0.0;

            if (elapsedSec > 0) {
                averageSpeed = totalDistance / elapsedSec; // m/s
            }

            routeJson.put("averageSpeed", averageSpeed);//平均速度をJSONに入れる

            JSONArray pointsArray = new JSONArray();//JSONArrayの作成(からの配列を作る)
            //各ルートポイントをJSONに保存
            for (RoutePoint p : routePoints) {
                JSONObject pointJson = new JSONObject();//JSONオブジェクトを作成

                pointJson.put("lat", p.lat);//緯度
                pointJson.put("lon", p.lon);//経度
                pointJson.put("time", p.time);//記録時刻
                pointJson.put("speed", p.speed);//GPSによる速度
                pointJson.put("distance", p.distance);//距離

                pointsArray.put(pointJson);//作成したJSONオブジェクトを配列に追加
            }

            routeJson.put("points", pointsArray);//routeJsonにpointsを追加
            //ファイル名を決める(route_(年)(月)(日)_(時)(分)(秒).json)
            String fileName = "route_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN)
                            .format(new Date(startTime)) +
                    ".json";
            //アプリ専用領域のrouteフォルダを探す
            File routeDir = new File(getFilesDir(), "routes");

            if (!routeDir.exists()) {//なければ作成
                routeDir.mkdir();
            }

            File file = new File(routeDir, fileName);//ファイルを作成

            FileOutputStream fos = new FileOutputStream(file);//ファイルを開く
            fos.write(routeJson.toString(4).getBytes());//ファイルに書き込み
            fos.close();//ファイルを閉じる
            Log.d("SAVE_ROUTE", file.getAbsolutePath());
            //保存が成功したことを通知
            Toast.makeText(this, "保存しました: " + fileName, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }
}