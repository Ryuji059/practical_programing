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
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    private enum AppMode {
        MAP,            // 通常地図
        RECORDING,      // 記録中
        HISTORY,        // 履歴表示
        HISTORY_DETAIL, // 走行経路の表示
        EDIT_ROAD       // 道路色分け
    }
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
    private RoutePoint lastRoutePoint = null;//今の地点
    private AppMode currentMode = AppMode.MAP;//現在のモード
    private View mapLayout;
    private View historyLayout;
    private View recordPanel;
    private View roadEditPanel;
    private TextView titleBar;
    private LinearLayout historyList;
    private MapView historyMap;
    private View historyDetailLayout;
    private Polyline historyRouteLine;
    private View routeDetailPanel;
    private boolean detailPanelOpen = false;
    private TextView detailDistance;
    private TextView detailTime;
    private TextView detailAverageSpeed;
    private TextView detailMaxGpsSpeed;
    private TextView detailMaxSectionSpeed;
    // 日付・ルート名
    private TextView detailDate;
    private TextView detailRouteName;

    // 走行分析
    private TextView detailMovingTime;
    private TextView detailStopTime;
    private TextView detailStopCount;
    private TextView detailLongestStopTime;
    private TextView detailMovingAverageSpeed;

    // 時間割合
    private TextView detailMovingRatio;
    private TextView detailStopRatio;

    // 速度内訳
    private TextView detailSpeed0to5;
    private TextView detailSpeed5to10;
    private TextView detailSpeed10to15;
    private TextView detailSpeed15to20;
    private TextView detailSpeed20Over;

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

        //各モードのレイアウトを取得
        mapLayout = findViewById(R.id.mapLayout);
        historyLayout = findViewById(R.id.historyLayout);
        recordPanel = findViewById(R.id.recordPanel);
        roadEditPanel = findViewById(R.id.roadEditPanel);
        titleBar = findViewById(R.id.titleBar);
        historyList = findViewById(R.id.historyList);
        //走行経路表示用のMAPの初期化
        historyDetailLayout = findViewById(R.id.historyDetailLayout);

        historyMap = findViewById(R.id.historyMap);
        historyMap.setTileSource(TileSourceFactory.MAPNIK);
        historyMap.setMultiTouchControls(true);

        historyRouteLine = new Polyline();
        historyRouteLine.setColor(Color.BLUE);
        historyRouteLine.setWidth(8.0f);
        historyMap.getOverlays().add(historyRouteLine);
        //詳細データ表示用レイアウト群の取得
        routeDetailPanel = findViewById(R.id.routeDetailPanel);
        detailDistance = findViewById(R.id.detailDistance);
        detailTime = findViewById(R.id.detailTime);
        detailAverageSpeed = findViewById(R.id.detailAverageSpeed);
        detailMaxGpsSpeed = findViewById(R.id.detailMaxGpsSpeed);
        detailMaxSectionSpeed = findViewById(R.id.detailMaxSectionSpeed);
        detailDate = findViewById(R.id.detailDate);
        detailRouteName = findViewById(R.id.detailRouteName);
        detailMovingTime = findViewById(R.id.detailMovingTime);
        detailStopTime = findViewById(R.id.detailStopTime);
        detailStopCount = findViewById(R.id.detailStopCount);
        detailLongestStopTime = findViewById(R.id.detailLongestStopTime);
        detailMovingAverageSpeed = findViewById(R.id.detailMovingAverageSpeed);
        detailMovingRatio = findViewById(R.id.detailMovingRatio);
        detailStopRatio = findViewById(R.id.detailStopRatio);
        detailSpeed0to5 = findViewById(R.id.detailSpeed0to5);
        detailSpeed5to10 = findViewById(R.id.detailSpeed5to10);
        detailSpeed10to15 = findViewById(R.id.detailSpeed10to15);
        detailSpeed15to20 = findViewById(R.id.detailSpeed15to20);
        detailSpeed20Over = findViewById(R.id.detailSpeed20Over);
        //ボタンのID取得
        Button btnHistoryMode = findViewById(R.id.btnHistoryMode);
        Button btnRoadEditMode = findViewById(R.id.btnRoadEditMode);
        TextView btnBackMapFromHistory = findViewById(R.id.btnBackMapFromHistory);
        Button btnSaveRoadEdit = findViewById(R.id.btnSaveRoadEdit);
        Button btnFinishRoadEdit = findViewById(R.id.btnFinishRoadEdit);
        TextView btnBackHistory = findViewById(R.id.btnBackHistory);

        //各種ボタンの機能実装
        btnHistoryMode.setOnClickListener(v -> changeMode(AppMode.HISTORY));
        btnRoadEditMode.setOnClickListener(v -> changeMode(AppMode.EDIT_ROAD));
        btnBackMapFromHistory.setOnClickListener(v -> changeMode(AppMode.MAP));
        btnSaveRoadEdit.setOnClickListener(v -> {
            // 色分けデータをJSONに保存
        });
        btnFinishRoadEdit.setOnClickListener(v -> {changeMode(AppMode.MAP);});
        btnBackHistory.setOnClickListener(v -> changeMode(AppMode.HISTORY));


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
        routeLine.setColor(Color.BLUE);
        routeLine.setWidth(8.0f);
        map.getOverlays().add(routeLine);

        //記録開始、停止ボタン
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        //記録開始ボタン
        btnStart.setOnClickListener(v -> {
            if (isRecording) {
                Toast.makeText(this, "すでに記録中です", Toast.LENGTH_SHORT).show();
                return;
            }
            isRecording = true;
            changeMode(AppMode.RECORDING);

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
            if (!isRecording) {
                Toast.makeText(this, "現在は記録中ではありません", Toast.LENGTH_SHORT).show();
                return;
            }
            isRecording = false;
            endTime = System.currentTimeMillis();//終了時刻を記録

            saveRouteToJson();//JSONファイルに保存

            //終了を通知
            Toast.makeText(this, "記録を停止して保存しました", Toast.LENGTH_SHORT).show();
        });

        //走行履歴の詳細データの表示用パネルの設定
        routeDetailPanel.setOnClickListener(v -> {
            if (detailPanelOpen) {
                routeDetailPanel.animate().translationY(dp(240)).setDuration(250).start();
                detailPanelOpen = false;
            } else {
                routeDetailPanel.animate().translationY(0f).setDuration(250).start();
                detailPanelOpen = true;
            }
        });
        startLocationUpdates();
        changeMode(AppMode.MAP);
    }


    @Override
    protected void onResume() {
        super.onResume();//地図通信の再開
        if (map != null) {
            map.onResume();
        }
        if (historyMap != null) {
            historyMap.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();//地図通信の停止
        if (map != null) {
            map.onPause();
        }
        if (historyMap != null) {
            historyMap.onPause();
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
    private void changeMode(AppMode mode) {
        currentMode = mode;

        mapLayout.setVisibility(View.GONE);
        historyLayout.setVisibility(View.GONE);
        historyDetailLayout.setVisibility(View.GONE);

        if (mode == AppMode.HISTORY) {
            historyLayout.setVisibility(View.VISIBLE);
            isRecording = false;
            loadHistoryList();
            return;
        }

        if (mode == AppMode.HISTORY_DETAIL) {
            historyDetailLayout.setVisibility(View.VISIBLE);
            isRecording = false;
            return;
        }

        mapLayout.setVisibility(View.VISIBLE);

        if (mode == AppMode.MAP) {
            titleBar.setText("自転車安全マップ");
            titleBar.setBackgroundColor(Color.rgb(67, 160, 71));
            recordPanel.setVisibility(View.VISIBLE);
            roadEditPanel.setVisibility(View.GONE);
        } else if (mode == AppMode.EDIT_ROAD) {
            titleBar.setText("色分けモード");
            titleBar.setBackgroundColor(Color.rgb(70, 170, 220));
            recordPanel.setVisibility(View.GONE);
            roadEditPanel.setVisibility(View.VISIBLE);
            isRecording = false;
        } else if (mode == AppMode.RECORDING) {
            titleBar.setText("記録中");
            recordPanel.setVisibility(View.VISIBLE);
            roadEditPanel.setVisibility(View.GONE);
        }
    }

    private void loadHistoryList() {
        historyList.removeAllViews();

        File routeDir = new File(getFilesDir(), "routes");

        if (!routeDir.exists()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("保存された走行履歴はありません");
            emptyText.setTextSize(18);
            historyList.addView(emptyText);
            return;
        }

        File[] files = routeDir.listFiles();

        if (files == null || files.length == 0) {
            TextView emptyText = new TextView(this);
            emptyText.setText("保存された走行履歴はありません");
            emptyText.setTextSize(18);
            historyList.addView(emptyText);
            return;
        }

        for (File file : files) {
            if (!file.getName().endsWith(".json")) {
                continue;
            }

            try {
                String jsonText = readTextFile(file);
                JSONObject json = new JSONObject(jsonText);

                long start = json.getLong("startTime");
                long end = json.getLong("endTime");
                double distance = json.getDouble("totalDistance");

                String dateText = new SimpleDateFormat(
                        "yyyy/MM/dd HH:mm",
                        Locale.JAPAN
                ).format(new Date(start));

                long sec = (end - start) / 1000;
                long min = sec / 60;
                long remainSec = sec % 60;

                String distanceText = String.format(
                        Locale.JAPAN,
                        "%.2f km",
                        distance / 1000.0
                );

                String timeText = String.format(
                        Locale.JAPAN,
                        "%02d:%02d",
                        min,
                        remainSec
                );

                Button historyButton = new Button(this);
                historyButton.setText(
                        dateText + "\n" +
                                distanceText + "\n" +
                                timeText
                );

                historyButton.setOnClickListener(v -> {
                    loadRouteOnHistoryMap(file);
                    changeMode(AppMode.HISTORY_DETAIL);
                });

                historyList.addView(historyButton);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String readTextFile(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);

        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();

        return new String(data, StandardCharsets.UTF_8);
    }

    private void loadRouteOnMap(File file) {
        try {
            String jsonText = readTextFile(file);
            JSONObject json = new JSONObject(jsonText);
            JSONArray pointsArray = json.getJSONArray("points");

            ArrayList<GeoPoint> geoPoints = new ArrayList<>();

            for (int i = 0; i < pointsArray.length(); i++) {
                JSONObject pointJson = pointsArray.getJSONObject(i);

                double lat = pointJson.getDouble("lat");
                double lon = pointJson.getDouble("lon");

                geoPoints.add(new GeoPoint(lat, lon));
            }

            routeLine.setPoints(geoPoints);

            if (!geoPoints.isEmpty()) {
                map.getController().animateTo(geoPoints.get(0));
                map.getController().setZoom(18.0);
            }

            map.invalidate();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "履歴の読み込みに失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadRouteOnHistoryMap(File file) {
        try {
            String jsonText = readTextFile(file);
            JSONObject json = new JSONObject(jsonText);
            JSONArray pointsArray = json.getJSONArray("points");

            long startTime = json.getLong("startTime");
            long endTime = json.getLong("endTime");
            double totalDistance = json.getDouble("totalDistance");
            double averageSpeed = json.getDouble("averageSpeed");

            long elapsedSec = (endTime - startTime) / 1000;

            detailDate.setText(new SimpleDateFormat(
                    "yyyy/MM/dd HH:mm",
                    Locale.JAPAN
            ).format(new Date(startTime)));

            detailRouteName.setText("走行ルート");

            detailDistance.setText(String.format(
                    Locale.JAPAN,
                    "走行距離: %.2f km",
                    totalDistance / 1000.0
            ));

            detailTime.setText(String.format(
                    Locale.JAPAN,
                    "走行時間: %d分%02d秒",
                    elapsedSec / 60,
                    elapsedSec % 60
            ));

            detailAverageSpeed.setText(String.format(
                    Locale.JAPAN,
                    "平均速度: %.1f km/h",
                    averageSpeed * 3.6
            ));

            double maxGpsSpeed = 0.0;
            double maxSectionSpeed = 0.0;

            double movingTime = 0.0;
            double stopTime = 0.0;
            double longestStopTime = 0.0;
            double currentStopTime = 0.0;
            int stopCount = 0;
            boolean wasStopping = false;

            double speed0to5Time = 0.0;
            double speed5to10Time = 0.0;
            double speed10to15Time = 0.0;
            double speed15to20Time = 0.0;
            double speed20OverTime = 0.0;

            ArrayList<GeoPoint> geoPoints = new ArrayList<>();

            for (int i = 0; i < pointsArray.length(); i++) {
                JSONObject now = pointsArray.getJSONObject(i);

                double lat = now.getDouble("lat");
                double lon = now.getDouble("lon");
                geoPoints.add(new GeoPoint(lat, lon));

                double gpsSpeed = now.getDouble("speed");
                if (gpsSpeed > maxGpsSpeed) {
                    maxGpsSpeed = gpsSpeed;
                }

                if (i == 0) {
                    continue;
                }

                JSONObject prev = pointsArray.getJSONObject(i - 1);

                double prevDistance = prev.getDouble("distance");
                double nowDistance = now.getDouble("distance");

                long prevTime = prev.getLong("time");
                long nowTime = now.getLong("time");

                double diffDistance = nowDistance - prevDistance;
                double diffTime = (nowTime - prevTime) / 1000.0;

                if (diffTime <= 0) {
                    continue;
                }

                double sectionSpeed = diffDistance / diffTime; // m/s
                double sectionSpeedKmh = sectionSpeed * 3.6;

                if (sectionSpeed > maxSectionSpeed) {
                    maxSectionSpeed = sectionSpeed;
                }

                // 2km/h未満を停止扱い
                if (sectionSpeedKmh < 2.0) {
                    stopTime += diffTime;
                    currentStopTime += diffTime;

                    if (!wasStopping) {
                        stopCount++;
                        wasStopping = true;
                    }

                    if (currentStopTime > longestStopTime) {
                        longestStopTime = currentStopTime;
                    }

                } else {
                    movingTime += diffTime;
                    currentStopTime = 0.0;
                    wasStopping = false;

                    if (sectionSpeedKmh < 5.0) {
                        speed0to5Time += diffTime;
                    } else if (sectionSpeedKmh < 10.0) {
                        speed5to10Time += diffTime;
                    } else if (sectionSpeedKmh < 15.0) {
                        speed10to15Time += diffTime;
                    } else if (sectionSpeedKmh < 20.0) {
                        speed15to20Time += diffTime;
                    } else {
                        speed20OverTime += diffTime;
                    }
                }
            }

            double movingAverageSpeed = 0.0;
            if (movingTime > 0) {
                movingAverageSpeed = totalDistance / movingTime; // m/s
            }

            double movingRatio = 0.0;
            double stopRatio = 0.0;
            if (elapsedSec > 0) {
                movingRatio = movingTime / elapsedSec * 100.0;
                stopRatio = stopTime / elapsedSec * 100.0;
            }

            double totalMovingTimeForSpeed = movingTime;
            if (totalMovingTimeForSpeed <= 0) {
                totalMovingTimeForSpeed = 1.0;
            }

            detailMovingTime.setText(String.format(
                    Locale.JAPAN,
                    "移動時間: %d分%02d秒",
                    (long) movingTime / 60,
                    (long) movingTime % 60
            ));

            detailStopTime.setText(String.format(
                    Locale.JAPAN,
                    "停止時間: %d分%02d秒",
                    (long) stopTime / 60,
                    (long) stopTime % 60
            ));

            detailStopCount.setText(String.format(
                    Locale.JAPAN,
                    "停止回数: %d回",
                    stopCount
            ));

            detailLongestStopTime.setText(String.format(
                    Locale.JAPAN,
                    "最長停止時間: %d分%02d秒",
                    (long) longestStopTime / 60,
                    (long) longestStopTime % 60
            ));

            detailMovingAverageSpeed.setText(String.format(
                    Locale.JAPAN,
                    "移動中平均速度: %.1f km/h",
                    movingAverageSpeed * 3.6
            ));

            detailMaxSectionSpeed.setText(String.format(
                    Locale.JAPAN,
                    "最高区間速度: %.1f km/h",
                    maxSectionSpeed * 3.6
            ));

            detailMovingRatio.setText(String.format(
                    Locale.JAPAN,
                    "移動: %.1f%%",
                    movingRatio
            ));

            detailStopRatio.setText(String.format(
                    Locale.JAPAN,
                    "停止: %.1f%%",
                    stopRatio
            ));

            detailSpeed0to5.setText(String.format(
                    Locale.JAPAN,
                    "0～5 km/h: %.1f%%",
                    speed0to5Time / totalMovingTimeForSpeed * 100.0
            ));

            detailSpeed5to10.setText(String.format(
                    Locale.JAPAN,
                    "5～10 km/h: %.1f%%",
                    speed5to10Time / totalMovingTimeForSpeed * 100.0
            ));

            detailSpeed10to15.setText(String.format(
                    Locale.JAPAN,
                    "10～15 km/h: %.1f%%",
                    speed10to15Time / totalMovingTimeForSpeed * 100.0
            ));

            detailSpeed15to20.setText(String.format(
                    Locale.JAPAN,
                    "15～20 km/h: %.1f%%",
                    speed15to20Time / totalMovingTimeForSpeed * 100.0
            ));

            detailSpeed20Over.setText(String.format(
                    Locale.JAPAN,
                    "20 km/h～: %.1f%%",
                    speed20OverTime / totalMovingTimeForSpeed * 100.0
            ));

            detailMaxGpsSpeed.setText(String.format(
                    Locale.JAPAN,
                    "最高GPS速度: %.1f km/h",
                    maxGpsSpeed * 3.6
            ));

            historyRouteLine.setPoints(geoPoints);

            if (!geoPoints.isEmpty()) {
                historyMap.getController().setZoom(18.0);
                historyMap.getController().animateTo(geoPoints.get(0));
            }

            historyMap.invalidate();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "履歴の読み込みに失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}