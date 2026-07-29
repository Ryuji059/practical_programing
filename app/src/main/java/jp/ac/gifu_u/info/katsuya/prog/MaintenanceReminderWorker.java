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
 * メンテナンス時期を確認し、条件を満たした場合に通知を表示するWorker
 *
 * Workerは画面とは独立してバックグラウンドで実行される。
 * MainActivityからWorkManagerに登録され、1日1回程度このクラスのdoWork()が呼ばれる。
 *
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

    //Logcatへログを出力するときに使用するタグ
    private static final String TAG =
            "MAINTENANCE_WORKER";

    /**
     * Workerのコンストラクタ
     *
     * context：アプリのファイルや通知機能を利用するためのContext
     * workerParams：WorkManagerから渡されるWorkerの実行情報
     */
    public MaintenanceReminderWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        //親クラスであるWorkerへ必要な情報を渡す
        super(context, workerParams);
    }

    /**
     * WorkManagerによって呼び出されるメイン処理
     *
     * maintenance.jsonの全記録を確認し、
     * 日付条件または距離条件を満たす記録に対して通知を表示する。
     */
    @NonNull
    @Override
    public Result doWork() {
        try {
            //アプリ専用領域にあるmaintenance.jsonを指定
            File maintenanceFile =
                    new File(
                            getApplicationContext()
                                    .getFilesDir(),
                            "maintenance.json"
                    );

            //メンテナンス記録がまだ一件も保存されていない場合は何もせず正常終了
            if (!maintenanceFile.exists()) {
                return Result.success();
            }

            //maintenance.jsonを文字列として読み込み、JSONObjectへ変換
            JSONObject rootJson =
                    new JSONObject(
                            readTextFile(
                                    maintenanceFile
                            )
                    );

            //JSON内のメンテナンス記録一覧であるrecords配列を取得
            JSONArray recordsArray =
                    rootJson.optJSONArray(
                            "records"
                    );

            //records配列が存在しない場合も通知対象がないため正常終了
            if (recordsArray == null) {
                return Result.success();
            }

            //statistics.jsonから、現在までの累計走行距離をメートル単位で取得
            double currentDistance =
                    getCurrentTotalDistanceMeters();

            //通知済みフラグを書き換えたかを記録する
            boolean jsonChanged = false;

            //保存されているすべてのメンテナンス記録を順番に確認
            for (int i = 0;
                 i < recordsArray.length();
                 i++) {

                //i番目のメンテナンス記録を取得
                JSONObject recordJson =
                        recordsArray.getJSONObject(i);

                //次回メンテナンス予定日を取得
                //未設定の場合は空文字列
                String nextDate =
                        recordJson.optString(
                                "nextDate",
                                ""
                        );

                //次回メンテナンスを行う累計走行距離の目安を取得
                //単位はメートル。未設定の場合は0
                double nextDistance =
                        recordJson.optDouble(
                                "nextDistance",
                                0.0
                        );

                //日付による通知をすでに送信したかを取得
                boolean dateNotificationSent =
                        recordJson.optBoolean(
                                "dateNotificationSent",
                                false
                        );

                //距離による通知をすでに送信したかを取得
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

                //日付通知の条件
                //未通知、予定日が設定済み、予定日まで7日以内のすべてを満たす
                //daysUntilが負数の場合も「7以下」なので、予定日超過として通知される
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

                //距離通知の条件
                //未通知、距離目安が設定済み、残り50km以内のすべてを満たす
                //remainingDistanceが負数の場合も、距離目安を超過したものとして通知される
                boolean notifyByDistance =
                        !distanceNotificationSent
                                && nextDistance > 0.0
                                && remainingDistance
                                <= 50000.0;

                //日付条件と距離条件の両方を満たさない記録は次の記録へ進む
                if (!notifyByDate
                        && !notifyByDistance) {
                    continue;
                }

                //条件を満たしたメンテナンス記録について通知を表示
                showMaintenanceNotification(
                        recordJson,
                        notifyByDate,
                        notifyByDistance,
                        daysUntil,
                        remainingDistance
                );

                //同じ日付通知を繰り返さないよう通知済みに変更
                //日付条件で通知する場合、予定日までの日数を本文へ追加
                if (notifyByDate) {
                    recordJson.put(
                            "dateNotificationSent",
                            true
                    );
                }

                //同じ距離通知を繰り返さないよう通知済みに変更
                //距離条件で通知する場合、残り距離または超過距離を本文へ追加
                if (notifyByDistance) {
                    recordJson.put(
                            "distanceNotificationSent",
                            true
                    );
                }

                jsonChanged = true;
            }

            //通知済みフラグを変更した場合だけmaintenance.jsonを上書き保存
            if (jsonChanged) {
                writeJsonFile(
                        maintenanceFile,
                        rootJson
                );
            }

            //すべての確認処理が正常に完了
            return Result.success();

        } catch (Exception e) {
            Log.e(
                    TAG,
                    "メンテナンス通知処理に失敗しました",
                    e
            );

            //一時的な失敗としてWorkManagerへ再実行を依頼
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
        //Activityではなくアプリ全体で利用できるContextを取得
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

        //メンテナンスの種類を取得
        String type =
                recordJson.optString(
                        "type",
                        "自転車"
                );

        //登録されている具体的な作業内容を取得
        String maintenanceTitle =
                recordJson.optString(
                        "title",
                        "メンテナンス"
                );

        //日付・距離の状態に応じて通知本文を作成
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

        //メンテナンス記録ごとに異なる通知IDを作るため、記録IDを取得
        long recordId =
                recordJson.optLong(
                        "id",
                        System.currentTimeMillis()
                );

        //通知をタップしたときにMainActivityを開くためのPendingIntentを作成
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        (int) (recordId
                                & 0x7fffffff),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        //通知に表示するアイコン、タイトル、本文などを設定
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

        //Androidの通知機能を管理するNotificationManagerを取得
        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        //NotificationManagerを取得できた場合に通知を表示
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
        //条件に応じて文字列を後ろへ追加するためStringBuilderを使用
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

        //完成した通知本文をStringとして返す
        return message.toString();
    }

    /**
     * 指定日までの日数を求める
     */
    private long getDaysUntilDate(
            String dateKey
    ) {
        //予定日が未設定の場合は通知条件を満たさないよう非常に大きな値を返す
        if (dateKey == null
                || dateKey.isEmpty()) {
            return Long.MAX_VALUE;
        }

        try {
            //JSONの「yyyy-MM-dd」形式をDateへ変換するための書式
            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.JAPAN
                    );

            //存在しない日付を自動補正せず、正しくない日付として扱う
            format.setLenient(false);

            Date date =
                    format.parse(dateKey);

            if (date == null) {
                return Long.MAX_VALUE;
            }

            //今日の日付を取得
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

            //次回メンテナンス予定日をCalendarとして取得
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

            //予定日と今日の差をミリ秒で求め、1日分のミリ秒で割って日数へ変換
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
            //日付形式が不正な場合は通知対象にしない
            return Long.MAX_VALUE;
        }
    }

    /**
     * statistics.jsonから累計距離を取得する
     * 戻り値の単位はメートル
     */
    private double getCurrentTotalDistanceMeters() {
        try {
            //アプリ専用領域にあるstatistics.jsonを指定
            File file =
                    new File(
                            getApplicationContext()
                                    .getFilesDir(),
                            "statistics.json"
                    );

            //統計ファイルがなければ累計距離を0mとして扱う
            if (!file.exists()) {
                return 0.0;
            }

            //statistics.jsonを読み込んでJSONObjectへ変換
            JSONObject rootJson =
                    new JSONObject(
                            readTextFile(file)
                    );

            //全期間の統計データであるallTimeを取得
            JSONObject allTimeJson =
                    rootJson.optJSONObject(
                            "allTime"
                    );

            if (allTimeJson == null) {
                return 0.0;
            }

            //全期間データ内の集計結果summaryを取得
            JSONObject summaryJson =
                    allTimeJson.optJSONObject(
                            "summary"
                    );

            if (summaryJson == null) {
                return 0.0;
            }

            //累計走行距離totalDistanceをメートル単位で返す
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

    /**
     * 指定したテキストファイルをUTF-8で読み込む共通関数
     */
    private String readTextFile(
            File file
    ) throws Exception {
        //ファイルを読み込むためのストリームを開く
        FileInputStream fis =
                new FileInputStream(file);

        //ファイルサイズと同じ長さのバイト配列を用意
        byte[] data =
                new byte[(int) file.length()];

        //ファイルの内容をバイト配列へ読み込み、実際に読めた長さを保存
        int readLength =
                fis.read(data);

        //読み込み後はファイルを閉じる
        fis.close();

        if (readLength < 0) {
            return "";
        }

        //読み込んだバイト列をUTF-8の文字列へ変換して返す
        return new String(
                data,
                0,
                readLength,
                StandardCharsets.UTF_8
        );
    }

    /**
     * JSONObjectを整形したJSONとしてファイルへ保存する共通関数
     */
    private void writeJsonFile(
            File file,
            JSONObject rootJson
    ) throws Exception {
        //指定されたファイルを上書き保存するためのストリームを開く
        FileOutputStream fos =
                new FileOutputStream(file);

        //インデント幅4でJSONを整形し、UTF-8でファイルへ書き込む
        fos.write(
                rootJson
                        .toString(4)
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );

        //書き込み後はファイルを閉じる
        fos.close();
    }
}