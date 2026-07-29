package jp.ac.gifu_u.info.katsuya.prog;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/*
 * メンテナンスをするための通知チャンネル
 *
 * メンテナンス通知を出す条件
 *
 * 1. 日付による通知
 *    ・次回予定日が設定されている
 *    ・まだ日付通知を送っていない
 *    ・次回予定日まで残り7日以内
 *      または、すでに予定日を過ぎている
 *
 * 2. 距離による通知
 *    ・次回走行距離目安が設定されている
 *    ・まだ距離通知を送っていない
 *    ・次回距離目安まで残り50km以内
 *      または、すでに距離目安を超えている
 *
 * 日付条件または距離条件のどちらかを満たした場合に通知する。
 */
public class MaintenanceReminderWorker
        extends Worker {

    private static final String TAG =
            "MAINTENANCE_WORKER";

    public MaintenanceReminderWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            File maintenanceFile =
                    new File(
                            getApplicationContext()
                                    .getFilesDir(),
                            "maintenance.json"
                    );

            if (!maintenanceFile.exists()) {
                return Result.success();
            }

            JSONObject rootJson =
                    new JSONObject(
                            readTextFile(
                                    maintenanceFile
                            )
                    );

            JSONArray recordsArray =
                    rootJson.optJSONArray(
                            "records"
                    );

            if (recordsArray == null) {
                return Result.success();
            }

            double currentDistance =
                    getCurrentTotalDistanceMeters();

            boolean jsonChanged = false;

            for (int i = 0;
                 i < recordsArray.length();
                 i++) {

                JSONObject recordJson =
                        recordsArray.getJSONObject(i);

                String nextDate =
                        recordJson.optString(
                                "nextDate",
                                ""
                        );

                double nextDistance =
                        recordJson.optDouble(
                                "nextDistance",
                                0.0
                        );

                boolean dateNotificationSent =
                        recordJson.optBoolean(
                                "dateNotificationSent",
                                false
                        );

                boolean distanceNotificationSent =
                        recordJson.optBoolean(
                                "distanceNotificationSent",
                                false
                        );

                /*
                 * 日付まで7日以内なら通知対象
                 * 負数は予定日超過
                 */
                long daysUntil =
                        getDaysUntilDate(
                                nextDate
                        );

                boolean notifyByDate =
                        !dateNotificationSent
                                && !nextDate.isEmpty()
                                && daysUntil <= 7;

                /*
                 * 残り50km以内なら通知対象
                 * 負数は距離超過
                 */
                double remainingDistance =
                        nextDistance
                                - currentDistance;

                boolean notifyByDistance =
                        !distanceNotificationSent
                                && nextDistance > 0.0
                                && remainingDistance
                                <= 50000.0;

                if (!notifyByDate
                        && !notifyByDistance) {
                    continue;
                }

                showMaintenanceNotification(
                        recordJson,
                        notifyByDate,
                        notifyByDistance,
                        daysUntil,
                        remainingDistance
                );

                if (notifyByDate) {
                    recordJson.put(
                            "dateNotificationSent",
                            true
                    );
                }

                if (notifyByDistance) {
                    recordJson.put(
                            "distanceNotificationSent",
                            true
                    );
                }

                jsonChanged = true;
            }

            if (jsonChanged) {
                writeJsonFile(
                        maintenanceFile,
                        rootJson
                );
            }

            return Result.success();

        } catch (Exception e) {
            Log.e(
                    TAG,
                    "メンテナンス通知処理に失敗しました",
                    e
            );

            return Result.retry();
        }
    }

    /**
     * 通知を表示する
     */
    private void showMaintenanceNotification(
            JSONObject recordJson,
            boolean notifyByDate,
            boolean notifyByDistance,
            long daysUntil,
            double remainingDistance
    ) {
        Context context =
                getApplicationContext();

        /*
         * Android 13以降で権限がなければ通知しない
         */
        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {

            return;
        }

        String type =
                recordJson.optString(
                        "type",
                        "自転車"
                );

        String maintenanceTitle =
                recordJson.optString(
                        "title",
                        "メンテナンス"
                );

        String message =
                createNotificationMessage(
                        type,
                        notifyByDate,
                        notifyByDistance,
                        daysUntil,
                        remainingDistance
                );

        /*
         * 通知をタップしたらMainActivityを開く
         */
        Intent intent =
                new Intent(
                        context,
                        MainActivity.class
                );

        intent.putExtra(
                "openMaintenance",
                true
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        long recordId =
                recordJson.optLong(
                        "id",
                        System.currentTimeMillis()
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        (int) (recordId
                                & 0x7fffffff),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        MainActivity
                                .MAINTENANCE_CHANNEL_ID
                )
                        .setSmallIcon(
                                R.drawable
                                        .ic_launcher_foreground
                        )
                        .setContentTitle(
                                "メンテナンスのお知らせ"
                        )
                        .setContentText(
                                message
                        )
                        .setStyle(
                                new NotificationCompat
                                        .BigTextStyle()
                                        .bigText(
                                                message
                                                        + "\n"
                                                        + maintenanceTitle
                                        )
                        )
                        .setPriority(
                                NotificationCompat
                                        .PRIORITY_DEFAULT
                        )
                        .setAutoCancel(true)
                        .setContentIntent(
                                pendingIntent
                        );

        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (manager != null) {
            manager.notify(
                    (int) (recordId
                            & 0x7fffffff),
                    builder.build()
            );
        }
    }

    /**
     * 通知本文を作成する
     */
    private String createNotificationMessage(
            String type,
            boolean notifyByDate,
            boolean notifyByDistance,
            long daysUntil,
            double remainingDistance
    ) {
        StringBuilder message =
                new StringBuilder();

        message.append(type)
                .append("のメンテナンス時期です。");

        if (notifyByDate) {
            if (daysUntil < 0) {
                message.append(" 予定日を")
                        .append(
                                Math.abs(daysUntil)
                        )
                        .append("日過ぎています。");

            } else if (daysUntil == 0) {
                message.append(
                        " 予定日は今日です。"
                );

            } else {
                message.append(" 予定日まであと")
                        .append(daysUntil)
                        .append("日です。");
            }
        }

        if (notifyByDistance) {
            if (remainingDistance <= 0.0) {
                message.append(" 距離目安を")
                        .append(
                                Math.round(
                                        Math.abs(
                                                remainingDistance
                                        ) / 1000.0
                                )
                        )
                        .append("km超えています。");

            } else {
                message.append(" 距離目安まであと")
                        .append(
                                Math.round(
                                        remainingDistance
                                                / 1000.0
                                )
                        )
                        .append("kmです。");
            }
        }

        return message.toString();
    }

    /**
     * 指定日までの日数を求める
     */
    private long getDaysUntilDate(
            String dateKey
    ) {
        if (dateKey == null
                || dateKey.isEmpty()) {
            return Long.MAX_VALUE;
        }

        try {
            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.JAPAN
                    );

            format.setLenient(false);

            Date date =
                    format.parse(dateKey);

            if (date == null) {
                return Long.MAX_VALUE;
            }

            Calendar today =
                    Calendar.getInstance(
                            Locale.JAPAN
                    );

            today.set(
                    Calendar.HOUR_OF_DAY,
                    0
            );
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar target =
                    Calendar.getInstance(
                            Locale.JAPAN
                    );

            target.setTime(date);
            target.set(
                    Calendar.HOUR_OF_DAY,
                    0
            );
            target.set(Calendar.MINUTE, 0);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);

            return (
                    target.getTimeInMillis()
                            - today.getTimeInMillis()
            ) / (
                    24L
                            * 60L
                            * 60L
                            * 1000L
            );

        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * statistics.jsonから累計距離を取得する
     */
    private double getCurrentTotalDistanceMeters() {
        try {
            File file =
                    new File(
                            getApplicationContext()
                                    .getFilesDir(),
                            "statistics.json"
                    );

            if (!file.exists()) {
                return 0.0;
            }

            JSONObject rootJson =
                    new JSONObject(
                            readTextFile(file)
                    );

            JSONObject allTimeJson =
                    rootJson.optJSONObject(
                            "allTime"
                    );

            if (allTimeJson == null) {
                return 0.0;
            }

            JSONObject summaryJson =
                    allTimeJson.optJSONObject(
                            "summary"
                    );

            if (summaryJson == null) {
                return 0.0;
            }

            return summaryJson.optDouble(
                    "totalDistance",
                    0.0
            );

        } catch (Exception e) {
            Log.e(
                    TAG,
                    "累計距離の読み込みに失敗",
                    e
            );

            return 0.0;
        }
    }

    private String readTextFile(
            File file
    ) throws Exception {
        FileInputStream fis =
                new FileInputStream(file);

        byte[] data =
                new byte[(int) file.length()];

        int readLength =
                fis.read(data);

        fis.close();

        if (readLength < 0) {
            return "";
        }

        return new String(
                data,
                0,
                readLength,
                StandardCharsets.UTF_8
        );
    }

    private void writeJsonFile(
            File file,
            JSONObject rootJson
    ) throws Exception {
        FileOutputStream fos =
                new FileOutputStream(file);

        fos.write(
                rootJson
                        .toString(4)
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );

        fos.close();
    }
}