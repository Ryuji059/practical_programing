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

public class LocationTrackingService extends Service {

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
        saveRouteToJson();

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

    private void saveRouteToJson() {
        try {
            if (routePoints.size() < 2) {
                Log.d("TRACKING_SERVICE", "記録点が少なすぎるため保存しません");
                return;
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
        }
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
}