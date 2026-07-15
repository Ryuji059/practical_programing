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

public class LocationTrackingService extends Service {
    //統計データ計算用の内部クラス
    private static class RideStatistics {
        long rideTimeSec;
        double movingTimeSec;
        double stopTimeSec;
        double averageSpeed;
        double maxGpsSpeed;
    }

    public static final String ACTION_START = "jp.ac.gifu_u.info.katsuya.prog.ACTION_START_TRACKING";
    public static final String ACTION_STOP = "jp.ac.gifu_u.info.katsuya.prog.ACTION_STOP_TRACKING";

    private static final String CHANNEL_ID = "tracking_channel";
    private static final int NOTIFICATION_ID = 1001;

    private LocationManager locationManager;
    private LocationListener locationListener;

    private ArrayList<RoutePoint> routePoints = new ArrayList<>();

    private boolean isRecording = false;
    private long startTime = 0;
    private long endTime = 0;
    private double totalDistance = 0.0;
    private RoutePoint lastRoutePoint = null;

    private long lastGpsLocationTime = 0;

    private static final float RECORDING_MAX_ACCURACY = 30.0f;
    private static final double MAX_REASONABLE_SPEED_KMH = 60.0;
    private static final double STOP_JITTER_DISTANCE = 8.0;
    private static final float STOP_JITTER_SPEED = 0.8f;

    public static final String ACTION_LOCATION_UPDATE =
            "jp.ac.gifu_u.info.katsuya.prog.ACTION_LOCATION_UPDATE";

    public static final String ACTION_REQUEST_ROUTE =
            "jp.ac.gifu_u.info.katsuya.prog.ACTION_REQUEST_ROUTE";

    public static final String ACTION_ROUTE_SNAPSHOT =
            "jp.ac.gifu_u.info.katsuya.prog.ACTION_ROUTE_SNAPSHOT";

    public static final String EXTRA_ROUTE_JSON = "extra_route_json";

    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LON = "extra_lon";
    public static final String EXTRA_DISTANCE = "extra_distance";

    @Override
    public void onCreate() {
        super.onCreate();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        createNotificationChannel();

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                handleLocation(location);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        if (ACTION_START.equals(intent.getAction())) {
            startTracking();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopTracking();
        } else if (ACTION_REQUEST_ROUTE.equals(intent.getAction())) {
            sendRouteSnapshotToActivity();
        }

        return START_STICKY;
    }

    private void startTracking() {
        if (isRecording) {
            return;
        }

        isRecording = true;
        routePoints.clear();
        totalDistance = 0.0;
        lastRoutePoint = null;
        startTime = System.currentTimeMillis();
        lastGpsLocationTime = 0;

        Notification notification = createNotification(
                "走行記録中",
                "位置情報を記録しています"
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        startLocationUpdates();

        Log.d("TRACKING_SERVICE", "記録開始");
    }

    private void stopTracking() {
        if (!isRecording) {
            stopSelf();
            return;
        }

        isRecording = false;
        endTime = System.currentTimeMillis();

        stopLocationUpdates();

        boolean routeSaved = saveRouteToJson();

        if (routeSaved) {
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

        stopForeground(true);
        stopSelf();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("TRACKING_SERVICE", "位置情報権限がありません");
            stopSelf();
            return;
        }

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000,
                    5,
                    locationListener
            );

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

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    private void handleLocation(Location location) {
        String provider = location.getProvider();
        long nowTime = System.currentTimeMillis();

        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            lastGpsLocationTime = nowTime;
        }

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

        if (shouldIgnoreLocationForRecording(location)) {
            return;
        }

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        long time = System.currentTimeMillis();
        float speed = location.hasSpeed() ? location.getSpeed() : 0.0f;

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

        RoutePoint routePoint = new RoutePoint(
                lat,
                lon,
                time,
                speed,
                totalDistance
        );

        routePoints.add(routePoint);
        lastRoutePoint = routePoint;

        sendLocationUpdateToActivity(lat, lon, totalDistance);

        Log.d("TRACKING_SERVICE",
                "記録点追加 provider=" + provider +
                        ", points=" + routePoints.size() +
                        ", distance=" + totalDistance);
    }

    private boolean shouldIgnoreLocationForRecording(Location location) {
        if (location.hasAccuracy() && location.getAccuracy() > RECORDING_MAX_ACCURACY) {
            Log.d("GPS_FILTER", "精度が悪いため無視: accuracy=" + location.getAccuracy());
            return true;
        }

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

        double sectionSpeedKmh = (distance / diffTime) * 3.6;

        if (sectionSpeedKmh > MAX_REASONABLE_SPEED_KMH) {
            Log.d("GPS_FILTER", "ワープ判定で無視: speed="
                    + sectionSpeedKmh + " km/h, distance=" + distance);
            return true;
        }

        float gpsSpeed = location.hasSpeed() ? location.getSpeed() : 0.0f;

        if (gpsSpeed < STOP_JITTER_SPEED && distance < STOP_JITTER_DISTANCE) {
            Log.d("GPS_FILTER", "停止中のブレとして無視: distance="
                    + distance + ", gpsSpeed=" + gpsSpeed);
            return true;
        }

        return false;
    }

    private boolean saveRouteToJson() {
        try {
            if (routePoints.size() < 2) {
                Log.d("TRACKING_SERVICE", "記録点が少なすぎるため保存しません");
                return false;
            }

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

            routeJson.put("points", pointsArray);

            String fileName = "route_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN)
                            .format(new Date(startTime)) +
                    ".json";

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

    private Notification createNotification(String title, String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

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

    @Override
    public void onDestroy() {
        stopLocationUpdates();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendLocationUpdateToActivity(double lat, double lon, double distance) {
        Intent intent = new Intent(ACTION_LOCATION_UPDATE);
        intent.setPackage(getPackageName());

        intent.putExtra(EXTRA_LAT, lat);
        intent.putExtra(EXTRA_LON, lon);
        intent.putExtra(EXTRA_DISTANCE, distance);

        sendBroadcast(intent);
    }

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
    private JSONObject createDefaultStatisticsJson() throws Exception {
        JSONObject rootJson = new JSONObject();

        rootJson.put("version", 1);

        JSONObject summaryJson = new JSONObject();

        summaryJson.put("totalRideCount", 0);
        summaryJson.put("totalDistance", 0.0);
        summaryJson.put("totalRideTime", 0);
        summaryJson.put("totalMovingTime", 0.0);
        summaryJson.put("totalStopTime", 0.0);

        JSONObject recordsJson = new JSONObject();

        recordsJson.put("maxSingleRideDistance", 0.0);
        recordsJson.put("maxSingleRideTime", 0);
        recordsJson.put("maxAverageSpeed", 0.0);
        recordsJson.put("maxGpsSpeed", 0.0);

        rootJson.put("summary", summaryJson);
        rootJson.put("records", recordsJson);

        return rootJson;
    }

    //statistics.json を読み込む関数
    private JSONObject loadStatisticsJson() {
        try {
            File file = getStatisticsFile();

            // まだ統計ファイルがない場合
            if (!file.exists()) {
                return createDefaultStatisticsJson();
            }

            FileInputStream fis = new FileInputStream(file);

            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String jsonText = new String(
                    data,
                    StandardCharsets.UTF_8
            );

            return new JSONObject(jsonText);

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
        double maxGpsSpeed = 0.0;

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

            // 2km/h未満を停止扱い
            if (sectionSpeedKmh < 2.0) {
                stopTime += diffTime;
            } else {
                movingTime += diffTime;
            }
        }

        stats.movingTimeSec = movingTime;
        stats.stopTimeSec = stopTime;
        stats.maxGpsSpeed = maxGpsSpeed;

        return stats;
    }

    //統計更新用の関数
    private boolean updateStatistics() {
        try {
            JSONObject rootJson = loadStatisticsJson();

            JSONObject summaryJson =
                    rootJson.optJSONObject("summary");

            JSONObject recordsJson =
                    rootJson.optJSONObject("records");

            // 古いファイルなどで存在しなかった場合に備える
            if (summaryJson == null) {
                summaryJson = new JSONObject();
                rootJson.put("summary", summaryJson);
            }

            if (recordsJson == null) {
                recordsJson = new JSONObject();
                rootJson.put("records", recordsJson);
            }

            RideStatistics rideStats =
                    calculateCurrentRideStatistics();

            // =========================
            // 累計統計
            // =========================

            int totalRideCount =
                    summaryJson.optInt("totalRideCount", 0);

            double allDistance =
                    summaryJson.optDouble("totalDistance", 0.0);

            long allRideTime =
                    summaryJson.optLong("totalRideTime", 0);

            double allMovingTime =
                    summaryJson.optDouble("totalMovingTime", 0.0);

            double allStopTime =
                    summaryJson.optDouble("totalStopTime", 0.0);

            totalRideCount += 1;
            allDistance += totalDistance;
            allRideTime += rideStats.rideTimeSec;
            allMovingTime += rideStats.movingTimeSec;
            allStopTime += rideStats.stopTimeSec;

            summaryJson.put(
                    "totalRideCount",
                    totalRideCount
            );

            summaryJson.put(
                    "totalDistance",
                    allDistance
            );

            summaryJson.put(
                    "totalRideTime",
                    allRideTime
            );

            summaryJson.put(
                    "totalMovingTime",
                    allMovingTime
            );

            summaryJson.put(
                    "totalStopTime",
                    allStopTime
            );

            // =========================
            // 最高記録
            // =========================

            double maxSingleRideDistance =
                    recordsJson.optDouble(
                            "maxSingleRideDistance",
                            0.0
                    );

            long maxSingleRideTime =
                    recordsJson.optLong(
                            "maxSingleRideTime",
                            0
                    );

            double maxAverageSpeed =
                    recordsJson.optDouble(
                            "maxAverageSpeed",
                            0.0
                    );

            double maxGpsSpeed =
                    recordsJson.optDouble(
                            "maxGpsSpeed",
                            0.0
                    );

            if (totalDistance > maxSingleRideDistance) {
                maxSingleRideDistance = totalDistance;
            }

            if (rideStats.rideTimeSec > maxSingleRideTime) {
                maxSingleRideTime = rideStats.rideTimeSec;
            }

            if (rideStats.averageSpeed > maxAverageSpeed) {
                maxAverageSpeed = rideStats.averageSpeed;
            }

            if (rideStats.maxGpsSpeed > maxGpsSpeed) {
                maxGpsSpeed = rideStats.maxGpsSpeed;
            }

            recordsJson.put(
                    "maxSingleRideDistance",
                    maxSingleRideDistance
            );

            recordsJson.put(
                    "maxSingleRideTime",
                    maxSingleRideTime
            );

            recordsJson.put(
                    "maxAverageSpeed",
                    maxAverageSpeed
            );

            recordsJson.put(
                    "maxGpsSpeed",
                    maxGpsSpeed
            );

            rootJson.put("version", 1);

            return saveStatisticsJson(rootJson);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}