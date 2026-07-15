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

        // バージョン
        rootJson.put("version", 1);

        // =========================
        // 累計情報
        // =========================
        JSONObject summaryJson = new JSONObject();

        summaryJson.put("totalRideCount", 0);
        summaryJson.put("totalDistance", 0.0);
        summaryJson.put("totalRideTime", 0);
        summaryJson.put("totalMovingTime", 0.0);
        summaryJson.put("totalStopTime", 0.0);
        summaryJson.put("totalStopCount", 0);

        rootJson.put("summary", summaryJson);

        // =========================
        // 最高記録
        // =========================
        JSONObject recordsJson = new JSONObject();

        recordsJson.put("maxSingleRideDistance", 0.0);
        recordsJson.put("maxSingleRideTime", 0);
        recordsJson.put("maxAverageSpeed", 0.0);
        recordsJson.put("maxMovingAverageSpeed", 0.0);
        recordsJson.put("maxGpsSpeed", 0.0);
        recordsJson.put("longestStopTime", 0.0);

        rootJson.put("records", recordsJson);

        // =========================
        // 速度分布
        // =========================
        JSONObject speedDistributionJson = new JSONObject();

        speedDistributionJson.put("time0to5", 0.0);
        speedDistributionJson.put("time5to10", 0.0);
        speedDistributionJson.put("time10to15", 0.0);
        speedDistributionJson.put("time15to20", 0.0);
        speedDistributionJson.put("time20to25", 0.0);
        speedDistributionJson.put("time25to30", 0.0);
        speedDistributionJson.put("time30Over", 0.0);

        rootJson.put("speedDistribution", speedDistributionJson);

        // =========================
        // 距離分布
        // =========================
        JSONObject distanceDistributionJson = new JSONObject();

        distanceDistributionJson.put("ride0to5km", 0);
        distanceDistributionJson.put("ride5to10km", 0);
        distanceDistributionJson.put("ride10to20km", 0);
        distanceDistributionJson.put("ride20to50km", 0);
        distanceDistributionJson.put("ride50kmOver", 0);

        rootJson.put("distanceDistribution", distanceDistributionJson);

        // =========================
        // 月別統計
        // =========================
        JSONObject monthlyJson = new JSONObject();

        rootJson.put("monthly", monthlyJson);

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

            JSONObject speedDistributionJson =
                    rootJson.optJSONObject("speedDistribution");

            JSONObject distanceDistributionJson =
                    rootJson.optJSONObject("distanceDistribution");

            JSONObject monthlyJson =
                    rootJson.optJSONObject("monthly");

            // 古いファイルなどで存在しなかった場合に備える
            if (summaryJson == null) {
                summaryJson = new JSONObject();
                rootJson.put("summary", summaryJson);
            }

            if (recordsJson == null) {
                recordsJson = new JSONObject();
                rootJson.put("records", recordsJson);
            }

            if (speedDistributionJson == null) {
                speedDistributionJson = new JSONObject();
                rootJson.put("speedDistribution", speedDistributionJson);
            }

            if (distanceDistributionJson == null) {
                distanceDistributionJson = new JSONObject();
                rootJson.put("distanceDistribution", distanceDistributionJson);
            }

            if (monthlyJson == null) {
                monthlyJson = new JSONObject();
                rootJson.put("monthly", monthlyJson);
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

            int totalStopCount =
                    summaryJson.optInt("totalStopCount", 0);

            totalStopCount += rideStats.stopCount;

            summaryJson.put(
                    "totalStopCount",
                    totalStopCount
            );

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

            double maxMovingAverageSpeed =
                    recordsJson.optDouble(
                            "maxMovingAverageSpeed",
                            0.0
                    );

            double longestStopTime =
                    recordsJson.optDouble(
                            "longestStopTime",
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

            if (rideStats.movingAverageSpeed > maxMovingAverageSpeed) {
                maxMovingAverageSpeed =
                        rideStats.movingAverageSpeed;
            }

            if (rideStats.longestStopTime > longestStopTime) {
                longestStopTime =
                        rideStats.longestStopTime;
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

            recordsJson.put(
                    "maxMovingAverageSpeed",
                    maxMovingAverageSpeed
            );

            recordsJson.put(
                    "longestStopTime",
                    longestStopTime
            );

            double totalTime0to5 =
                    speedDistributionJson.optDouble(
                            "time0to5",
                            0.0
                    );

            double totalTime5to10 =
                    speedDistributionJson.optDouble(
                            "time5to10",
                            0.0
                    );

            double totalTime10to15 =
                    speedDistributionJson.optDouble(
                            "time10to15",
                            0.0
                    );

            double totalTime15to20 =
                    speedDistributionJson.optDouble(
                            "time15to20",
                            0.0
                    );

            double totalTime20to25 =
                    speedDistributionJson.optDouble(
                            "time20to25",
                            0.0
                    );

            double totalTime25to30 =
                    speedDistributionJson.optDouble(
                            "time25to30",
                            0.0
                    );

            double totalTime30Over =
                    speedDistributionJson.optDouble(
                            "time30Over",
                            0.0
                    );

            totalTime0to5 += rideStats.time0to5;
            totalTime5to10 += rideStats.time5to10;
            totalTime10to15 += rideStats.time10to15;
            totalTime15to20 += rideStats.time15to20;
            totalTime20to25 += rideStats.time20to25;
            totalTime25to30 += rideStats.time25to30;
            totalTime30Over += rideStats.time30Over;

            speedDistributionJson.put(
                    "time0to5",
                    totalTime0to5
            );

            speedDistributionJson.put(
                    "time5to10",
                    totalTime5to10
            );

            speedDistributionJson.put(
                    "time10to15",
                    totalTime10to15
            );

            speedDistributionJson.put(
                    "time15to20",
                    totalTime15to20
            );

            speedDistributionJson.put(
                    "time20to25",
                    totalTime20to25
            );

            speedDistributionJson.put(
                    "time25to30",
                    totalTime25to30
            );

            speedDistributionJson.put(
                    "time30Over",
                    totalTime30Over
            );

            double rideDistanceKm =
                    totalDistance / 1000.0;

            if (rideDistanceKm < 5.0) {

                int count =
                        distanceDistributionJson.optInt(
                                "ride0to5km",
                                0
                        );

                distanceDistributionJson.put(
                        "ride0to5km",
                        count + 1
                );

            } else if (rideDistanceKm < 10.0) {

                int count =
                        distanceDistributionJson.optInt(
                                "ride5to10km",
                                0
                        );

                distanceDistributionJson.put(
                        "ride5to10km",
                        count + 1
                );

            } else if (rideDistanceKm < 20.0) {

                int count =
                        distanceDistributionJson.optInt(
                                "ride10to20km",
                                0
                        );

                distanceDistributionJson.put(
                        "ride10to20km",
                        count + 1
                );

            } else if (rideDistanceKm < 50.0) {

                int count =
                        distanceDistributionJson.optInt(
                                "ride20to50km",
                                0
                        );

                distanceDistributionJson.put(
                        "ride20to50km",
                        count + 1
                );

            } else {

                int count =
                        distanceDistributionJson.optInt(
                                "ride50kmOver",
                                0
                        );

                distanceDistributionJson.put(
                        "ride50kmOver",
                        count + 1
                );
            }


            rootJson.put("version", 1);

            return saveStatisticsJson(rootJson);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    //JSSOファイル補間関数
    private void ensureStatisticsStructure(JSONObject rootJson) throws Exception {
        rootJson.put("version", rootJson.optInt("version", 1));

        // summary
        JSONObject summaryJson = rootJson.optJSONObject("summary");

        if (summaryJson == null) {
            summaryJson = new JSONObject();
            rootJson.put("summary", summaryJson);
        }

        if (!summaryJson.has("totalRideCount")) {
            summaryJson.put("totalRideCount", 0);
        }

        if (!summaryJson.has("totalDistance")) {
            summaryJson.put("totalDistance", 0.0);
        }

        if (!summaryJson.has("totalRideTime")) {
            summaryJson.put("totalRideTime", 0);
        }

        if (!summaryJson.has("totalMovingTime")) {
            summaryJson.put("totalMovingTime", 0.0);
        }

        if (!summaryJson.has("totalStopTime")) {
            summaryJson.put("totalStopTime", 0.0);
        }

        if (!summaryJson.has("totalStopCount")) {
            summaryJson.put("totalStopCount", 0);
        }

        // records
        JSONObject recordsJson = rootJson.optJSONObject("records");

        if (recordsJson == null) {
            recordsJson = new JSONObject();
            rootJson.put("records", recordsJson);
        }

        if (!recordsJson.has("maxSingleRideDistance")) {
            recordsJson.put("maxSingleRideDistance", 0.0);
        }

        if (!recordsJson.has("maxSingleRideTime")) {
            recordsJson.put("maxSingleRideTime", 0);
        }

        if (!recordsJson.has("maxAverageSpeed")) {
            recordsJson.put("maxAverageSpeed", 0.0);
        }

        if (!recordsJson.has("maxMovingAverageSpeed")) {
            recordsJson.put("maxMovingAverageSpeed", 0.0);
        }

        if (!recordsJson.has("maxGpsSpeed")) {
            recordsJson.put("maxGpsSpeed", 0.0);
        }

        if (!recordsJson.has("longestStopTime")) {
            recordsJson.put("longestStopTime", 0.0);
        }

        // speedDistribution
        JSONObject speedJson = rootJson.optJSONObject("speedDistribution");

        if (speedJson == null) {
            speedJson = new JSONObject();
            rootJson.put("speedDistribution", speedJson);
        }

        if (!speedJson.has("time0to5")) {
            speedJson.put("time0to5", 0.0);
        }

        if (!speedJson.has("time5to10")) {
            speedJson.put("time5to10", 0.0);
        }

        if (!speedJson.has("time10to15")) {
            speedJson.put("time10to15", 0.0);
        }

        if (!speedJson.has("time15to20")) {
            speedJson.put("time15to20", 0.0);
        }

        if (!speedJson.has("time20to25")) {
            speedJson.put("time20to25", 0.0);
        }

        if (!speedJson.has("time25to30")) {
            speedJson.put("time25to30", 0.0);
        }

        if (!speedJson.has("time30Over")) {
            speedJson.put("time30Over", 0.0);
        }

        // distanceDistribution
        JSONObject distanceJson =
                rootJson.optJSONObject("distanceDistribution");

        if (distanceJson == null) {
            distanceJson = new JSONObject();
            rootJson.put("distanceDistribution", distanceJson);
        }

        if (!distanceJson.has("ride0to5km")) {
            distanceJson.put("ride0to5km", 0);
        }

        if (!distanceJson.has("ride5to10km")) {
            distanceJson.put("ride5to10km", 0);
        }

        if (!distanceJson.has("ride10to20km")) {
            distanceJson.put("ride10to20km", 0);
        }

        if (!distanceJson.has("ride20to50km")) {
            distanceJson.put("ride20to50km", 0);
        }

        if (!distanceJson.has("ride50kmOver")) {
            distanceJson.put("ride50kmOver", 0);
        }

        // monthly
        JSONObject monthlyJson = rootJson.optJSONObject("monthly");

        if (monthlyJson == null) {
            rootJson.put("monthly", new JSONObject());
        }
    }
}