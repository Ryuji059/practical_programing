package jp.ac.gifu_u.info.katsuya.prog;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/**
 * 画面がバックグラウンドに移動しても走行位置を記録し続けるService
 *
 * 主な役割
 * ・GPSとネットワークから位置情報を取得する
 * ・GPSの誤差や停止中の小さなブレを除外する
 * ・走行ルートをJSONファイルへ保存する
 * ・走行終了後に統計データを更新する
 * ・MainActivityへ現在位置や記録中ルートをBroadcastで送る
 */
public class LocationTrackingService extends Service {
    /**
     * 1回の走行から計算した統計値をまとめて保持する内部クラス
     *
     * JSONへ直接保存する前に、移動時間、停止時間、最高速度、
     * 速度帯ごとの時間などを一時的に保存するために使用する。
     */
    private static class RideStatistics {
        long rideTimeSec;

        double movingTimeSec;
        double stopTimeSec;

        int stopCount;
        double longestStopTime;

        double averageSpeed;
        double movingAverageSpeed;

        double maxGpsSpeed;

        double time0to5;
        double time5to10;
        double time10to15;
        double time15to20;
        double time20to25;
        double time25to30;
        double time30Over;
    }

    //MainActivityから走行記録開始を指示するときのAction
    public static final String ACTION_START = "jp.ac.gifu_u.info.katsuya.prog.ACTION_START_TRACKING";
    //MainActivityから走行記録停止を指示するときのAction
    public static final String ACTION_STOP = "jp.ac.gifu_u.info.katsuya.prog.ACTION_STOP_TRACKING";

    //フォアグラウンドサービスの通知チャンネルID
    private static final String CHANNEL_ID = "tracking_channel";
    //走行記録中の常駐通知に使用する通知ID
    private static final int NOTIFICATION_ID = 1001;

    //GPSやネットワーク位置情報を管理するクラス
    private LocationManager locationManager;
    //位置情報が更新されたときに呼ばれるリスナー
    private LocationListener locationListener;

    //記録したすべての走行地点を保存するリスト
    private ArrayList<RoutePoint> routePoints = new ArrayList<>();

    //現在走行記録中かどうかを示すフラグ
    private boolean isRecording = false;
    //走行記録の開始時刻
    private long startTime = 0;
    //走行記録の終了時刻
    private long endTime = 0;
    //現在の走行における累計距離。単位はメートル
    private double totalDistance = 0.0;
    //直前に保存した走行地点。区間距離や速度の計算に使用する
    private RoutePoint lastRoutePoint = null;

    //最後にGPSから位置情報を受信した時刻
    private long lastGpsLocationTime = 0;

    //記録に使用できる最大位置誤差。30mを超える位置は除外
    private static final float RECORDING_MAX_ACCURACY = 30.0f;
    //自転車として異常とみなす区間速度。これを超える場合はGPSの飛びとして除外
    private static final double MAX_REASONABLE_SPEED_KMH = 60.0;
    //停止中のGPSブレとみなす移動距離
    private static final double STOP_JITTER_DISTANCE = 8.0;
    //停止中のGPSブレ判定に使用するGPS速度。単位はm/s
    private static final float STOP_JITTER_SPEED = 0.8f;

    //新しい記録地点をMainActivityへ送るBroadcastのAction
    public static final String ACTION_LOCATION_UPDATE =
            "jp.ac.gifu_u.info.katsuya.prog.ACTION_LOCATION_UPDATE";

    //MainActivityが現在までの記録ルートを要求するときのAction
    public static final String ACTION_REQUEST_ROUTE =
            "jp.ac.gifu_u.info.katsuya.prog.ACTION_REQUEST_ROUTE";

    //ServiceからMainActivityへルート一覧を返すBroadcastのAction
    public static final String ACTION_ROUTE_SNAPSHOT =
            "jp.ac.gifu_u.info.katsuya.prog.ACTION_ROUTE_SNAPSHOT";

    public static final String EXTRA_ROUTE_JSON = "extra_route_json";

    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LON = "extra_lon";
    public static final String EXTRA_DISTANCE = "extra_distance";

    /**
     * Serviceが初めて作成されたときに1回だけ呼ばれる
     */
    @Override
    public void onCreate() {
        super.onCreate();

        //Androidの位置情報サービスを取得
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        //走行記録中の常駐通知に使用するチャンネルを作成
        createNotificationChannel();

        //位置情報が更新されたときの処理を定義
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                //受信した位置情報の検査、距離計算、保存を行う
                handleLocation(location);
            }
        };
    }

    /**
     * startServiceまたはstartForegroundServiceでServiceが呼ばれるたびに実行される
     * IntentのActionに応じて開始、停止、ルート送信を切り替える。
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //ServiceがAndroidによって再生成され、Intentが渡されない場合の対策
        if (intent == null || intent.getAction() == null) {
            //Serviceが強制終了された場合にAndroidへ再生成を依頼する
            return START_STICKY;
        }

        //Actionに応じて対応する処理を実行
        if (ACTION_START.equals(intent.getAction())) {
            startTracking();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopTracking();
        } else if (ACTION_REQUEST_ROUTE.equals(intent.getAction())) {
            sendRouteSnapshotToActivity();
        }

        return START_STICKY;
    }

    /**
     * 走行記録を開始する
     */
    private void startTracking() {
        //すでに記録中なら二重開始しない
        if (isRecording) {
            return;
        }

        //前回の走行データを初期化し、新しい記録を開始
        isRecording = true;
        routePoints.clear();
        totalDistance = 0.0;
        lastRoutePoint = null;
        startTime = System.currentTimeMillis();
        lastGpsLocationTime = 0;

        //バックグラウンドでも記録を継続するための常駐通知を作成
        Notification notification = createNotification(
                "走行記録中",
                "位置情報を記録しています"
        );

        //Android 10以降では位置情報を使うフォアグラウンドサービスとして開始
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        //GPSとネットワーク位置情報の取得を開始
        startLocationUpdates();

        Log.d("TRACKING_SERVICE", "記録開始");
    }

    /**
     * 走行記録を停止し、ルート保存と統計更新を行う
     */
    private void stopTracking() {
        //記録中でなければServiceだけを終了
        if (!isRecording) {
            stopSelf();
            return;
        }

        isRecording = false;
        endTime = System.currentTimeMillis();

        //位置情報更新を停止
        stopLocationUpdates();

        //記録したルートをJSONファイルとして保存
        boolean routeSaved = saveRouteToJson();

        if (routeSaved) {
            //ルート保存に成功した場合のみ統計データを更新
            boolean statisticsUpdated = updateStatistics();

            if (statisticsUpdated) {
                Log.d(
                        "STATISTICS",
                        "統計データの更新に成功しました"
                );
            } else {
                Log.d(
                        "STATISTICS",
                        "統計データの更新に失敗しました"
                );
            }
        }

        Log.d("TRACKING_SERVICE", "記録停止");

        //常駐通知を削除し、Serviceを終了
        stopForeground(true);
        stopSelf();
    }

    /**
     * GPSとネットワーク位置情報の更新要求を登録する
     */
    private void startLocationUpdates() {
        //位置情報権限があるか確認
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("TRACKING_SERVICE", "位置情報権限がありません");
            stopSelf();
            return;
        }

        try {
            //GPS位置情報を3秒または5m移動ごとに要求
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000,
                    5,
                    locationListener
            );

            //ネットワーク位置情報も3秒または5m移動ごとに要求
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    3000,
                    5,
                    locationListener
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 登録済みの位置情報更新を解除する
     */
    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    /**
     * 受信した位置情報を検査し、走行地点として保存する
     */
    private void handleLocation(Location location) {
        //位置情報を取得した提供元を確認
        String provider = location.getProvider();
        long nowTime = System.currentTimeMillis();

        //GPSを受信した時刻を保存
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            lastGpsLocationTime = nowTime;
        }

        //直近10秒以内にGPSを受信している場合は精度の低いNETWORKを使用しない
        if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
            if (nowTime - lastGpsLocationTime < 10000) {
                return;
            }

            if (location.hasAccuracy() && location.getAccuracy() > 100) {
                return;
            }
        }

        if (location.hasAccuracy() && location.getAccuracy() > 100) {
            return;
        }

        //記録用フィルターで異常な位置情報と判定された場合は無視
        if (shouldIgnoreLocationForRecording(location)) {
            return;
        }

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        long time = System.currentTimeMillis();
        float speed = location.hasSpeed() ? location.getSpeed() : 0.0f;

        //前回地点がある場合、前回地点から今回地点までの距離を加算
        if (lastRoutePoint != null) {
            float[] result = new float[1];

            Location.distanceBetween(
                    lastRoutePoint.lat,
                    lastRoutePoint.lon,
                    lat,
                    lon,
                    result
            );

            totalDistance += result[0];
        }

        //緯度、経度、時刻、GPS速度、累計距離を1つのRoutePointにまとめる
        RoutePoint routePoint = new RoutePoint(
                lat,
                lon,
                time,
                speed,
                totalDistance
        );

        routePoints.add(routePoint);
        lastRoutePoint = routePoint;

        //MainActivityへ新しい記録地点を送信
        sendLocationUpdateToActivity(lat, lon, totalDistance);

        Log.d("TRACKING_SERVICE",
                "記録点追加 provider=" + provider +
                        ", points=" + routePoints.size() +
                        ", distance=" + totalDistance);
    }

    /**
     * 位置情報を走行記録へ使用してよいか判定する
     *
     * true：異常または停止中のブレなので無視する
     * false：走行地点として記録する
     */
    private boolean shouldIgnoreLocationForRecording(Location location) {
        //位置誤差が30mを超える場合は精度不足として除外
        if (location.hasAccuracy() && location.getAccuracy() > RECORDING_MAX_ACCURACY) {
            Log.d("GPS_FILTER", "精度が悪いため無視: accuracy=" + location.getAccuracy());
            return true;
        }

        //最初の地点は比較対象がないため記録する
        if (lastRoutePoint == null) {
            return false;
        }

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        float[] result = new float[1];

        Location.distanceBetween(
                lastRoutePoint.lat,
                lastRoutePoint.lon,
                lat,
                lon,
                result
        );

        double distance = result[0];
        double diffTime = (System.currentTimeMillis() - lastRoutePoint.time) / 1000.0;

        if (diffTime <= 0) {
            return true;
        }

        //前回地点から今回地点までの区間速度をkm/hで計算
        double sectionSpeedKmh = (distance / diffTime) * 3.6;

        //60km/hを超える場合はGPS座標が飛んだものとして除外
        if (sectionSpeedKmh > MAX_REASONABLE_SPEED_KMH) {
            Log.d("GPS_FILTER", "ワープ判定で無視: speed="
                    + sectionSpeedKmh + " km/h, distance=" + distance);
            return true;
        }

        float gpsSpeed = location.hasSpeed() ? location.getSpeed() : 0.0f;

        //GPS速度が低く移動距離も小さい場合は停止中の位置ブレとして除外
        if (gpsSpeed < STOP_JITTER_SPEED && distance < STOP_JITTER_DISTANCE) {
            Log.d("GPS_FILTER", "停止中のブレとして無視: distance="
                    + distance + ", gpsSpeed=" + gpsSpeed);
            return true;
        }

        return false;
    }

    /**
     * 現在の走行ルートをroutesフォルダへJSON形式で保存する
     *
     * 保存成功：true
     * 保存失敗または記録点不足：false
     */
    private boolean saveRouteToJson() {
        try {
            if (routePoints.size() < 2) {
                Log.d("TRACKING_SERVICE", "記録点が少なすぎるため保存しません");
                return false;
            }

            //走行全体を保存するJSONオブジェクトを作成
            JSONObject routeJson = new JSONObject();

            routeJson.put("startTime", startTime);
            routeJson.put("endTime", endTime);
            routeJson.put("totalDistance", totalDistance);

            double elapsedSec = (endTime - startTime) / 1000.0;
            double averageSpeed = 0.0;

            if (elapsedSec > 0) {
                averageSpeed = totalDistance / elapsedSec;
            }

            routeJson.put("averageSpeed", averageSpeed);

            //各記録地点を保存するJSON配列を作成
            JSONArray pointsArray = new JSONArray();

            //すべてのRoutePointをJSONObjectへ変換して配列へ追加
            for (RoutePoint p : routePoints) {
                JSONObject pointJson = new JSONObject();

                pointJson.put("lat", p.lat);
                pointJson.put("lon", p.lon);
                pointJson.put("time", p.time);
                pointJson.put("speed", p.speed);
                pointJson.put("distance", p.distance);

                pointsArray.put(pointJson);
            }

            routeJson.put("points", pointsArray);

            //走行開始時刻を使って重複しにくいファイル名を作成
            String fileName = "route_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN)
                            .format(new Date(startTime)) +
                    ".json";

            //アプリ専用領域内のroutesフォルダを指定
            File routeDir = new File(getFilesDir(), "routes");

            if (!routeDir.exists()) {
                routeDir.mkdir();
            }

            File file = new File(routeDir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(routeJson.toString(4).getBytes());
            fos.close();

            Log.d("TRACKING_SERVICE", "保存成功: " + file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 走行記録中に表示する常駐通知を作成する
     */
    private Notification createNotification(String title, String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * Android 8以降で必要な通知チャンネルを作成する
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "走行記録",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription("走行記録中の通知");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * Service破棄時に位置情報更新を確実に解除する
     */
    @Override
    public void onDestroy() {
        stopLocationUpdates();
        super.onDestroy();
    }

    /**
     * このServiceはbindServiceではなくstartService方式で使用するためnullを返す
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 新しい位置情報をBroadcastでMainActivityへ送信する
     */
    private void sendLocationUpdateToActivity(double lat, double lon, double distance) {
        Intent intent = new Intent(ACTION_LOCATION_UPDATE);
        intent.setPackage(getPackageName());

        intent.putExtra(EXTRA_LAT, lat);
        intent.putExtra(EXTRA_LON, lon);
        intent.putExtra(EXTRA_DISTANCE, distance);

        sendBroadcast(intent);
    }

    /**
     * 現在までに記録したルート全体をJSON文字列にしてMainActivityへ送信する
     * 画面復帰時に記録中ルートを再表示するために使用する。
     */
    private void sendRouteSnapshotToActivity() {
        try {
            JSONArray pointsArray = new JSONArray();

            for (RoutePoint p : routePoints) {
                JSONObject pointJson = new JSONObject();

                pointJson.put("lat", p.lat);
                pointJson.put("lon", p.lon);
                pointJson.put("time", p.time);
                pointJson.put("speed", p.speed);
                pointJson.put("distance", p.distance);

                pointsArray.put(pointJson);
            }

            Intent intent = new Intent(ACTION_ROUTE_SNAPSHOT);
            intent.setPackage(getPackageName());
            intent.putExtra(EXTRA_ROUTE_JSON, pointsArray.toString());

            sendBroadcast(intent);

            Log.d("TRACKING_SERVICE", "ルート一覧を送信 points=" + routePoints.size());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //statistics.json(統計データ) のファイルを取得する関数
    private File getStatisticsFile() {
        return new File(getFilesDir(), "statistics.json");
    }

    //初期状態の統計JSONを作る関数
    /**
     * statistics.json全体の初期構造を作る
     */
    private JSONObject createDefaultStatisticsJson() throws Exception {
        JSONObject rootJson = new JSONObject();

        rootJson.put("version", 2);

        // 全期間の統計
        rootJson.put("allTime", createDefaultStatisticsBlock());

        // 期間別統計
        rootJson.put("yearly", new JSONObject());
        rootJson.put("monthly", new JSONObject());
        rootJson.put("daily", new JSONObject());

        return rootJson;
    }

    //statistics.json を読み込む関数
    private JSONObject loadStatisticsJson() {
        try {
            File file = getStatisticsFile();

            if (!file.exists()) {
                return createDefaultStatisticsJson();
            }

            FileInputStream fis = new FileInputStream(file);

            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String jsonText =
                    new String(data, StandardCharsets.UTF_8);

            JSONObject rootJson =
                    new JSONObject(jsonText);

            // 足りない項目を自動追加
            ensureStatisticsStructure(rootJson);

            return rootJson;

        } catch (Exception e) {
            e.printStackTrace();

            try {
                return createDefaultStatisticsJson();
            } catch (Exception ex) {
                ex.printStackTrace();
                return new JSONObject();
            }
        }
    }

    //統計JSONを書き込む関数
    private boolean saveStatisticsJson(JSONObject statisticsJson) {
        try {
            File file = getStatisticsFile();

            FileOutputStream fos = new FileOutputStream(file);

            fos.write(
                    statisticsJson
                            .toString(4)
                            .getBytes(StandardCharsets.UTF_8)
            );

            fos.close();

            Log.d(
                    "STATISTICS",
                    "統計データを保存しました: " + file.getAbsolutePath()
            );

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    //統計データの更新に必要な値を計算する関数
    /**
     * 現在の1回分の走行データから統計値を計算する
     */
    private RideStatistics calculateCurrentRideStatistics() {
        RideStatistics stats = new RideStatistics();

        // 全体走行時間
        stats.rideTimeSec = (endTime - startTime) / 1000;

        // 全体平均速度
        if (stats.rideTimeSec > 0) {
            stats.averageSpeed = totalDistance / stats.rideTimeSec;
        } else {
            stats.averageSpeed = 0.0;
        }

        double movingTime = 0.0;
        double stopTime = 0.0;

        int stopCount = 0;

        double currentStopTime = 0.0;
        double longestStopTime = 0.0;

        boolean wasStopping = false;

        double maxGpsSpeed = 0.0;

        double time0to5 = 0.0;
        double time5to10 = 0.0;
        double time10to15 = 0.0;
        double time15to20 = 0.0;
        double time20to25 = 0.0;
        double time25to30 = 0.0;
        double time30Over = 0.0;

        //隣り合う記録地点ごとに区間速度と経過時間を計算
        for (int i = 0; i < routePoints.size(); i++) {
            RoutePoint now = routePoints.get(i);

            // 最高GPS速度
            if (now.speed > maxGpsSpeed) {
                maxGpsSpeed = now.speed;
            }

            if (i == 0) {
                continue;
            }

            RoutePoint prev = routePoints.get(i - 1);

            double diffDistance =
                    now.distance - prev.distance;

            double diffTime =
                    (now.time - prev.time) / 1000.0;

            if (diffTime <= 0) {
                continue;
            }

            double sectionSpeed =
                    diffDistance / diffTime;

            double sectionSpeedKmh =
                    sectionSpeed * 3.6;

            //区間速度が2km/h未満なら停止中として集計
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

                // 速度分布
                if (sectionSpeedKmh < 5.0) {
                    time0to5 += diffTime;

                } else if (sectionSpeedKmh < 10.0) {
                    time5to10 += diffTime;

                } else if (sectionSpeedKmh < 15.0) {
                    time10to15 += diffTime;

                } else if (sectionSpeedKmh < 20.0) {
                    time15to20 += diffTime;

                } else if (sectionSpeedKmh < 25.0) {
                    time20to25 += diffTime;

                } else if (sectionSpeedKmh < 30.0) {
                    time25to30 += diffTime;

                } else {
                    time30Over += diffTime;
                }
            }
        }

        stats.movingTimeSec = movingTime;
        stats.stopTimeSec = stopTime;

        stats.stopCount = stopCount;
        stats.longestStopTime = longestStopTime;

        if (movingTime > 0) {
            stats.movingAverageSpeed =
                    totalDistance / movingTime;
        } else {
            stats.movingAverageSpeed = 0.0;
        }

        stats.maxGpsSpeed = maxGpsSpeed;

        stats.time0to5 = time0to5;
        stats.time5to10 = time5to10;
        stats.time10to15 = time10to15;
        stats.time15to20 = time15to20;
        stats.time20to25 = time20to25;
        stats.time25to30 = time25to30;
        stats.time30Over = time30Over;

        //計算した統計値をまとめて返す
        return stats;
    }

    //統計更新用の関数
    /**
     * 全体、年別、月別、日別の統計を更新する
     */
    private boolean updateStatistics() {
        try {
            JSONObject rootJson = loadStatisticsJson();

            //今回の走行から統計値を計算
            RideStatistics rideStats =
                    calculateCurrentRideStatistics();

            //走行開始日を基準に年、月、日の保存先キーを作成
            Date rideDate = new Date(startTime);

            String yearKey =
                    new SimpleDateFormat(
                            "yyyy",
                            Locale.JAPAN
                    ).format(rideDate);

            String monthKey =
                    new SimpleDateFormat(
                            "yyyy-MM",
                            Locale.JAPAN
                    ).format(rideDate);

            String dayKey =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.JAPAN
                    ).format(rideDate);

            // =========================
            // 各統計領域の取得
            // =========================

            JSONObject allTimeJson =
                    rootJson.optJSONObject("allTime");

            if (allTimeJson == null) {
                allTimeJson = createDefaultStatisticsBlock();
                rootJson.put("allTime", allTimeJson);
            }

            JSONObject yearlyJson =
                    rootJson.optJSONObject("yearly");

            if (yearlyJson == null) {
                yearlyJson = new JSONObject();
                rootJson.put("yearly", yearlyJson);
            }

            JSONObject monthlyJson =
                    rootJson.optJSONObject("monthly");

            if (monthlyJson == null) {
                monthlyJson = new JSONObject();
                rootJson.put("monthly", monthlyJson);
            }

            JSONObject dailyJson =
                    rootJson.optJSONObject("daily");

            if (dailyJson == null) {
                dailyJson = new JSONObject();
                rootJson.put("daily", dailyJson);
            }

            JSONObject yearBlock =
                    getOrCreatePeriodBlock(
                            yearlyJson,
                            yearKey
                    );

            JSONObject monthBlock =
                    getOrCreatePeriodBlock(
                            monthlyJson,
                            monthKey
                    );

            JSONObject dayBlock =
                    getOrCreatePeriodBlock(
                            dailyJson,
                            dayKey
                    );

            // =========================
            //今回の走行を全期間、年別、月別、日別の4か所へ加算
            // =========================

            updateStatisticsBlock(
                    allTimeJson,
                    rideStats
            );

            updateStatisticsBlock(
                    yearBlock,
                    rideStats
            );

            updateStatisticsBlock(
                    monthBlock,
                    rideStats
            );

            updateStatisticsBlock(
                    dayBlock,
                    rideStats
            );

            rootJson.put("version", 2);

            boolean saved =
                    saveStatisticsJson(rootJson);

            if (saved) {
                Log.d(
                        "STATISTICS",
                        "統計更新完了"
                                + " year=" + yearKey
                                + ", month=" + monthKey
                                + ", day=" + dayKey
                );
            }

            return saved;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    //JSONファイル補完関数
    /**
     * statistics.json全体の不足構造を補完する
     */
    private void ensureStatisticsStructure(
            JSONObject rootJson
    ) throws Exception {

        int version =
                rootJson.optInt("version", 1);

        /*
         * 旧形式のstatistics.jsonを新形式へ移行
         *
         * 旧形式：
         * root直下にsummary、recordsなどがある
         *
         * 新形式：
         * allTime内にsummary、recordsなどがある
         */
        if (version < 2
                && rootJson.optJSONObject("allTime") == null) {

            JSONObject oldSummary =
                    rootJson.optJSONObject("summary");

            JSONObject oldRecords =
                    rootJson.optJSONObject("records");

            JSONObject oldSpeed =
                    rootJson.optJSONObject("speedDistribution");

            JSONObject oldDistance =
                    rootJson.optJSONObject("distanceDistribution");

            JSONObject allTimeJson =
                    createDefaultStatisticsBlock();

            if (oldSummary != null) {
                allTimeJson.put(
                        "summary",
                        oldSummary
                );
            }

            if (oldRecords != null) {
                allTimeJson.put(
                        "records",
                        oldRecords
                );
            }

            if (oldSpeed != null) {
                allTimeJson.put(
                        "speedDistribution",
                        oldSpeed
                );
            }

            if (oldDistance != null) {
                allTimeJson.put(
                        "distanceDistribution",
                        oldDistance
                );
            }

            rootJson.put(
                    "allTime",
                    allTimeJson
            );

            // 旧形式の項目を削除
            rootJson.remove("summary");
            rootJson.remove("records");
            rootJson.remove("speedDistribution");
            rootJson.remove("distanceDistribution");

            Log.d(
                    "STATISTICS",
                    "旧統計データをversion 2形式へ変換しました"
            );
        }

        JSONObject allTimeJson =
                rootJson.optJSONObject("allTime");

        if (allTimeJson == null) {
            allTimeJson =
                    createDefaultStatisticsBlock();

            rootJson.put(
                    "allTime",
                    allTimeJson
            );
        }

        ensureStatisticsBlockStructure(
                allTimeJson
        );

        JSONObject yearlyJson =
                rootJson.optJSONObject("yearly");

        if (yearlyJson == null) {
            yearlyJson = new JSONObject();
            rootJson.put("yearly", yearlyJson);
        }

        JSONObject monthlyJson =
                rootJson.optJSONObject("monthly");

        if (monthlyJson == null) {
            monthlyJson = new JSONObject();
            rootJson.put("monthly", monthlyJson);
        }

        JSONObject dailyJson =
                rootJson.optJSONObject("daily");

        if (dailyJson == null) {
            dailyJson = new JSONObject();
            rootJson.put("daily", dailyJson);
        }

        // 既存の年別統計を補完
        ensurePeriodCollectionStructure(yearlyJson);

        // 既存の月別統計を補完
        ensurePeriodCollectionStructure(monthlyJson);

        // 既存の日別統計を補完
        ensurePeriodCollectionStructure(dailyJson);

        rootJson.put("version", 2);
    }

    /**
     * yearly、monthly、daily内の各統計ブロックを補完する
     */
    private void ensurePeriodCollectionStructure(
            JSONObject collectionJson
    ) throws Exception {

        java.util.Iterator<String> keys =
                collectionJson.keys();

        while (keys.hasNext()) {
            String key = keys.next();

            JSONObject blockJson =
                    collectionJson.optJSONObject(key);

            if (blockJson != null) {
                ensureStatisticsBlockStructure(
                        blockJson
                );
            }
        }
    }

    /**
     * 全体・年別・月別・日別で共通して使う統計ブロックを作る
     */
    private JSONObject createDefaultStatisticsBlock() throws Exception {
        JSONObject blockJson = new JSONObject();

        // 累計情報
        JSONObject summaryJson = new JSONObject();
        summaryJson.put("totalRideCount", 0);
        summaryJson.put("totalDistance", 0.0);
        summaryJson.put("totalRideTime", 0);
        summaryJson.put("totalMovingTime", 0.0);
        summaryJson.put("totalStopTime", 0.0);
        summaryJson.put("totalStopCount", 0);

        // 最高記録
        JSONObject recordsJson = new JSONObject();
        recordsJson.put("maxSingleRideDistance", 0.0);
        recordsJson.put("maxSingleRideTime", 0);
        recordsJson.put("maxAverageSpeed", 0.0);
        recordsJson.put("maxMovingAverageSpeed", 0.0);
        recordsJson.put("maxGpsSpeed", 0.0);
        recordsJson.put("longestStopTime", 0.0);

        // 速度分布
        JSONObject speedDistributionJson = new JSONObject();
        speedDistributionJson.put("time0to5", 0.0);
        speedDistributionJson.put("time5to10", 0.0);
        speedDistributionJson.put("time10to15", 0.0);
        speedDistributionJson.put("time15to20", 0.0);
        speedDistributionJson.put("time20to25", 0.0);
        speedDistributionJson.put("time25to30", 0.0);
        speedDistributionJson.put("time30Over", 0.0);

        // 1回の走行距離分布
        JSONObject distanceDistributionJson = new JSONObject();
        distanceDistributionJson.put("ride0to5km", 0);
        distanceDistributionJson.put("ride5to10km", 0);
        distanceDistributionJson.put("ride10to20km", 0);
        distanceDistributionJson.put("ride20to50km", 0);
        distanceDistributionJson.put("ride50kmOver", 0);

        blockJson.put("summary", summaryJson);
        blockJson.put("records", recordsJson);
        blockJson.put("speedDistribution", speedDistributionJson);
        blockJson.put("distanceDistribution", distanceDistributionJson);

        return blockJson;
    }

    /**
     * yearly、monthly、dailyから対象期間の統計ブロックを取得する。
     * 存在しない場合は新しく作成する。
     */
    private JSONObject getOrCreatePeriodBlock(
            JSONObject periodRoot,
            String periodKey
    ) throws Exception {

        JSONObject blockJson =
                periodRoot.optJSONObject(periodKey);

        if (blockJson == null) {
            blockJson = createDefaultStatisticsBlock();
            periodRoot.put(periodKey, blockJson);
        }

        ensureStatisticsBlockStructure(blockJson);

        return blockJson;
    }

    /**
     * 1つの統計ブロックに今回の走行データを加算する
     */
    private void updateStatisticsBlock(
            JSONObject blockJson,
            RideStatistics rideStats
    ) throws Exception {

        ensureStatisticsBlockStructure(blockJson);

        JSONObject summaryJson =
                blockJson.getJSONObject("summary");

        JSONObject recordsJson =
                blockJson.getJSONObject("records");

        JSONObject speedDistributionJson =
                blockJson.getJSONObject("speedDistribution");

        JSONObject distanceDistributionJson =
                blockJson.getJSONObject("distanceDistribution");

        // =========================
        // 累計統計
        // =========================

        summaryJson.put(
                "totalRideCount",
                summaryJson.optInt("totalRideCount", 0) + 1
        );

        summaryJson.put(
                "totalDistance",
                summaryJson.optDouble("totalDistance", 0.0)
                        + totalDistance
        );

        summaryJson.put(
                "totalRideTime",
                summaryJson.optLong("totalRideTime", 0)
                        + rideStats.rideTimeSec
        );

        summaryJson.put(
                "totalMovingTime",
                summaryJson.optDouble("totalMovingTime", 0.0)
                        + rideStats.movingTimeSec
        );

        summaryJson.put(
                "totalStopTime",
                summaryJson.optDouble("totalStopTime", 0.0)
                        + rideStats.stopTimeSec
        );

        summaryJson.put(
                "totalStopCount",
                summaryJson.optInt("totalStopCount", 0)
                        + rideStats.stopCount
        );

        // =========================
        // 最高記録
        // =========================

        recordsJson.put(
                "maxSingleRideDistance",
                Math.max(
                        recordsJson.optDouble(
                                "maxSingleRideDistance",
                                0.0
                        ),
                        totalDistance
                )
        );

        recordsJson.put(
                "maxSingleRideTime",
                Math.max(
                        recordsJson.optLong(
                                "maxSingleRideTime",
                                0
                        ),
                        rideStats.rideTimeSec
                )
        );

        recordsJson.put(
                "maxAverageSpeed",
                Math.max(
                        recordsJson.optDouble(
                                "maxAverageSpeed",
                                0.0
                        ),
                        rideStats.averageSpeed
                )
        );

        recordsJson.put(
                "maxMovingAverageSpeed",
                Math.max(
                        recordsJson.optDouble(
                                "maxMovingAverageSpeed",
                                0.0
                        ),
                        rideStats.movingAverageSpeed
                )
        );

        recordsJson.put(
                "maxGpsSpeed",
                Math.max(
                        recordsJson.optDouble(
                                "maxGpsSpeed",
                                0.0
                        ),
                        rideStats.maxGpsSpeed
                )
        );

        recordsJson.put(
                "longestStopTime",
                Math.max(
                        recordsJson.optDouble(
                                "longestStopTime",
                                0.0
                        ),
                        rideStats.longestStopTime
                )
        );

        // =========================
        // 速度分布
        // =========================

        speedDistributionJson.put(
                "time0to5",
                speedDistributionJson.optDouble("time0to5", 0.0)
                        + rideStats.time0to5
        );

        speedDistributionJson.put(
                "time5to10",
                speedDistributionJson.optDouble("time5to10", 0.0)
                        + rideStats.time5to10
        );

        speedDistributionJson.put(
                "time10to15",
                speedDistributionJson.optDouble("time10to15", 0.0)
                        + rideStats.time10to15
        );

        speedDistributionJson.put(
                "time15to20",
                speedDistributionJson.optDouble("time15to20", 0.0)
                        + rideStats.time15to20
        );

        speedDistributionJson.put(
                "time20to25",
                speedDistributionJson.optDouble("time20to25", 0.0)
                        + rideStats.time20to25
        );

        speedDistributionJson.put(
                "time25to30",
                speedDistributionJson.optDouble("time25to30", 0.0)
                        + rideStats.time25to30
        );

        speedDistributionJson.put(
                "time30Over",
                speedDistributionJson.optDouble("time30Over", 0.0)
                        + rideStats.time30Over
        );

        // =========================
        // 走行距離分布
        // =========================

        double rideDistanceKm = totalDistance / 1000.0;

        if (rideDistanceKm < 5.0) {
            distanceDistributionJson.put(
                    "ride0to5km",
                    distanceDistributionJson.optInt(
                            "ride0to5km",
                            0
                    ) + 1
            );

        } else if (rideDistanceKm < 10.0) {
            distanceDistributionJson.put(
                    "ride5to10km",
                    distanceDistributionJson.optInt(
                            "ride5to10km",
                            0
                    ) + 1
            );

        } else if (rideDistanceKm < 20.0) {
            distanceDistributionJson.put(
                    "ride10to20km",
                    distanceDistributionJson.optInt(
                            "ride10to20km",
                            0
                    ) + 1
            );

        } else if (rideDistanceKm < 50.0) {
            distanceDistributionJson.put(
                    "ride20to50km",
                    distanceDistributionJson.optInt(
                            "ride20to50km",
                            0
                    ) + 1
            );

        } else {
            distanceDistributionJson.put(
                    "ride50kmOver",
                    distanceDistributionJson.optInt(
                            "ride50kmOver",
                            0
                    ) + 1
            );
        }
    }

    /**
     * 1つの統計ブロック内の不足項目を補完する
     */
    private void ensureStatisticsBlockStructure(
            JSONObject blockJson
    ) throws Exception {

        JSONObject defaultBlock =
                createDefaultStatisticsBlock();

        JSONObject summaryJson =
                blockJson.optJSONObject("summary");

        if (summaryJson == null) {
            summaryJson = new JSONObject();
            blockJson.put("summary", summaryJson);
        }

        JSONObject defaultSummary =
                defaultBlock.getJSONObject("summary");

        copyMissingValues(
                summaryJson,
                defaultSummary
        );

        JSONObject recordsJson =
                blockJson.optJSONObject("records");

        if (recordsJson == null) {
            recordsJson = new JSONObject();
            blockJson.put("records", recordsJson);
        }

        JSONObject defaultRecords =
                defaultBlock.getJSONObject("records");

        copyMissingValues(
                recordsJson,
                defaultRecords
        );

        JSONObject speedJson =
                blockJson.optJSONObject("speedDistribution");

        if (speedJson == null) {
            speedJson = new JSONObject();
            blockJson.put("speedDistribution", speedJson);
        }

        JSONObject defaultSpeed =
                defaultBlock.getJSONObject("speedDistribution");

        copyMissingValues(
                speedJson,
                defaultSpeed
        );

        JSONObject distanceJson =
                blockJson.optJSONObject("distanceDistribution");

        if (distanceJson == null) {
            distanceJson = new JSONObject();
            blockJson.put("distanceDistribution", distanceJson);
        }

        JSONObject defaultDistance =
                defaultBlock.getJSONObject("distanceDistribution");

        copyMissingValues(
                distanceJson,
                defaultDistance
        );
    }

    /**
     * targetに存在しないキーをdefaultJsonからコピーする
     */
    private void copyMissingValues(
            JSONObject target,
            JSONObject defaultJson
    ) throws Exception {

        java.util.Iterator<String> keys =
                defaultJson.keys();

        while (keys.hasNext()) {
            String key = keys.next();

            if (!target.has(key)) {
                target.put(
                        key,
                        defaultJson.get(key)
                );
            }
        }
    }
}