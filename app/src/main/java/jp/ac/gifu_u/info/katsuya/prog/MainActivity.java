package jp.ac.gifu_u.info.katsuya.prog;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
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
import android.widget.RadioGroup;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.views.overlay.MapEventsOverlay;
import android.graphics.Point;
import androidx.appcompat.app.AlertDialog;
import android.widget.EditText;
import android.widget.PopupMenu;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import android.widget.CalendarView;
import android.widget.GridLayout;
import java.util.HashSet;

public class MainActivity extends AppCompatActivity {
    private enum AppMode {
        MAP,            // 通常地図
        RECORDING,      // 記録中
        HISTORY,        // 履歴表示
        HISTORY_DETAIL, // 走行経路の表示
        EDIT_ROAD,      // 道路色分け
        STATISTICS,     //統計情報
        MAINTENANCE,    //メンテナンス記録
        SETTINGS        //設定
    }
    /**
     * 履歴一覧で使用する1件分のデータ
     */
    private static class HistoryItem {
        File file;
        long startTime;
        long endTime;
        double distance;

        HistoryItem(
                File file,
                long startTime,
                long endTime,
                double distance
        ) {
            this.file = file;
            this.startTime = startTime;
            this.endTime = endTime;
            this.distance = distance;
        }
    }
    //メンテナンス予定日を入れた配列
    private static final String[] MAINTENANCE_INTERVALS = {
            "なし",
            "1週間後",
            "2週間後",
            "1か月後",
            "2か月後",
            "3か月後",
            "6か月後",
            "1年後"
    };
    // 走行距離による次回メンテナンス目安
    private static final String[] MAINTENANCE_DISTANCE_INTERVALS = {
            "なし",
            "100 km後",
            "300 km後",
            "500 km後",
            "1,000 km後",
            "2,000 km後",
            "3,000 km後",
            "5,000 km後"
    };
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
    private View mapLayout;//マップ表示
    private View historyLayout;//走行履歴表示
    private View recordPanel;//記録関係のパネル
    private View roadEditPanel;//色分けのパネル
    private TextView titleBar;//画面上部のタイトルバー
    private LinearLayout historyList;//走行履歴の表示用リスト
    // 履歴のフィルター・並び替え
    private Spinner spinnerHistoryPeriod;
    private Spinner spinnerHistorySort;
    private MapView historyMap;//走行履歴のルート表示用のMAP
    private View historyDetailLayout;//走行データの詳細表示用のレイアウト
    private Polyline historyRouteLine;//走行履歴のルートの線
    private View routeDetailPanel;//走行詳細用のパネル
    private boolean detailPanelOpen = false;//詳細を開いているかどうかのフラグ
    private TextView detailDistance;//走行距離を表示
    private TextView detailTime;//走行時間の表示
    private TextView detailAverageSpeed;//平均速度の表示
    private TextView detailMaxGpsSpeed;//最大GPS速度の表示
    private TextView detailMaxSectionSpeed;//最大区間平均速度の表示
    // 日付・ルート名
    private TextView detailDate;
    private TextView detailRouteName;

    // 走行分析
    private TextView detailMovingTime;//移動時間
    private TextView detailStopTime;//停止時間
    private TextView detailStopCount;//停止回数
    private TextView detailLongestStopTime;//最長停止時間
    private TextView detailMovingAverageSpeed;//移動速度の平均

    // 時間割合
    private TextView detailMovingRatio;//移動時間の割合
    private TextView detailStopRatio;//停止時間の割合

    // 速度内訳
    private TextView detailSpeed0to5;//0~5km/hの走行速度の割合
    private TextView detailSpeed5to10;//5~10km/hの走行速度の割合
    private TextView detailSpeed10to15;//10~15km/hの走行速度の割合
    private TextView detailSpeed15to20;//15~20km/hの走行速度の割合
    private TextView detailSpeed20Over;//20km/h~の走行速度の割合

    //色分けモード用の変数
    private RoadSegment editingRoad;
    private ArrayList<RoadSegment> roadSegments = new ArrayList<>();
    private Polyline roadPreviewLine;
    private ArrayList<Polyline> roadLines = new ArrayList<>();
    private RadioGroup radioRoadType;
    private MapEventsOverlay mapEventsOverlay;//線上の点を選ぶための対策
    private GeoPoint lastRoadEndPoint = null;//最後の記録地点
    private int selectedRoadIndex = -1;//選択中の線を管理するインデックス(-1は選択していない状態を表す)
    private boolean isFollowingCurrentLocation = false;//trueの時は現在地を追従する
    private View selectedRoadPanel;//色分けモード中道路を選択しているときのビュー
    private ArrayList<Polyline> historySpeedLines = new ArrayList<>();//履歴走行ルートの速度による色分け表示
    private long lastGpsLocationTime = 0;//GPS取得時の時間
    private boolean isSpeedColorMode = true;//速度によって色を付けるかどうか
    private JSONArray currentHistoryPointsArray = null;//現在表示中のJSONの点データ
    private ArrayList<GeoPoint> currentHistoryGeoPoints = new ArrayList<>();//現在表示中のルートの点
    private Button btnToggleSpeedColor;//色を付けるかどうかを切り替えるボタン
    // GPSフィルター用
    private static final float RECORDING_MAX_ACCURACY = 30.0f; // 記録中に許可する最大誤差[m]
    private static final double MAX_REASONABLE_SPEED_KMH = 60.0; // 自転車として異常な速度[km/h]
    private static final double STOP_JITTER_DISTANCE = 8.0; // 停止中ブレとみなす距離[m]
    private static final float STOP_JITTER_SPEED = 0.8f; // 停止中ブレとみなすGPS速度[m/s]
    //LocationTrackingServiseから記録した点の緯度経度と累計走行距離を渡すための変数
    private BroadcastReceiver trackingReceiver;//LocationTrackingServiseからデータを受け取るためのBroadcastReceiverを追加
    private ArrayList<GeoPoint> liveRouteGeoPoints = new ArrayList<>();//記録点の格納リスト
    private View topBar;//トップバー
    private TextView btnMainMenu;//ハンバーガーメニューボタン

    //統計用の変数
    private View statisticsLayout;//統計用のレイアウト

    private TextView statTotalRideCount;
    private TextView statTotalDistance;
    private TextView statTotalRideTime;
    private TextView statTotalMovingTime;
    private TextView statTotalStopTime;

    private TextView statMaxDistance;
    private TextView statMaxRideTime;
    private TextView statMaxAverageSpeed;
    private TextView statMaxGpsSpeed;

    private TextView btnStatisticsMenu;
    private TextView statTotalStopCount;

    private TextView statMaxMovingAverageSpeed;
    private TextView statLongestStopTime;

    private TextView statSpeed0to5;
    private TextView statSpeed5to10;
    private TextView statSpeed10to15;
    private TextView statSpeed15to20;
    private TextView statSpeed20to25;
    private TextView statSpeed25to30;
    private TextView statSpeed30Over;

    private TextView statDistance0to5;
    private TextView statDistance5to10;
    private TextView statDistance10to20;
    private TextView statDistance20to50;
    private TextView statDistance50Over;
    private Spinner spinnerStatisticsType;
    private Spinner spinnerStatisticsPeriod;
    private TextView statSelectedPeriod;

    // statistics.json全体を一時保存
    private JSONObject currentStatisticsRoot;

    // 2番目のSpinnerに表示する名前
    private ArrayList<String> statisticsPeriodLabels = new ArrayList<>();

    // 実際にJSON検索に使うキー
    private ArrayList<String> statisticsPeriodKeys = new ArrayList<>();

    private boolean isUpdatingStatisticsSpinner = false;
    //グラフ表示用
    private DistanceBarChartView statDistanceChart;
    private TextView statDistanceChartTitle;
    // メンテナンス画面用
    private View maintenanceLayout;

    private GridLayout maintenanceCalendarGrid;
    private TextView textCalendarMonth;
    private Button btnPreviousMonth;
    private Button btnNextMonth;

    private TextView maintenanceSelectedDate;
    private LinearLayout maintenanceList;
    private TextView btnMaintenanceMenu;
    private Button btnAddMaintenance;

    // 現在カレンダーに表示している年月
    private Calendar maintenanceDisplayCalendar;

    // メンテナンス実施記録が存在する日付
    private final HashSet<String> maintenanceRecordDates =
            new HashSet<>();

    // 次回メンテナンス予定日
    private final HashSet<String> maintenanceScheduledDates =
            new HashSet<>();

    // 現在選択している日付
    private String selectedMaintenanceDateKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());//OpenStreetMapのサーバーへ「このアプリがアクセスしています」と名乗る
        //レイアウト読み込み
        setContentView(R.layout.activity_main);
        View rootLayout = findViewById(R.id.rootLayout);

        //画面を少し下にずらす
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            v.setPadding(
                    0,
                    systemBars.top,
                    0,
                    0
            );

            return insets;
        });

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
        spinnerHistoryPeriod =
                findViewById(R.id.spinnerHistoryPeriod);

        spinnerHistorySort =
                findViewById(R.id.spinnerHistorySort);
        //走行経路表示用のMAPの初期化
        historyDetailLayout = findViewById(R.id.historyDetailLayout);

        historyMap = findViewById(R.id.historyMap);
        historyMap.setTileSource(TileSourceFactory.MAPNIK);//地図のタイプを設定
        historyMap.setMultiTouchControls(true);//日本指で操作可能に

        historyRouteLine = new Polyline();//走行履歴のルート表示
        historyRouteLine.setColor(Color.BLUE);//走行履歴のルート表示を青色に
        historyRouteLine.setWidth(8.0f);//フォントサイズの設定
        historyMap.getOverlays().add(historyRouteLine);//地図の上に線を表示できるようにする
        //詳細データ表示用のID取得
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
        Button btnUndoRoadEdit = findViewById(R.id.btnUndoRoadEdit);
        Button btnClearRoadEdit = findViewById(R.id.btnClearRoadEdit);
        Button btnDeleteRoadEdit = findViewById(R.id.btnDeleteRoadEdit);
        Button btnSaveRoadEdit = findViewById(R.id.btnSaveRoadEdit);
        Button btnFinishRoadEdit = findViewById(R.id.btnFinishRoadEdit);
        TextView btnBackHistory = findViewById(R.id.btnBackHistory);
        selectedRoadPanel = findViewById(R.id.selectedRoadPanel);
        Button btnEditRoadMemo = findViewById(R.id.btnEditRoadMemo);
        Button btnCancelRoadSelection = findViewById(R.id.btnCancelRoadSelection);
        btnToggleSpeedColor = findViewById(R.id.btnToggleSpeedColor);
        topBar = findViewById(R.id.topBar);
        btnMainMenu = findViewById(R.id.btnMainMenu);

        //各種ボタンの機能実装
        btnHistoryMode.setOnClickListener(v -> changeMode(AppMode.HISTORY));
        btnRoadEditMode.setOnClickListener(v -> changeMode(AppMode.EDIT_ROAD));
        btnBackMapFromHistory.setOnClickListener(v -> changeMode(AppMode.MAP));

        //ハンバーガーメニューバーの機能実装
        btnMainMenu.setOnClickListener(v -> {
            showMainMenu(btnMainMenu);
        });

        //色分けモードのボタン処理
        btnUndoRoadEdit.setOnClickListener(v -> {
            if (editingRoad == null || editingRoad.points.size() == 0) {
                Toast.makeText(this, "取り消す点がありません", Toast.LENGTH_SHORT).show();
                return;
            }

            // 前の線から引き継いだ始点だけの場合は消さない
            if (lastRoadEndPoint != null && editingRoad.points.size() == 1) {
                Toast.makeText(this, "始点は取り消せません", Toast.LENGTH_SHORT).show();
                return;
            }

            // 最後の点を削除
            editingRoad.points.remove(editingRoad.points.size() - 1);

            // 仮線を更新
            roadPreviewLine.setPoints(new ArrayList<>(editingRoad.points));
            roadPreviewLine.setColor(getColorByRoadType(getSelectedRoadType()));

            map.invalidate();

            Toast.makeText(this, "最後の点を取り消しました", Toast.LENGTH_SHORT).show();
        });
        btnClearRoadEdit.setOnClickListener(v -> {
            // 新しく編集中の線を作り直す
            editingRoad = new RoadSegment(getSelectedRoadType());

            // 前の線の終点から続ける仕様なので、始点だけ残す
            if (lastRoadEndPoint != null) {
                editingRoad.points.add(lastRoadEndPoint);
            }

            // 仮線を更新
            roadPreviewLine.setPoints(new ArrayList<>(editingRoad.points));
            roadPreviewLine.setColor(getColorByRoadType(editingRoad.type));

            map.invalidate();

            Toast.makeText(this, "編集中の線をクリアしました", Toast.LENGTH_SHORT).show();
        });
        btnDeleteRoadEdit.setOnClickListener(v -> {
            if (selectedRoadIndex < 0 || selectedRoadIndex >= roadSegments.size()) {
                Toast.makeText(this, "削除する線を長押しで選択してください", Toast.LENGTH_SHORT).show();
                return;
            }

            Polyline deleteLine = roadLines.get(selectedRoadIndex);

            map.getOverlays().remove(deleteLine);
            roadLines.remove(selectedRoadIndex);
            roadSegments.remove(selectedRoadIndex);

            selectedRoadIndex = -1;
            selectedRoadPanel.setVisibility(View.GONE);

            saveRoadSegmentsToJson();

            // タップ判定用Overlayを一番上に戻す
            map.getOverlays().remove(mapEventsOverlay);
            map.getOverlays().add(mapEventsOverlay);

            map.invalidate();

            Toast.makeText(this, "選択した線を削除しました", Toast.LENGTH_SHORT).show();
        });
        btnSaveRoadEdit.setOnClickListener(v -> {
            if (editingRoad == null || editingRoad.points.size() < 2) {
                Toast.makeText(this, "2点以上選択してください", Toast.LENGTH_SHORT).show();
                return;
            }

            showMemoInputDialogForEditingRoad();
        });
        btnEditRoadMemo.setOnClickListener(v -> {
            if (selectedRoadIndex < 0 || selectedRoadIndex >= roadSegments.size()) {
                Toast.makeText(this, "編集する線を選択してください", Toast.LENGTH_SHORT).show();
                return;
            }

            showMemoEditDialogForSelectedRoad();
        });
        btnCancelRoadSelection.setOnClickListener(v -> {
            clearSelectedRoadSegment();
        });
        btnFinishRoadEdit.setOnClickListener(v -> {
            editingRoad = null;
            roadPreviewLine.setPoints(new ArrayList<>());
            map.invalidate();
            lastRoadEndPoint = null;
            selectedRoadIndex = -1;

            changeMode(AppMode.MAP);
        });
        btnBackHistory.setOnClickListener(v -> changeMode(AppMode.HISTORY));

        // =========================
        // 履歴期間フィルター
        // =========================
        ArrayList<String> historyPeriodItems =
                new ArrayList<>();

        historyPeriodItems.add("すべての期間");
        historyPeriodItems.add("今週");
        historyPeriodItems.add("今月");
        historyPeriodItems.add("今年");

        ArrayAdapter<String> historyPeriodAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.spinner_item,
                        historyPeriodItems
                );

        historyPeriodAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item
        );

        spinnerHistoryPeriod.setAdapter(
                historyPeriodAdapter
        );

        // =========================
        // 履歴並び替え
        // =========================
        ArrayList<String> historySortItems =
                new ArrayList<>();

        historySortItems.add("新しい順");
        historySortItems.add("古い順");
        historySortItems.add("距離が長い順");
        historySortItems.add("距離が短い順");

        ArrayAdapter<String> historySortAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.spinner_item,
                        historySortItems
                );

        historySortAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item
        );

        spinnerHistorySort.setAdapter(
                historySortAdapter
        );

        // 選択変更時に履歴を再表示
        AdapterView.OnItemSelectedListener historyFilterListener =
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        if (currentMode == AppMode.HISTORY) {
                            loadHistoryList();
                        }
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                    }
                };

        spinnerHistoryPeriod.setOnItemSelectedListener(
                historyFilterListener
        );

        spinnerHistorySort.setOnItemSelectedListener(
                historyFilterListener
        );


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

        // 色分け用の仮線
        roadPreviewLine = new Polyline();
        roadPreviewLine.setColor(Color.RED);
        roadPreviewLine.setWidth(10.0f);
        map.getOverlays().add(roadPreviewLine);

        radioRoadType = findViewById(R.id.radioRoadType);
        radioRoadType.check(R.id.radioSidewalk);
        radioRoadType.setOnCheckedChangeListener((group, checkedId) -> {
            RoadType selectedType = getSelectedRoadType();

            // すでに保存済みの線を選択している場合
            if (selectedRoadIndex >= 0 && selectedRoadIndex < roadSegments.size()) {
                RoadSegment selectedSegment = roadSegments.get(selectedRoadIndex);
                selectedSegment.type = selectedType;

                Polyline selectedLine = roadLines.get(selectedRoadIndex);
                selectedLine.setColor(getColorByRoadType(selectedType));

                // 選択中だと分かるように太さは太いままにする
                selectedLine.setWidth(18.0f);

                saveRoadSegmentsToJson();

                map.invalidate();

                Toast.makeText(this, "選択中の線の種類を変更しました", Toast.LENGTH_SHORT).show();
                return;
            }

            // 編集中の仮線がある場合
            if (editingRoad != null) {
                editingRoad.type = selectedType;
                roadPreviewLine.setColor(getColorByRoadType(editingRoad.type));
                map.invalidate();
            }
        });

        //地図に線を書き入れるための処理
        MapEventsReceiver receiver = new MapEventsReceiver() {
            //地図をシングルタップした時の動作
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (currentMode == AppMode.EDIT_ROAD) {//色分けモードの時(地図に線を引く)
                    clearSelectedRoadSegment();//道路区間の選択をリセット

                    RoadType selectedType = getSelectedRoadType();//選択中のロードタイプを取得

                    if (editingRoad == null) {//新しく引く場合は、インスタンスを作る
                        editingRoad = new RoadSegment(selectedType);
                    }

                    editingRoad.type = selectedType;//区分をれらばれているものに変更
                    editingRoad.points.add(p);//タップした点の座標を記録

                    roadPreviewLine.setPoints(editingRoad.points);//線を引く
                    roadPreviewLine.setColor(getColorByRoadType(editingRoad.type));//線の色を区分に沿って変更

                    map.invalidate();//地図を再描画
                    return true;
                }

                if (currentMode == AppMode.MAP) {//地図モードの時(コメントを表示)
                    int index = findNearestRoadSegmentIndex(p);//タップした区画を探索

                    if (index != -1) {//見つかった場合、その区画のコメントを表示
                        showRoadSegmentMemoDialog(index);
                        return true;
                    }
                }

                return false;
            }

            //地図を長押しした時の処理
            @Override
            public boolean longPressHelper(GeoPoint p) {
                if (currentMode == AppMode.EDIT_ROAD) {//色分けモード時、区画を選択
                    int index = findNearestRoadSegmentIndex(p);//長押ししている当たりの区画を探索

                    if (index == -1) {//見つからなかった場合は、「近くに線がありません」と表示
                        clearSelectedRoadSegment();
                        Toast.makeText(MainActivity.this, "近くに線がありません", Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    //見つかった場合は、線を太くして「線を選択しました」と表示
                    selectRoadSegment(index);
                    Toast.makeText(MainActivity.this, "線を選択しました", Toast.LENGTH_SHORT).show();
                    return true;
                }

                return false;
            }
        };

        //mapEventOverlayを作成し、マップに追加
        mapEventsOverlay = new MapEventsOverlay(receiver);
        map.getOverlays().add(mapEventsOverlay);

        // 保存済みの色分け道路を読み込む
        loadRoadSegmentsFromJson();

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
            isFollowingCurrentLocation = true;
            changeMode(AppMode.RECORDING);//記録モードに

            // MainActivity側の表示用ルート線は一度消す
            // Service側で記録するので、ここでは保存用データは初期化しない
            routeLine.setPoints(new ArrayList<>());
            liveRouteGeoPoints.clear();//表示用ルートを初期化
            map.invalidate();

            //フォアグラウンドサービスを開始
            Intent intent = new Intent(this, LocationTrackingService.class);
            intent.setAction(LocationTrackingService.ACTION_START);

            //アンドロイドのバージョンによって開始するための呼び出し関数が変わるので分けて書く
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            Toast.makeText(this, "記録を開始しました", Toast.LENGTH_SHORT).show();
        });

        //終了ボタン
        btnStop.setOnClickListener(v -> {
            if (!isRecording) {
                Toast.makeText(this, "現在は記録中ではありません", Toast.LENGTH_SHORT).show();
                return;
            }

            isRecording = false;
            isFollowingCurrentLocation = false;

            //フォアグラウンドサービスの終了
            Intent intent = new Intent(this, LocationTrackingService.class);
            intent.setAction(LocationTrackingService.ACTION_STOP);
            startService(intent);

            Toast.makeText(this, "記録を停止して保存しました", Toast.LENGTH_SHORT).show();

            changeMode(AppMode.MAP);//地図モードに変更
        });

        //速度色分け切り替えボタンの処理
        btnToggleSpeedColor.setOnClickListener(v -> {
            isSpeedColorMode = !isSpeedColorMode;//booleanを反転させる
            updateHistoryRouteDisplay();//線の切り替え表示処理
        });

        //走行履歴の詳細データの表示用パネルの設定
        routeDetailPanel.setOnClickListener(v -> {
            if (detailPanelOpen) {//詳細欄を触ったら、下から上に上がってくるモーションをする。
                routeDetailPanel.animate().translationY(dp(390)).setDuration(250).start();
                detailPanelOpen = false;
            } else {//閉じるときのアニメーションを追加
                routeDetailPanel.animate().translationY(0f).setDuration(250).start();
                detailPanelOpen = true;
            }
        });

        //統計関連のID取得
        statisticsLayout = findViewById(R.id.statisticsLayout);

        statTotalRideCount = findViewById(R.id.statTotalRideCount);
        statTotalDistance = findViewById(R.id.statTotalDistance);
        statTotalRideTime = findViewById(R.id.statTotalRideTime);
        statTotalMovingTime = findViewById(R.id.statTotalMovingTime);
        statTotalStopTime = findViewById(R.id.statTotalStopTime);

        statMaxDistance = findViewById(R.id.statMaxDistance);
        statMaxRideTime = findViewById(R.id.statMaxRideTime);
        statMaxAverageSpeed = findViewById(R.id.statMaxAverageSpeed);
        statMaxGpsSpeed = findViewById(R.id.statMaxGpsSpeed);

        btnStatisticsMenu = findViewById(R.id.btnStatisticsMenu);

        statTotalStopCount = findViewById(R.id.statTotalStopCount);

        statMaxMovingAverageSpeed = findViewById(R.id.statMaxMovingAverageSpeed);

        statLongestStopTime = findViewById(R.id.statLongestStopTime);

        statSpeed0to5 = findViewById(R.id.statSpeed0to5);
        statSpeed5to10 = findViewById(R.id.statSpeed5to10);
        statSpeed10to15 = findViewById(R.id.statSpeed10to15);
        statSpeed15to20 = findViewById(R.id.statSpeed15to20);
        statSpeed20to25 = findViewById(R.id.statSpeed20to25);
        statSpeed25to30 = findViewById(R.id.statSpeed25to30);
        statSpeed30Over = findViewById(R.id.statSpeed30Over);

        statDistance0to5 = findViewById(R.id.statDistance0to5);
        statDistance5to10 = findViewById(R.id.statDistance5to10);
        statDistance10to20 = findViewById(R.id.statDistance10to20);
        statDistance20to50 = findViewById(R.id.statDistance20to50);
        statDistance50Over = findViewById(R.id.statDistance50Over);

        //表示期間変更用スピナーのID取得
        spinnerStatisticsType =
                findViewById(R.id.spinnerStatisticsType);

        spinnerStatisticsPeriod =
                findViewById(R.id.spinnerStatisticsPeriod);

        statSelectedPeriod =
                findViewById(R.id.statSelectedPeriod);

        //走行距離のグラフ用
        statDistanceChart =
                findViewById(R.id.statDistanceChart);

        statDistanceChartTitle =
                findViewById(R.id.statDistanceChartTitle);

        //統計関連用のボタンの実装
        btnStatisticsMenu.setOnClickListener(v -> {
            showMainMenu(btnStatisticsMenu);
        });

        //統計用種類スピナーの初期化
        ArrayList<String> typeList = new ArrayList<>();

        //スピナーに表示する項目を追加
        typeList.add("全体");
        typeList.add("年");
        typeList.add("月");
        typeList.add("週");

        //スピナーを作成
        ArrayAdapter<String> typeAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.spinner_item,
                        typeList
                );

        //レイアウト指定
        typeAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item
        );

        //spinnerStatisticsTypeに上で作ったスピナーを適応
        spinnerStatisticsType.setAdapter(typeAdapter);

        //スピナー選択時の処理
        spinnerStatisticsType.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    //スピナーの項目が選ばれたときの処理
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        if (isUpdatingStatisticsSpinner) {
                            return;//今更新中なら何もしない
                        }
                        //選択された項目になるように統計を更新
                        updateStatisticsPeriodSpinner();
                    }

                    //何も選ばれなかった時の処理
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );

        //表示時期を変更するスライダーの処理
        spinnerStatisticsPeriod.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    //何か選ばれたときの処理
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        //変更中なら何もしない
                        if (isUpdatingStatisticsSpinner) {
                            return;
                        }

                        //選択したものに合わせて統計データの表示を更新
                        displaySelectedStatistics();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );

        //メンテナンスモード用
        // メンテナンス画面のID取得
        maintenanceLayout =
                findViewById(R.id.maintenanceLayout);

        maintenanceCalendarGrid =
                findViewById(
                        R.id.maintenanceCalendarGrid
                );

        textCalendarMonth =
                findViewById(
                        R.id.textCalendarMonth
                );

        btnPreviousMonth =
                findViewById(
                        R.id.btnPreviousMonth
                );

        btnNextMonth =
                findViewById(
                        R.id.btnNextMonth
                );

        maintenanceSelectedDate =
                findViewById(R.id.maintenanceSelectedDate);

        maintenanceList =
                findViewById(R.id.maintenanceList);

        btnMaintenanceMenu =
                findViewById(R.id.btnMaintenanceMenu);

        btnAddMaintenance =
                findViewById(R.id.btnAddMaintenance);

        //今日の日付を選択
        Date today = new Date();

        selectedMaintenanceDateKey =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.JAPAN
                ).format(today);

        maintenanceSelectedDate.setText(
                "選択日: "
                        + new SimpleDateFormat(
                        "yyyy年M月d日",
                        Locale.JAPAN
                ).format(today)
        );

        // 自作カレンダーの初期表示月を今月にする
        maintenanceDisplayCalendar =
                Calendar.getInstance(
                        Locale.JAPAN
                );

        maintenanceDisplayCalendar.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        btnPreviousMonth.setOnClickListener(v -> {
            maintenanceDisplayCalendar.add(
                    Calendar.MONTH,
                    -1
            );

            updateMaintenanceCalendar();
        });

        btnNextMonth.setOnClickListener(v -> {
            maintenanceDisplayCalendar.add(
                    Calendar.MONTH,
                    1
            );

            updateMaintenanceCalendar();
        });

        updateMaintenanceCalendar();

        //メンテナンス記録追加ボタンの処理
        btnAddMaintenance.setOnClickListener(v -> {
            showAddMaintenanceDialog();
        });

        //メニューバーを押した時の処理
        btnMaintenanceMenu.setOnClickListener(v -> {
            showMainMenu(btnMaintenanceMenu);
        });

        //Receiverの作成
        trackingReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                //LocationTrackingServiceが位置情報を更新した時の処理
                if (LocationTrackingService.ACTION_LOCATION_UPDATE.equals(action)) {
                    //LocationTrackingServiceから緯度と経度を受け取る
                    double lat = intent.getDoubleExtra(LocationTrackingService.EXTRA_LAT, 0.0);
                    double lon = intent.getDoubleExtra(LocationTrackingService.EXTRA_LON, 0.0);

                    addLiveRoutePoint(lat, lon);//ルートポイントに追加

                    //更新された位置に画面を追従させる
                    if (isRecording && isFollowingCurrentLocation && currentPoint != null) {
                        map.getController().animateTo(currentPoint);
                    }

                    map.invalidate();//レイアウトの更新
                    return;
                }

                //LocationTrackingServiceから受け取れていない位置情報がある場合
                if (LocationTrackingService.ACTION_ROUTE_SNAPSHOT.equals(action)) {
                    //JSON形式でスナップショットを受け取る
                    String routeJsonText = intent.getStringExtra(LocationTrackingService.EXTRA_ROUTE_JSON);

                    //JSONからルートポイントを復元
                    restoreLiveRouteFromJson(routeJsonText);

                    //更新された位置に画面を追従させる
                    if (isRecording && isFollowingCurrentLocation && currentPoint != null) {
                        map.getController().animateTo(currentPoint);
                    }

                    map.invalidate();//更新
                }
            }
        };

        startLocationUpdates();//GPS情報の取得を開始
        changeMode(AppMode.MAP);//マップモードを地図に変更する
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

    @Override
    protected void onStart() {
        super.onStart();

        //画面復帰時に自分の走ったルートを描画する処理
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationTrackingService.ACTION_LOCATION_UPDATE);
        filter.addAction(LocationTrackingService.ACTION_ROUTE_SNAPSHOT);

        ContextCompat.registerReceiver(
                this,
                trackingReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        // 記録中に画面復帰した場合、Serviceから現在までのルートをもらう
        if (isRecording) {
            requestRouteSnapshotFromService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        //バックグラウンドに回した時にレシーバーを解除する
        try {
            unregisterReceiver(trackingReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startLocationUpdates() {
        // 権限確認
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

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
                // MainActivity側は現在地表示だけを担当する

                String provider = location.getProvider();
                long now = System.currentTimeMillis();

                // GPSが来たら時刻を保存
                if (LocationManager.GPS_PROVIDER.equals(provider)) {
                    lastGpsLocationTime = now;
                }

                // GPSが最近来ているならNETWORKは無視
                if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
                    if (now - lastGpsLocationTime < 10000) {
                        return;
                    }

                    // 現在地表示用なので少しゆるめ
                    if (location.hasAccuracy() && location.getAccuracy() > 100) {
                        return;
                    }
                }

                // 精度が極端に悪い位置は表示にも使わない
                if (location.hasAccuracy() && location.getAccuracy() > 100) {
                    return;
                }

                double lat = location.getLatitude();
                double lon = location.getLongitude();

                currentPoint = new GeoPoint(lat, lon);

                // 記録中に現在地追従したい場合
                if (isRecording && isFollowingCurrentLocation) {
                    map.getController().animateTo(currentPoint);
                }

                // 現在地マーカーの位置を更新
                if (currentMarker == null) {
                    currentMarker = new Marker(map);
                    currentMarker.setTitle("現在地");
                    map.getOverlays().add(currentMarker);
                }

                //現在地にピンを指す
                currentMarker.setPosition(currentPoint);
                currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                map.invalidate();

                Log.d("GPS_DISPLAY",
                        "provider=" + provider +
                                ", lat=" + lat +
                                ", lon=" + lon +
                                ", accuracy=" + location.getAccuracy()
                );
            }
        };
        //GPS位置情報の更新リクエスト(5mまたは3sおきに更新要求)
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                3000,
                5,
                listener
        );
        //ネットワーク位置情報の更新リクエスト(5mまたは3sおきに更新要求)
        locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                3000,
                5,
                listener
        );
    }

    //権限がない場合の処理
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
                averageSpeed = totalDistance / elapsedSec; // ここではm/sであることに注意されたし
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

        } catch (Exception e) {//保存失敗時
            e.printStackTrace();
            Toast.makeText(this, "保存に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    private void changeMode(AppMode mode) {
        currentMode = mode;//モードを変更

        //全モードのレイアウトを非表示にする
        mapLayout.setVisibility(View.GONE);
        historyLayout.setVisibility(View.GONE);
        historyDetailLayout.setVisibility(View.GONE);
        statisticsLayout.setVisibility(View.GONE);
        maintenanceLayout.setVisibility(View.GONE);

        if (mode == AppMode.HISTORY) {//履歴モードの場合
            historyLayout.setVisibility(View.VISIBLE);//履歴用のレイアウトを表示
            isRecording = false;//記録中なら記録を終了する
            loadHistoryList();//走行履歴のリストを表示
            return;
        }

        if (mode == AppMode.HISTORY_DETAIL) {//走行ルートの場合
            historyDetailLayout.setVisibility(View.VISIBLE);//ルート表示用のレイアウトを表示
            isRecording = false;//記録中なら記録を終了する
            //詳細パネルを閉じた状態に
            routeDetailPanel.setTranslationY(dp(390));
            detailPanelOpen = false;
            return;
        }

        if (mode == AppMode.STATISTICS) {
            statisticsLayout.setVisibility(View.VISIBLE);//統計モードのレイアウトを表示
            loadStatistics();//統計データの読み取り
            return;
        }

        if (mode == AppMode.MAINTENANCE) {
            maintenanceLayout.setVisibility(
                    View.VISIBLE
            );//メンテナンスのレイアウトを表示

            //選択日がない場合、今の日付にする
            if (selectedMaintenanceDateKey == null) {
                Date today = new Date();

                selectedMaintenanceDateKey =
                        new SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.JAPAN
                        ).format(today);

                maintenanceSelectedDate.setText(
                        "選択日: "
                                + new SimpleDateFormat(
                                "yyyy年M月d日",
                                Locale.JAPAN
                        ).format(today)
                );
            }

            loadMaintenanceList(
                    selectedMaintenanceDateKey
            );//メンテナンスリストを読み取り

            updateMaintenanceCalendar();//カレンダーの更新

            return;
        }

        //これ以降はすべてMAPレイアウト状に表示するモードである
        mapLayout.setVisibility(View.VISIBLE);

        if (mode == AppMode.MAP) {//通常モードの場合
            titleBar.setText("自転車安全マップ");//タイトルバーの表記を変更
            topBar.setBackgroundColor(Color.rgb(67, 160, 71));//トップバーの色を変更
            recordPanel.setVisibility(View.VISIBLE);//記録と保存のボタンを表示
            roadEditPanel.setVisibility(View.GONE);//色分け用のボタンを非表示に
        } else if (mode == AppMode.EDIT_ROAD) {//色分けモードの場合
            titleBar.setText("色分けモード");//タイトルバーの表記を変更
            topBar.setBackgroundColor(Color.rgb(70, 170, 220));//トップバーの色を変更
            recordPanel.setVisibility(View.GONE);//記録用のボタンを非表示に
            roadEditPanel.setVisibility(View.VISIBLE);//色分け用のボタンを表示
            isRecording = false;
        } else if (mode == AppMode.RECORDING) {//記録中の場合
            titleBar.setText("記録中");//タイトルバーの表記を変更
            topBar.setBackgroundColor(Color.rgb(67, 160, 71));//トップバーの色を変更
            recordPanel.setVisibility(View.VISIBLE);//記録と保存のボタンを表示
            roadEditPanel.setVisibility(View.GONE);//色分け用のボタンを非表示に
        }
    }

    private void loadHistoryList() {
        // 前回表示した履歴を削除
        historyList.removeAllViews();

        File routeDir =
                new File(getFilesDir(), "routes");

        if (!routeDir.exists()) {
            showEmptyHistoryMessage(
                    "保存された走行履歴はありません"
            );
            return;
        }

        File[] files =
                routeDir.listFiles();

        if (files == null || files.length == 0) {
            showEmptyHistoryMessage(
                    "保存された走行履歴はありません"
            );
            return;
        }

        /*
         * JSONファイルを読み込み、
         * HistoryItemの一覧に変換する
         */
        ArrayList<HistoryItem> historyItems =
                new ArrayList<>();

        for (File file : files) {
            if (!file.getName().endsWith(".json")) {
                continue;
            }

            try {
                String jsonText =
                        readTextFile(file);

                JSONObject json =
                        new JSONObject(jsonText);

                long start =
                        json.optLong(
                                "startTime",
                                0
                        );

                long end =
                        json.optLong(
                                "endTime",
                                0
                        );

                double distance =
                        json.optDouble(
                                "totalDistance",
                                0.0
                        );

                historyItems.add(
                        new HistoryItem(
                                file,
                                start,
                                end,
                                distance
                        )
                );

            } catch (Exception e) {
                Log.e(
                        "HISTORY",
                        "履歴ファイルの読み込みに失敗: "
                                + file.getName(),
                        e
                );
            }
        }

        /*
         * 選択中の期間フィルターを取得
         */
        String selectedPeriod = "すべての期間";

        //スピナーの選択している時期がある場合、それを選択時期とする
        if (spinnerHistoryPeriod != null
                && spinnerHistoryPeriod.getSelectedItem() != null) {

            selectedPeriod =
                    spinnerHistoryPeriod
                            .getSelectedItem()
                            .toString();
        }

        /*
         * 期間条件に一致するものだけ残す
         */
        ArrayList<HistoryItem> filteredItems =
                new ArrayList<>();

        for (HistoryItem item : historyItems) {
            if (matchesHistoryPeriod(
                    item.startTime,
                    selectedPeriod
            )) {
                filteredItems.add(item);
            }
        }

        /*
         * 選択中の並び順を取得
         */
        String selectedSort = "新しい順";

        if (spinnerHistorySort != null
                && spinnerHistorySort.getSelectedItem() != null) {

            selectedSort =
                    spinnerHistorySort
                            .getSelectedItem()
                            .toString();
        }

        sortHistoryItems(
                filteredItems,
                selectedSort
        );

        if (filteredItems.isEmpty()) {
            showEmptyHistoryMessage(
                    "条件に一致する走行履歴はありません"
            );
            return;
        }

        /*
         * フィルター・並び替え後の履歴を表示
         */
        for (HistoryItem item : filteredItems) {
            File file = item.file;
            long start = item.startTime;
            long end = item.endTime;
            double distance = item.distance;

            String dateText =
                    new SimpleDateFormat(
                            "yyyy/MM/dd HH:mm",
                            Locale.JAPAN
                    ).format(new Date(start));

            long sec =
                    Math.max(
                            0,
                            (end - start) / 1000
                    );

            long hours = sec / 3600;
            long minutes = (sec % 3600) / 60;
            long remainSec = sec % 60;

            String distanceText =
                    String.format(
                            Locale.JAPAN,
                            "%.2f km",
                            distance / 1000.0
                    );

            String timeText;

            if (hours > 0) {
                timeText =
                        String.format(
                                Locale.JAPAN,
                                "%d:%02d:%02d",
                                hours,
                                minutes,
                                remainSec
                        );
            } else {
                timeText =
                        String.format(
                                Locale.JAPAN,
                                "%02d:%02d",
                                minutes,
                                remainSec
                        );
            }

            // 履歴1件分の横並びレイアウト
            LinearLayout rowLayout =
                    new LinearLayout(this);

            rowLayout.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dpInt(64)
                    );

            rowParams.setMargins(
                    0,
                    dpInt(8),
                    0,
                    0
            );

            rowLayout.setLayoutParams(rowParams);

            // 左側：履歴情報
            TextView historyView =
                    new TextView(this);

            historyView.setText(
                    dateText + "\n"
                            + distanceText + "\n"
                            + timeText
            );

            historyView.setTextSize(14);
            historyView.setGravity(
                    android.view.Gravity.CENTER
            );
            historyView.setTextColor(Color.BLACK);
            historyView.setBackgroundColor(
                    Color.rgb(220, 220, 220)
            );
            historyView.setPadding(0, 0, 0, 0);
            historyView.setIncludeFontPadding(false);

            LinearLayout.LayoutParams historyParams =
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1.0f
                    );

            historyView.setLayoutParams(
                    historyParams
            );

            // 押したら履歴詳細を表示
            historyView.setOnClickListener(v -> {
                loadRouteOnHistoryMap(file);
                changeMode(AppMode.HISTORY_DETAIL);
            });

            // 右側：メニューボタン
            TextView menuButton =
                    new TextView(this);

            menuButton.setText("︙");
            menuButton.setTextSize(28);
            menuButton.setGravity(
                    android.view.Gravity.CENTER
            );
            menuButton.setTextColor(Color.BLACK);
            menuButton.setBackgroundColor(
                    Color.rgb(220, 220, 220)
            );
            menuButton.setPadding(0, 0, 0, 0);
            menuButton.setIncludeFontPadding(false);

            LinearLayout.LayoutParams menuParams =
                    new LinearLayout.LayoutParams(
                            dpInt(48),
                            LinearLayout.LayoutParams.MATCH_PARENT
                    );

            menuParams.setMargins(
                    dpInt(8),
                    0,
                    0,
                    0
            );

            menuButton.setLayoutParams(
                    menuParams
            );

            menuButton.setOnClickListener(v -> {
                showHistoryPopupMenu(
                        menuButton,
                        file
                );
            });

            rowLayout.addView(historyView);
            rowLayout.addView(menuButton);

            historyList.addView(rowLayout);
        }
    }

    /**
     * 指定した履歴が選択中の期間に含まれるかを判定
     */
    private boolean matchesHistoryPeriod(
            long startTime,
            String selectedPeriod
    ) {
        if (selectedPeriod.equals("すべての期間")) {
            return true;
        }

        Calendar rideCalendar =
                Calendar.getInstance(
                        Locale.JAPAN
                );

        rideCalendar.setTimeInMillis(
                startTime
        );

        Calendar nowCalendar =
                Calendar.getInstance(
                        Locale.JAPAN
                );

        if (selectedPeriod.equals("今年")) {
            return rideCalendar.get(Calendar.YEAR)
                    == nowCalendar.get(Calendar.YEAR);
        }

        if (selectedPeriod.equals("今月")) {
            return rideCalendar.get(Calendar.YEAR)
                    == nowCalendar.get(Calendar.YEAR)
                    && rideCalendar.get(Calendar.MONTH)
                    == nowCalendar.get(Calendar.MONTH);
        }

        if (selectedPeriod.equals("今週")) {
            Calendar weekStart =
                    Calendar.getInstance(
                            Locale.JAPAN
                    );

            int dayOfWeek =
                    weekStart.get(
                            Calendar.DAY_OF_WEEK
                    );

            // 月曜日から何日経過しているか
            int daysFromMonday =
                    (dayOfWeek + 5) % 7;

            weekStart.add(
                    Calendar.DAY_OF_MONTH,
                    -daysFromMonday
            );

            weekStart.set(
                    Calendar.HOUR_OF_DAY,
                    0
            );
            weekStart.set(
                    Calendar.MINUTE,
                    0
            );
            weekStart.set(
                    Calendar.SECOND,
                    0
            );
            weekStart.set(
                    Calendar.MILLISECOND,
                    0
            );

            Calendar weekEnd =
                    (Calendar) weekStart.clone();

            weekEnd.add(
                    Calendar.DAY_OF_MONTH,
                    7
            );

            return startTime
                    >= weekStart.getTimeInMillis()
                    && startTime
                    < weekEnd.getTimeInMillis();
        }

        return true;
    }

    /**
     * 選択された方法で履歴を並び替える
     */
    private void sortHistoryItems(
            ArrayList<HistoryItem> items,
            String selectedSort
    ) {
        if (selectedSort.equals("古い順")) {
            Collections.sort(
                    items,
                    (item1, item2) ->
                            Long.compare(
                                    item1.startTime,
                                    item2.startTime
                            )
            );

        } else if (selectedSort.equals("距離が長い順")) {
            Collections.sort(
                    items,
                    (item1, item2) ->
                            Double.compare(
                                    item2.distance,
                                    item1.distance
                            )
            );

        } else if (selectedSort.equals("距離が短い順")) {
            Collections.sort(
                    items,
                    (item1, item2) ->
                            Double.compare(
                                    item1.distance,
                                    item2.distance
                            )
            );

        } else {
            // 初期値：新しい順
            Collections.sort(
                    items,
                    (item1, item2) ->
                            Long.compare(
                                    item2.startTime,
                                    item1.startTime
                            )
            );
        }
    }

    /**
     * 履歴がない場合のメッセージを表示
     */
    private void showEmptyHistoryMessage(
            String message
    ) {
        TextView emptyText =
                new TextView(this);

        emptyText.setText(message);
        emptyText.setTextSize(18);
        emptyText.setTextColor(Color.DKGRAY);

        emptyText.setPadding(
                dpInt(12),
                dpInt(24),
                dpInt(12),
                dpInt(24)
        );

        historyList.addView(emptyText);
    }

    //ファイルを読み込む関数
    private String readTextFile(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);//ファイルの読み込み用のストリーム

        byte[] data = new byte[(int) file.length()];//読み込んだ値を入れる
        fis.read(data);//ファイル読み込み
        fis.close();//ファイルを閉じる

        return new String(data, StandardCharsets.UTF_8);//読み込んだデータを文字データとして返す。(UTF-8でエンコード)
    }

    //履歴用に別のマップを使う前の関数、地図上に走行履歴を表示する。
    private void loadRouteOnMap(File file) {
        try {
            String jsonText = readTextFile(file);//ファイルデータの取得
            JSONObject json = new JSONObject(jsonText);//JSONオブジェクトを生成(先ほど読みこんんだデータで)
            JSONArray pointsArray = json.getJSONArray("points");//JSON内のpointsという配列を読み込みArrayに

            ArrayList<GeoPoint> geoPoints = new ArrayList<>();//GeoPoint用の配列(後々線を引くために使用する)

            //pointsArray内のデータからgeoPointsにデータを入れる
            for (int i = 0; i < pointsArray.length(); i++) {
                JSONObject pointJson = pointsArray.getJSONObject(i);

                double lat = pointJson.getDouble("lat");
                double lon = pointJson.getDouble("lon");

                geoPoints.add(new GeoPoint(lat, lon));
            }

            routeLine.setPoints(geoPoints);//線を引くための点を追加

            if (!geoPoints.isEmpty()) {//点がゼロ個でない場合
                map.getController().animateTo(geoPoints.get(0));//地図の中心を最初の点に移動
                map.getController().setZoom(18.0);//縮尺を変更
            }

            map.invalidate();//線を可視化する

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "履歴の読み込みに失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadRouteOnHistoryMap(File file) {
        try {
            String jsonText = readTextFile(file);//ファイルデータの取得
            JSONObject json = new JSONObject(jsonText);//JSONオブジェクトを生成(先ほど読みこんんだデータで)
            JSONArray pointsArray = json.getJSONArray("points");//JSON内のpointsという配列を読み込みArrayに

            //初期化
            long startTime = json.getLong("startTime");//記録開始時間
            long endTime = json.getLong("endTime");//記録終了時刻
            double totalDistance = json.getDouble("totalDistance");//走行距離
            double averageSpeed = json.getDouble("averageSpeed");//平均速度

            long elapsedSec = (endTime - startTime) / 1000;//記録時間

            //記録日の表示
            detailDate.setText(new SimpleDateFormat(
                    "yyyy/MM/dd HH:mm",
                    Locale.JAPAN
            ).format(new Date(startTime)));

            detailRouteName.setText("走行ルート");

            //走行距離の表示
            detailDistance.setText(String.format(
                    Locale.JAPAN,
                    "走行距離: %.2f km",
                    totalDistance / 1000.0
            ));

            //走行時間の表示
            detailTime.setText(String.format(
                    Locale.JAPAN,
                    "走行時間: %d分%02d秒",
                    elapsedSec / 60,
                    elapsedSec % 60
            ));

            //平均速度の表示
            detailAverageSpeed.setText(String.format(
                    Locale.JAPAN,
                    "平均速度: %.1f km/h",
                    averageSpeed * 3.6
            ));

            //詳細データの宣言
            double maxGpsSpeed = 0.0;//最大GPS速度
            double maxSectionSpeed = 0.0;//最大区間平均速度

            double movingTime = 0.0;//移動時間
            double stopTime = 0.0;//停止時間
            double longestStopTime = 0.0;//最長停止時間
            double currentStopTime = 0.0;//停止時間計算用
            int stopCount = 0;//停止回数
            boolean wasStopping = false;//止まっているかどうかを示すBoolean

            double speed0to5Time = 0.0;//0~5km/hの時間
            double speed5to10Time = 0.0;//5~10km/h
            double speed10to15Time = 0.0;//10~15km/h
            double speed15to20Time = 0.0;//15~20km/h
            double speed20OverTime = 0.0;//20km/h~

            ArrayList<GeoPoint> geoPoints = new ArrayList<>();//GeoPoint用の配列(後々線を引くために使用する)

            for (int i = 0; i < pointsArray.length(); i++) {
                JSONObject now = pointsArray.getJSONObject(i);

                double lat = now.getDouble("lat");
                double lon = now.getDouble("lon");
                geoPoints.add(new GeoPoint(lat, lon));//GeoPointに変換

                //GPS速度関連
                double gpsSpeed = now.getDouble("speed");
                if (gpsSpeed > maxGpsSpeed) {
                    maxGpsSpeed = gpsSpeed;
                }

                if (i == 0) {
                    continue;
                }

                //ひとつ前のデータを取得
                JSONObject prev = pointsArray.getJSONObject(i - 1);

                double prevDistance = prev.getDouble("distance");
                double nowDistance = now.getDouble("distance");

                long prevTime = prev.getLong("time");
                long nowTime = now.getLong("time");

                double diffDistance = nowDistance - prevDistance;//一個前からの移動距離
                double diffTime = (nowTime - prevTime) / 1000.0;//一個前からどのぐらいの時間がたったか

                if (diffTime <= 0) {
                    continue;
                }

                double sectionSpeed = diffDistance / diffTime; // m/s
                double sectionSpeedKmh = sectionSpeed * 3.6;//時速に変換

                if (sectionSpeed > maxSectionSpeed) {//最大速度を求める
                    maxSectionSpeed = sectionSpeed;
                }

                // 2km/h未満を停止扱い
                if (sectionSpeedKmh < 2.0) {
                    stopTime += diffTime;
                    currentStopTime += diffTime;

                    if (!wasStopping) {//止まったタイミングで
                        stopCount++;//停止回数を＋１
                        wasStopping = true;//停止中にする
                    }

                    //最大停止時間の更新
                    if (currentStopTime > longestStopTime) {
                        longestStopTime = currentStopTime;
                    }

                } else {//停止していないの時の処理
                    movingTime += diffTime;//移動時間を加算
                    currentStopTime = 0.0;//停止時間をゼロに
                    wasStopping = false;//移動中に

                    //各速度の時間を計算
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

            //平均速度
            double movingAverageSpeed = 0.0;
            if (movingTime > 0) {
                movingAverageSpeed = totalDistance / movingTime; // m/s
            }

            //移動停止の割合
            double movingRatio = 0.0;
            double stopRatio = 0.0;
            if (elapsedSec > 0) {
                movingRatio = movingTime / elapsedSec * 100.0;
                stopRatio = stopTime / elapsedSec * 100.0;
            }

            //移動時間
            double totalMovingTimeForSpeed = movingTime;
            if (totalMovingTimeForSpeed <= 0) {
                totalMovingTimeForSpeed = 1.0;
            }

            //詳細データ表示欄を入力
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

            // 現在表示中の履歴データとして保存
            currentHistoryPointsArray = pointsArray;
            currentHistoryGeoPoints = new ArrayList<>(geoPoints);

            // 速度色分けON/OFFに応じて表示
            updateHistoryRouteDisplay();

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

    //dpに値を変換する
    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    //整数dpに値を変換する
    private int dpInt(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    //選ばれているラジオボタンに応じて区分を設定する関数
    private RoadType getSelectedRoadType() {
        int checkedId = radioRoadType.getCheckedRadioButtonId();

        if (checkedId == R.id.radioSidewalk) {
            return RoadType.SIDEWALK;
        } else if (checkedId == R.id.radioRoadway) {
            return RoadType.ROADWAY;
        } else {
            return RoadType.CAUTION;
        }
    }

    //区分に応じて色を返す関数
    private int getColorByRoadType(RoadType type) {
        if (type == RoadType.SIDEWALK) {
            return Color.BLUE;
        } else if (type == RoadType.ROADWAY) {
            return Color.RED;
        } else {
            return Color.rgb(255, 140, 0); // 注意区間：オレンジ
        }
    }

    //色分けデータの保存用関数
    private void saveRoadSegmentsToJson() {
        try {
            JSONObject rootJson = new JSONObject();//からのJSONオブジェクトを作成
            JSONArray segmentsArray = new JSONArray();//からの配列を作成

            for (RoadSegment segment : roadSegments) {//記録したロードセグメント毎に実行
                JSONObject segmentJson = new JSONObject();//からのオブジェクトを作成

                segmentJson.put("type", segment.type.name());//JSONオブジェクト内に「"type":segment.type.name()」を入れる
                segmentJson.put("memo", segment.memo);//JSONオブジェクト内に「"memo":segment.memo」を入れる

                JSONArray pointsArray = new JSONArray();//からの配列を作成
                //配列にRoadSegmentのpointsを入れる
                for (GeoPoint p : segment.points) {
                    JSONObject pointJson = new JSONObject();
                    pointJson.put("lat", p.getLatitude());
                    pointJson.put("lon", p.getLongitude());
                    pointsArray.put(pointJson);
                }

                segmentJson.put("points", pointsArray);//segmentJsonというオブジェクトにpointsの入った配列を置く
                segmentsArray.put(segmentJson);//segmentArrayにsegmentJsonを置く
            }

            rootJson.put("segments", segmentsArray);//rootJsonにsegmentsという名前でsegmentArrayを置く

            //JSONファイルとして保存する処理
            File file = new File(getFilesDir(), "roads.json");

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(rootJson.toString(4).getBytes(StandardCharsets.UTF_8));
            fos.close();

            Log.d("SAVE_ROADS", file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "色分け道路の保存に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    //色分け用データの読み込み関数
    private void loadRoadSegmentsFromJson() {
        try {
            File file = new File(getFilesDir(), "roads.json");//ファイル名roads.jsonを探す

            if (!file.exists()) {//ないなら終了
                return;
            }

            //JSONファイルを読み込み
            String jsonText = readTextFile(file);
            JSONObject rootJson = new JSONObject(jsonText);
            JSONArray segmentsArray = rootJson.getJSONArray("segments");

            // 既存の色分け線を一度消す
            for (Polyline line : roadLines) {
                map.getOverlays().remove(line);
            }

            roadLines.clear();
            roadSegments.clear();

            //各区分のデータを取得
            for (int i = 0; i < segmentsArray.length(); i++) {
                JSONObject segmentJson = segmentsArray.getJSONObject(i);

                String typeText = segmentJson.getString("type");
                RoadType type = RoadType.valueOf(typeText);

                RoadSegment segment = new RoadSegment(type);
                segment.memo = segmentJson.optString("memo", "");

                JSONArray pointsArray = segmentJson.getJSONArray("points");

                //線を引くためのデータ(点)を取得
                for (int j = 0; j < pointsArray.length(); j++) {
                    JSONObject pointJson = pointsArray.getJSONObject(j);

                    double lat = pointJson.getDouble("lat");
                    double lon = pointJson.getDouble("lon");

                    segment.points.add(new GeoPoint(lat, lon));
                }

                //区分を追加
                roadSegments.add(segment);
                //線を描画する処理
                Polyline line = new Polyline();
                line.setPoints(new ArrayList<>(segment.points));
                line.setColor(getColorByRoadType(segment.type));//色は区分によって変える
                line.setWidth(10.0f);

                map.getOverlays().add(line);
                roadLines.add(line);
            }

            // タップ判定用Overlayを一番上に戻す
            if (mapEventsOverlay != null) {
                map.getOverlays().remove(mapEventsOverlay);
                map.getOverlays().add(mapEventsOverlay);
            }

            map.invalidate();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "色分け道路の読み込みに失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    //道路区分選択の関数
    private void selectRoadSegment(int index) {
        clearSelectedRoadSegment();//前の選択を消す

        selectedRoadIndex = index;//選択したものに変化させる

        //選択したRoadSegmentとそれによって引かれる線を取得
        RoadSegment selectedSegment = roadSegments.get(index);
        Polyline selectedLine = roadLines.get(index);

        // 選択中だと分かるように太くする
        selectedLine.setWidth(18.0f);

        // ラジオボタンを選択中の線の種類に合わせる
        if (selectedSegment.type == RoadType.SIDEWALK) {
            radioRoadType.check(R.id.radioSidewalk);
        } else if (selectedSegment.type == RoadType.ROADWAY) {
            radioRoadType.check(R.id.radioRoadway);
        } else if (selectedSegment.type == RoadType.CAUTION) {
            radioRoadType.check(R.id.radioCaution);
        }

        // 選択中パネルを表示
        selectedRoadPanel.setVisibility(View.VISIBLE);

        map.invalidate();
    }

    //選択解除の関数
    private void clearSelectedRoadSegment() {
        //線の太さを元に戻す
        if (selectedRoadIndex >= 0 && selectedRoadIndex < roadLines.size()) {
            Polyline oldLine = roadLines.get(selectedRoadIndex);
            oldLine.setWidth(10.0f);
        }
        //インデックスを-1(何も選択していない状態)に
        selectedRoadIndex = -1;
        //選択中の専用パネルを非表示に
        if (selectedRoadPanel != null) {
            selectedRoadPanel.setVisibility(View.GONE);
        }

        map.invalidate();
    }

    //タップ位置に一番近い線を探す関数
    private int findNearestRoadSegmentIndex(GeoPoint tapPoint) {
        int nearestIndex = -1;//選択をリセット
        double nearestDistance = Double.MAX_VALUE;

        Point tapScreenPoint = new Point();//触った場所の座標を取得
        map.getProjection().toPixels(tapPoint, tapScreenPoint);

        for (int i = 0; i < roadSegments.size(); i++) {
            RoadSegment segment = roadSegments.get(i);
            if (segment.points.size() < 2) {//とってきたRoadSegmentが点だった時は無視する
                continue;
            }

            //RoadSegmentに含まれるすべての線分と触った位置との距離を計算
            for (int j = 0; j < segment.points.size() - 1; j++) {

                Point p1 = new Point();
                Point p2 = new Point();

                //セグメントのj,j+1番目の座標(緯度経度)を画面のピクセルの座標に変換
                map.getProjection().toPixels(segment.points.get(j), p1);
                map.getProjection().toPixels(segment.points.get(j + 1), p2);

                //p1、p2によってできる線分とタップした位置との画面上の距離を計算
                double distance = distancePointToSegment(
                        tapScreenPoint.x,
                        tapScreenPoint.y,
                        p1.x,
                        p1.y,
                        p2.x,
                        p2.y
                );

                //最も近いものを選ぶ
                if (distance < nearestDistance) {
                    nearestDistance = distance;//最短距離の更新
                    nearestIndex = i;//最短距離が更新されたらその区間を候補として持つ
                }
            }
        }

        // 画面上でこの距離以内なら選択
        double threshold = 40.0;

        if (nearestDistance <= threshold) {
            return nearestIndex;//最も近かった色分けの区分のインデックスを返す
        }

        return -1;//見つからなかったら-1を返す
    }

    //点と線分の距離を求める関数
    private double distancePointToSegment(
            double px,
            double py,
            double x1,
            double y1,
            double x2,
            double y2
    ) {
        double dx = x2 - x1;
        double dy = y2 - y1;

        //二点の位置が重なっている場合、タップした点と重なった点の距離を計算
        if (dx == 0 && dy == 0) {
            double diffX = px - x1;
            double diffY = py - y1;
            return Math.sqrt(diffX * diffX + diffY * diffY);
        }

        /**
         * 線分で最も(px,py)に近い点がその線分のどのあたりにあるかを表すtを求める式
         * (px - x1) * dx + (py - y1) * dy : 内積を使って、点Pを線の方向に投影する
         * ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy) : 投影したものを線分上での割合に変換する
         */
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);

        if (t < 0) {
            t = 0;
        } else if (t > 1) {
            t = 1;
        }

        double nearestX = x1 + t * dx;
        double nearestY = y1 + t * dy;

        double diffX = px - nearestX;
        double diffY = py - nearestY;

        return Math.sqrt(diffX * diffX + diffY * diffY);
    }

    //コメントを追加する関数
    private void showMemoInputDialogForEditingRoad() {
        EditText editText = new EditText(this);//文字を入力する場所を作成
        editText.setHint("例：道が狭い、車が多い、夜暗い など");//テキストボックスに文字を入れる
        editText.setMinLines(3);
        editText.setSingleLine(false);

        //編集時に、選択した線がメモを持っているのならば、それをテキストボックス内に入れる。
        if (editingRoad != null && editingRoad.memo != null) {
            editText.setText(editingRoad.memo);
        }

        //ダイアログを作成
        new AlertDialog.Builder(this)
                .setTitle("コメントを入力")
                .setMessage("この色分け線にコメントを残せます。")
                .setView(editText)
                .setPositiveButton("保存", (dialog, which) -> {
                    editingRoad.memo = editText.getText().toString();
                    confirmRoadEdit();
                })
                .setNeutralButton("コメントなしで保存", (dialog, which) -> {
                    editingRoad.memo = "";
                    confirmRoadEdit();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    //色分けの保存処理関数
    private void confirmRoadEdit() {
        if (editingRoad == null || editingRoad.points.size() < 2) {
            Toast.makeText(this, "2点以上選択してください", Toast.LENGTH_SHORT).show();
            return;
        }

        editingRoad.type = getSelectedRoadType();

        Polyline fixedLine = new Polyline();
        fixedLine.setPoints(new ArrayList<>(editingRoad.points));
        fixedLine.setColor(getColorByRoadType(editingRoad.type));
        fixedLine.setWidth(10.0f);

        map.getOverlays().add(fixedLine);
        roadLines.add(fixedLine);

        roadSegments.add(editingRoad);

        saveRoadSegmentsToJson();

        lastRoadEndPoint = editingRoad.points.get(editingRoad.points.size() - 1);

        map.getOverlays().remove(mapEventsOverlay);
        map.getOverlays().add(mapEventsOverlay);

        editingRoad = new RoadSegment(getSelectedRoadType());

        if (lastRoadEndPoint != null) {
            editingRoad.points.add(lastRoadEndPoint);
        }

        roadPreviewLine.setPoints(new ArrayList<>());

        clearSelectedRoadSegment();

        map.invalidate();

        Toast.makeText(this, "色分け線を保存しました", Toast.LENGTH_SHORT).show();
    }

    //地図モード時色分けされた線を触るとコメントを表示する処理
    private void showRoadSegmentMemoDialog(int index) {
        if (index < 0 || index >= roadSegments.size()) {
            return;
        }

        RoadSegment segment = roadSegments.get(index);

        String typeText = getRoadTypeText(segment.type);

        String memoText = segment.memo;

        if (memoText == null || memoText.trim().isEmpty()) {
            memoText = "コメントはありません";
        }

        new AlertDialog.Builder(this)
                .setTitle(typeText)
                .setMessage(memoText)
                .setPositiveButton("OK", null)
                .show();
    }

    //道路区分名を日本語で表示する関数
    private String getRoadTypeText(RoadType type) {
        if (type == RoadType.SIDEWALK) {
            return "歩道通行可";
        } else if (type == RoadType.ROADWAY) {
            return "車道推奨";
        } else {
            return "注意区間";
        }
    }

    //メモ編集用関数
    private void showMemoEditDialogForSelectedRoad() {
        if (selectedRoadIndex < 0 || selectedRoadIndex >= roadSegments.size()) {
            return;
        }

        RoadSegment segment = roadSegments.get(selectedRoadIndex);

        EditText editText = new EditText(this);
        editText.setHint("例：道が狭い、車が多い、夜暗い など");
        editText.setMinLines(3);
        editText.setSingleLine(false);

        if (segment.memo != null) {
            editText.setText(segment.memo);
        }

        new AlertDialog.Builder(this)
                .setTitle("コメント編集")
                .setMessage(getRoadTypeText(segment.type) + " のコメントを編集します。")
                .setView(editText)
                .setPositiveButton("保存", (dialog, which) -> {
                    segment.memo = editText.getText().toString();
                    saveRoadSegmentsToJson();
                    Toast.makeText(this, "コメントを保存しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    //ポップアップ表示関数
    private void showHistoryPopupMenu(View anchor, File file) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);

        // 今は削除だけ。後で共有や名前変更もここに追加できる
        popupMenu.getMenu().add("削除");

        //ポップアップの選択時の動作
        popupMenu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();

            if (title.equals("削除")) {
                showDeleteHistoryConfirmDialog(file);
                return true;
            }

            return false;
        });

        popupMenu.show();
    }

    //削除の確認をする関数
    private void showDeleteHistoryConfirmDialog(File file) {
        new AlertDialog.Builder(this)
                .setTitle("履歴を削除")
                .setMessage("この走行履歴を削除しますか？")
                .setPositiveButton("削除", (dialog, which) -> {
                    deleteHistoryFile(file);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    //走行履歴のファイル削除する関数
    private void deleteHistoryFile(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, "履歴ファイルが見つかりません", Toast.LENGTH_SHORT).show();
            loadHistoryList();
            return;
        }

        boolean deleted = file.delete();

        if (deleted) {
            Toast.makeText(this, "履歴を削除しました", Toast.LENGTH_SHORT).show();
            loadHistoryList(); // 一覧を更新
        } else {
            Toast.makeText(this, "削除に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    //履歴速度線を消す関数(履歴表示時にのこらにようにするため)
    private void clearHistorySpeedLines() {
        for (Polyline line : historySpeedLines) {
            historyMap.getOverlays().remove(line);
        }

        historySpeedLines.clear();
    }

    //速度から色を作る関数
    private int getSpeedGradientColor(double speedKmh) {
        if (speedKmh < 0) {//速度の下限
            speedKmh = 0;
        }

        if (speedKmh > 30) {//速度の上限
            speedKmh = 30;
        }

        int color0  = Color.rgb(255, 0, 0);     // 0km/h 赤
        int color5  = Color.rgb(255, 80, 0);    // 5km/h 赤橙
        int color10 = Color.rgb(255, 140, 0);   // 10km/h オレンジ
        int color15 = Color.rgb(255, 220, 0);   // 15km/h 黄色
        int color20 = Color.rgb(180, 255, 0);   // 20km/h 黄緑
        int color25 = Color.rgb(60, 220, 60);   // 25km/h 緑
        int color30 = Color.rgb(0, 160, 0);     // 30km/h 濃い緑

        if (speedKmh <= 5) {
            double ratio = speedKmh / 5.0;
            return interpolateColor(color0, color5, ratio);
        } else if (speedKmh <= 10) {
            double ratio = (speedKmh - 5.0) / 5.0;
            return interpolateColor(color5, color10, ratio);
        } else if (speedKmh <= 15) {
            double ratio = (speedKmh - 10.0) / 5.0;
            return interpolateColor(color10, color15, ratio);
        } else if (speedKmh <= 20) {
            double ratio = (speedKmh - 15.0) / 5.0;
            return interpolateColor(color15, color20, ratio);
        } else if (speedKmh <= 25) {
            double ratio = (speedKmh - 20.0) / 5.0;
            return interpolateColor(color20, color25, ratio);
        } else {
            double ratio = (speedKmh - 25.0) / 5.0;
            return interpolateColor(color25, color30, ratio);
        }
    }

    //色を混ぜる関数
    private int interpolateColor(int startColor, int endColor, double ratio) {
        if (ratio < 0) {
            ratio = 0;
        }

        if (ratio > 1) {
            ratio = 1;
        }

        int startR = Color.red(startColor);
        int startG = Color.green(startColor);
        int startB = Color.blue(startColor);

        int endR = Color.red(endColor);
        int endG = Color.green(endColor);
        int endB = Color.blue(endColor);

        int r = (int) (startR + (endR - startR) * ratio);
        int g = (int) (startG + (endG - startG) * ratio);
        int b = (int) (startB + (endB - startB) * ratio);

        return Color.rgb(r, g, b);
    }

    //履歴ルートを速度色分けで描画する関数
    private void drawSpeedColoredHistoryRoute(JSONArray pointsArray) {
        try {
            clearHistorySpeedLines();

            if (pointsArray.length() < 2) {
                return;
            }

            for (int i = 1; i < pointsArray.length(); i++) {
                JSONObject prev = pointsArray.getJSONObject(i - 1);
                JSONObject now = pointsArray.getJSONObject(i);

                double prevLat = prev.getDouble("lat");
                double prevLon = prev.getDouble("lon");
                double nowLat = now.getDouble("lat");
                double nowLon = now.getDouble("lon");

                double prevDistance = prev.getDouble("distance");
                double nowDistance = now.getDouble("distance");

                long prevTime = prev.getLong("time");
                long nowTime = now.getLong("time");

                double diffDistance = nowDistance - prevDistance;
                double diffTime = (nowTime - prevTime) / 1000.0;

                if (diffTime <= 0) {
                    continue;
                }

                double speedMps = diffDistance / diffTime;
                double speedKmh = speedMps * 3.6;

                int color = getSpeedGradientColor(speedKmh);

                Polyline sectionLine = new Polyline();

                ArrayList<GeoPoint> sectionPoints = new ArrayList<>();
                sectionPoints.add(new GeoPoint(prevLat, prevLon));
                sectionPoints.add(new GeoPoint(nowLat, nowLon));

                sectionLine.setPoints(sectionPoints);
                sectionLine.setColor(color);
                sectionLine.setWidth(10.0f);

                historyMap.getOverlays().add(sectionLine);
                historySpeedLines.add(sectionLine);
            }

            historyMap.invalidate();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "速度色分け表示に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    //速度色分け表示切り替え用の関数
    private void updateHistoryRouteDisplay() {
        if (currentHistoryPointsArray == null || currentHistoryGeoPoints == null) {
            return;
        }

        // 速度色分け線を一度消す
        clearHistorySpeedLines();

        if (isSpeedColorMode) {
            // 青い通常線を消す
            historyRouteLine.setPoints(new ArrayList<>());

            // 速度色分け線を表示
            drawSpeedColoredHistoryRoute(currentHistoryPointsArray);

            if (btnToggleSpeedColor != null) {
                btnToggleSpeedColor.setText("速度色 ON");
            }
        } else {
            // 速度色分け線を消す
            clearHistorySpeedLines();

            // 青い通常線を表示
            historyRouteLine.setPoints(new ArrayList<>(currentHistoryGeoPoints));
            historyRouteLine.setColor(Color.BLUE);
            historyRouteLine.setWidth(8.0f);

            if (btnToggleSpeedColor != null) {
                btnToggleSpeedColor.setText("速度色 OFF");
            }
        }

        historyMap.invalidate();
    }

    //GPSフィルターをかける関数
    private boolean shouldIgnoreLocationForRecording(Location location) {
        // 記録中ではないなら、現在地表示には使いたいので無視しない
        if (!isRecording) {
            return false;
        }

        // 精度が悪すぎる点は無視
        if (location.hasAccuracy() && location.getAccuracy() > RECORDING_MAX_ACCURACY) {
            Log.d("GPS_FILTER", "精度が悪いため無視: accuracy=" + location.getAccuracy());
            return true;
        }

        // 前回地点がない場合は比較できないので使う
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

        // 異常に速い移動はGPSの飛びとして無視
        if (sectionSpeedKmh > MAX_REASONABLE_SPEED_KMH) {
            Log.d("GPS_FILTER", "ワープ判定で無視: speed="
                    + sectionSpeedKmh + " km/h, distance=" + distance);
            return true;
        }

        float gpsSpeed = location.hasSpeed() ? location.getSpeed() : 0.0f;

        // 停止中の小さいGPSブレを無視
        if (gpsSpeed < STOP_JITTER_SPEED && distance < STOP_JITTER_DISTANCE) {
            Log.d("GPS_FILTER", "停止中のブレとして無視: distance="
                    + distance + ", gpsSpeed=" + gpsSpeed);
            return true;
        }

        return false;
    }
    //表示用ルート追加メソッド
    private void addLiveRoutePoint(double lat, double lon) {
        GeoPoint point = new GeoPoint(lat, lon);

        if (!liveRouteGeoPoints.isEmpty()) {
            GeoPoint lastPoint = liveRouteGeoPoints.get(liveRouteGeoPoints.size() - 1);

            float[] result = new float[1];

            Location.distanceBetween(
                    lastPoint.getLatitude(),
                    lastPoint.getLongitude(),
                    lat,
                    lon,
                    result
            );

            // ほぼ同じ点なら重複追加しない
            if (result[0] < 0.5) {
                return;
            }
        }

        currentPoint = point;

        liveRouteGeoPoints.add(point);
        routeLine.setPoints(new ArrayList<>(liveRouteGeoPoints));
    }

    //ルート復元メソッド
    private void restoreLiveRouteFromJson(String routeJsonText) {
        try {
            if (routeJsonText == null || routeJsonText.isEmpty()) {
                return;
            }

            JSONArray pointsArray = new JSONArray(routeJsonText);

            liveRouteGeoPoints.clear();

            for (int i = 0; i < pointsArray.length(); i++) {
                JSONObject pointJson = pointsArray.getJSONObject(i);

                double lat = pointJson.getDouble("lat");
                double lon = pointJson.getDouble("lon");

                GeoPoint point = new GeoPoint(lat, lon);
                liveRouteGeoPoints.add(point);
            }

            routeLine.setPoints(new ArrayList<>(liveRouteGeoPoints));

            if (!liveRouteGeoPoints.isEmpty()) {
                currentPoint = liveRouteGeoPoints.get(liveRouteGeoPoints.size() - 1);
            }

            Log.d("LIVE_ROUTE", "画面復帰時にルート復元 points=" + liveRouteGeoPoints.size());

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "記録中ルートの復元に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    //Serviceへルート一覧を要求するメソッド
    private void requestRouteSnapshotFromService() {
        Intent intent = new Intent(this, LocationTrackingService.class);
        intent.setAction(LocationTrackingService.ACTION_REQUEST_ROUTE);
        startService(intent);
    }

    //ハンバーガーメニューバーの表示メソッド
    private void showMainMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);

        popupMenu.getMenu().add("地図");
        popupMenu.getMenu().add("色分け");
        popupMenu.getMenu().add("履歴");
        popupMenu.getMenu().add("統計");
        popupMenu.getMenu().add("メンテナンス");
        popupMenu.getMenu().add("設定");

        popupMenu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();

            if (title.equals("地図")) {
                changeMode(AppMode.MAP);
                return true;
            }

            if (title.equals("色分け")) {
                changeMode(AppMode.EDIT_ROAD);
                return true;
            }

            if (title.equals("履歴")) {
                changeMode(AppMode.HISTORY);
                return true;
            }

            if (title.equals("統計")) {
                changeMode(AppMode.STATISTICS);
                return true;
            }

            if (title.equals("メンテナンス")) {
                changeMode(AppMode.MAINTENANCE);
                return true;
            }

            if (title.equals("設定")) {
                Toast.makeText(this, "設定画面は今後実装します", Toast.LENGTH_SHORT).show();
                return true;
            }

            return false;
        });

        popupMenu.show();
    }

    //統計データの読み込み関数
    private void loadStatistics() {
        try {
            File file =
                    new File(getFilesDir(), "statistics.json");

            if (!file.exists()) {
                currentStatisticsRoot = null;

                statisticsPeriodLabels.clear();
                statisticsPeriodKeys.clear();

                statisticsPeriodLabels.add("データなし");
                statisticsPeriodKeys.add("");

                ArrayAdapter<String> emptyAdapter =
                        new ArrayAdapter<>(
                                this,
                                android.R.layout.simple_spinner_item,
                                statisticsPeriodLabels
                        );

                emptyAdapter.setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item
                );

                spinnerStatisticsPeriod.setAdapter(emptyAdapter);

                statSelectedPeriod.setText("データなし");

                showEmptyStatistics();
                return;
            }

            String jsonText = readTextFile(file);

            currentStatisticsRoot =
                    new JSONObject(jsonText);

            updateStatisticsPeriodSpinner();

        } catch (Exception e) {
            e.printStackTrace();

            Toast.makeText(
                    this,
                    "統計データの読み込みに失敗しました",
                    Toast.LENGTH_SHORT
            ).show();

            showEmptyStatistics();
        }
    }

    //時間表示用関数
    private String formatStatisticsTime(long totalSec) {
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;

        if (hours > 0) {
            return String.format(
                    Locale.JAPAN,
                    "%d時間%02d分%02d秒",
                    hours,
                    minutes,
                    seconds
            );
        }

        return String.format(
                Locale.JAPAN,
                "%d分%02d秒",
                minutes,
                seconds
        );
    }

    //割合計算用の関数
    private double calculatePercentage(
            double value,
            double total
    ) {
        if (total <= 0.0) {
            return 0.0;
        }

        return value / total * 100.0;
    }

    //選択された種類に応じて期間一覧を作る関数
    private void updateStatisticsPeriodSpinner() {
        if (currentStatisticsRoot == null) {
            showEmptyStatistics();
            return;
        }

        isUpdatingStatisticsSpinner = true;

        statisticsPeriodLabels.clear();
        statisticsPeriodKeys.clear();

        Object selectedItem =
                spinnerStatisticsType.getSelectedItem();

        if (selectedItem == null) {
            showEmptyStatistics();
            return;
        }

        String selectedType =
                selectedItem.toString();

        if (selectedType.equals("全体")) {
            statisticsPeriodLabels.add("全期間");
            statisticsPeriodKeys.add("allTime");

        } else if (selectedType.equals("年")) {
            JSONObject yearlyJson =
                    currentStatisticsRoot.optJSONObject("yearly");

            addYearPeriodItems(yearlyJson);

        } else if (selectedType.equals("月")) {
            JSONObject monthlyJson =
                    currentStatisticsRoot.optJSONObject("monthly");

            addMonthPeriodItems(monthlyJson);

        } else if (selectedType.equals("週")) {
            JSONObject dailyJson =
                    currentStatisticsRoot.optJSONObject("daily");

            addWeekPeriodItems(dailyJson);
        }

        if (statisticsPeriodLabels.isEmpty()) {
            statisticsPeriodLabels.add("データなし");
            statisticsPeriodKeys.add("");
        }

        ArrayAdapter<String> periodAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.spinner_item,
                        statisticsPeriodLabels
                );

        periodAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item
        );

        spinnerStatisticsPeriod.setAdapter(periodAdapter);

        isUpdatingStatisticsSpinner = false;

        displaySelectedStatistics();
    }

    //年一覧を作る関数
    private void addYearPeriodItems(JSONObject yearlyJson) {
        if (yearlyJson == null) {
            return;
        }

        ArrayList<String> keys =
                getSortedJsonKeys(yearlyJson);

        for (String key : keys) {
            statisticsPeriodKeys.add(key);
            statisticsPeriodLabels.add(key + "年");
        }
    }

    //月一覧を作る関数
    private void addMonthPeriodItems(JSONObject monthlyJson) {
        if (monthlyJson == null) {
            return;
        }

        ArrayList<String> keys =
                getSortedJsonKeys(monthlyJson);

        for (String key : keys) {
            statisticsPeriodKeys.add(key);

            try {
                Date date =
                        new SimpleDateFormat(
                                "yyyy-MM",
                                Locale.JAPAN
                        ).parse(key);

                String label =
                        new SimpleDateFormat(
                                "yyyy年M月",
                                Locale.JAPAN
                        ).format(date);

                statisticsPeriodLabels.add(label);

            } catch (Exception e) {
                statisticsPeriodLabels.add(key);
            }
        }
    }

    //JSONキーを新しい順に取得する関数
    private ArrayList<String> getSortedJsonKeys(
            JSONObject jsonObject
    ) {
        ArrayList<String> keys = new ArrayList<>();

        if (jsonObject == null) {
            return keys;
        }

        Iterator<String> iterator =
                jsonObject.keys();

        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }

        // yyyy、yyyy-MM、yyyy-MM-ddなら文字列の降順で新しい順になる
        Collections.sort(
                keys,
                Collections.reverseOrder()
        );

        return keys;
    }

    //週一覧を作成する関数
    private void addWeekPeriodItems(JSONObject dailyJson) {
        if (dailyJson == null) {
            return;
        }

        ArrayList<String> dayKeys =
                getSortedJsonKeys(dailyJson);

        ArrayList<String> weekStartKeys =
                new ArrayList<>();

        for (String dayKey : dayKeys) {
            String weekStartKey =
                    getWeekStartKey(dayKey);

            if (weekStartKey == null) {
                continue;
            }

            if (!weekStartKeys.contains(weekStartKey)) {
                weekStartKeys.add(weekStartKey);
            }
        }

        Collections.sort(
                weekStartKeys,
                Collections.reverseOrder()
        );

        for (String weekStartKey : weekStartKeys) {
            statisticsPeriodKeys.add(weekStartKey);

            String weekEndKey =
                    addDaysToDateKey(
                            weekStartKey,
                            6
                    );

            statisticsPeriodLabels.add(
                    formatDateKeyForLabel(weekStartKey)
                            + " ～ "
                            + formatDateKeyForLabel(weekEndKey)
            );
        }
    }

    private String getWeekStartKey(String dayKey) {
        try {
            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.JAPAN
                    );

            Date date = format.parse(dayKey);

            Calendar calendar =
                    Calendar.getInstance(Locale.JAPAN);

            calendar.setTime(date);

            // 月曜日を週の開始にする
            int dayOfWeek =
                    calendar.get(Calendar.DAY_OF_WEEK);

            int daysFromMonday =
                    (dayOfWeek + 5) % 7;

            calendar.add(
                    Calendar.DAY_OF_MONTH,
                    -daysFromMonday
            );

            return format.format(calendar.getTime());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String addDaysToDateKey(
            String dayKey,
            int days
    ) {
        try {
            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.JAPAN
                    );

            Date date = format.parse(dayKey);

            Calendar calendar =
                    Calendar.getInstance(Locale.JAPAN);

            calendar.setTime(date);

            calendar.add(
                    Calendar.DAY_OF_MONTH,
                    days
            );

            return format.format(calendar.getTime());

        } catch (Exception e) {
            return dayKey;
        }
    }

    private String formatDateKeyForLabel(
            String dayKey
    ) {
        try {
            Date date =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.JAPAN
                    ).parse(dayKey);

            return new SimpleDateFormat(
                    "yyyy/M/d",
                    Locale.JAPAN
            ).format(date);

        } catch (Exception e) {
            return dayKey;
        }
    }

    //選択された期間の統計を表示する関数
    private void displaySelectedStatistics() {
        if (currentStatisticsRoot == null) {
            showEmptyStatistics();
            return;
        }

        int position =
                spinnerStatisticsPeriod
                        .getSelectedItemPosition();

        if (position < 0
                || position >= statisticsPeriodKeys.size()) {
            showEmptyStatistics();
            return;
        }

        String periodKey =
                statisticsPeriodKeys.get(position);

        if (periodKey.isEmpty()) {
            showEmptyStatistics();
            statSelectedPeriod.setText("データなし");
            return;
        }

        Object selectedItem =
                spinnerStatisticsType.getSelectedItem();

        if (selectedItem == null) {
            statSelectedPeriod.setText("データなし");
            showEmptyStatistics();
            return;
        }

        String selectedType =
                selectedItem.toString();

        JSONObject statisticsBlock = null;

        if (selectedType.equals("全体")) {
            statisticsBlock =
                    currentStatisticsRoot
                            .optJSONObject("allTime");

        } else if (selectedType.equals("年")) {
            JSONObject yearlyJson =
                    currentStatisticsRoot
                            .optJSONObject("yearly");

            if (yearlyJson != null) {
                statisticsBlock =
                        yearlyJson.optJSONObject(periodKey);
            }

        } else if (selectedType.equals("月")) {
            JSONObject monthlyJson =
                    currentStatisticsRoot
                            .optJSONObject("monthly");

            if (monthlyJson != null) {
                statisticsBlock =
                        monthlyJson.optJSONObject(periodKey);
            }

        } else if (selectedType.equals("週")) {
            JSONObject dailyJson =
                    currentStatisticsRoot
                            .optJSONObject("daily");

            statisticsBlock =
                    buildWeekStatisticsBlock(
                            dailyJson,
                            periodKey
                    );
        }

        if (statisticsBlock == null) {
            statSelectedPeriod.setText("データなし");
            showEmptyStatistics();
            return;
        }

        statSelectedPeriod.setText(
                statisticsPeriodLabels.get(position)
        );

        showStatisticsBlock(statisticsBlock);
        //グラフの更新
        updateDistanceChart(
                selectedType,
                periodKey
        );
    }

    //一週間分の統計データを計算
    private JSONObject buildWeekStatisticsBlock(
            JSONObject dailyJson,
            String weekStartKey
    ) {
        try {
            JSONObject result =
                    createEmptyStatisticsBlockForDisplay();

            if (dailyJson == null) {
                return result;
            }

            for (int i = 0; i < 7; i++) {
                String dayKey =
                        addDaysToDateKey(
                                weekStartKey,
                                i
                        );

                JSONObject dayBlock =
                        dailyJson.optJSONObject(dayKey);

                if (dayBlock != null) {
                    mergeStatisticsBlocks(
                            result,
                            dayBlock
                    );
                }
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //からの統計用表示ブロックを作成する関数
    private JSONObject createEmptyStatisticsBlockForDisplay()
            throws Exception {

        JSONObject blockJson = new JSONObject();

        JSONObject summaryJson = new JSONObject();
        summaryJson.put("totalRideCount", 0);
        summaryJson.put("totalDistance", 0.0);
        summaryJson.put("totalRideTime", 0);
        summaryJson.put("totalMovingTime", 0.0);
        summaryJson.put("totalStopTime", 0.0);
        summaryJson.put("totalStopCount", 0);

        JSONObject recordsJson = new JSONObject();
        recordsJson.put("maxSingleRideDistance", 0.0);
        recordsJson.put("maxSingleRideTime", 0);
        recordsJson.put("maxAverageSpeed", 0.0);
        recordsJson.put("maxMovingAverageSpeed", 0.0);
        recordsJson.put("maxGpsSpeed", 0.0);
        recordsJson.put("longestStopTime", 0.0);

        JSONObject speedJson = new JSONObject();
        speedJson.put("time0to5", 0.0);
        speedJson.put("time5to10", 0.0);
        speedJson.put("time10to15", 0.0);
        speedJson.put("time15to20", 0.0);
        speedJson.put("time20to25", 0.0);
        speedJson.put("time25to30", 0.0);
        speedJson.put("time30Over", 0.0);

        JSONObject distanceJson = new JSONObject();
        distanceJson.put("ride0to5km", 0);
        distanceJson.put("ride5to10km", 0);
        distanceJson.put("ride10to20km", 0);
        distanceJson.put("ride20to50km", 0);
        distanceJson.put("ride50kmOver", 0);

        blockJson.put("summary", summaryJson);
        blockJson.put("records", recordsJson);
        blockJson.put("speedDistribution", speedJson);
        blockJson.put("distanceDistribution", distanceJson);

        return blockJson;
    }

    //日別ブロックを週間ブロックへ合成する関数
    private void mergeStatisticsBlocks(
            JSONObject target,
            JSONObject source
    ) throws Exception {

        JSONObject targetSummary =
                target.getJSONObject("summary");

        JSONObject sourceSummary =
                source.optJSONObject("summary");

        JSONObject targetRecords =
                target.getJSONObject("records");

        JSONObject sourceRecords =
                source.optJSONObject("records");

        JSONObject targetSpeed =
                target.getJSONObject("speedDistribution");

        JSONObject sourceSpeed =
                source.optJSONObject("speedDistribution");

        JSONObject targetDistance =
                target.getJSONObject("distanceDistribution");

        JSONObject sourceDistance =
                source.optJSONObject("distanceDistribution");

        if (sourceSummary != null) {
            addLongValue(
                    targetSummary,
                    sourceSummary,
                    "totalRideCount"
            );

            addDoubleValue(
                    targetSummary,
                    sourceSummary,
                    "totalDistance"
            );

            addLongValue(
                    targetSummary,
                    sourceSummary,
                    "totalRideTime"
            );

            addDoubleValue(
                    targetSummary,
                    sourceSummary,
                    "totalMovingTime"
            );

            addDoubleValue(
                    targetSummary,
                    sourceSummary,
                    "totalStopTime"
            );

            addLongValue(
                    targetSummary,
                    sourceSummary,
                    "totalStopCount"
            );
        }

        if (sourceRecords != null) {
            setMaximumDouble(
                    targetRecords,
                    sourceRecords,
                    "maxSingleRideDistance"
            );

            setMaximumLong(
                    targetRecords,
                    sourceRecords,
                    "maxSingleRideTime"
            );

            setMaximumDouble(
                    targetRecords,
                    sourceRecords,
                    "maxAverageSpeed"
            );

            setMaximumDouble(
                    targetRecords,
                    sourceRecords,
                    "maxMovingAverageSpeed"
            );

            setMaximumDouble(
                    targetRecords,
                    sourceRecords,
                    "maxGpsSpeed"
            );

            setMaximumDouble(
                    targetRecords,
                    sourceRecords,
                    "longestStopTime"
            );
        }

        if (sourceSpeed != null) {
            String[] speedKeys = {
                    "time0to5",
                    "time5to10",
                    "time10to15",
                    "time15to20",
                    "time20to25",
                    "time25to30",
                    "time30Over"
            };

            for (String key : speedKeys) {
                addDoubleValue(
                        targetSpeed,
                        sourceSpeed,
                        key
                );
            }
        }

        if (sourceDistance != null) {
            String[] distanceKeys = {
                    "ride0to5km",
                    "ride5to10km",
                    "ride10to20km",
                    "ride20to50km",
                    "ride50kmOver"
            };

            for (String key : distanceKeys) {
                addLongValue(
                        targetDistance,
                        sourceDistance,
                        key
                );
            }
        }
    }

    private void addDoubleValue(
            JSONObject target,
            JSONObject source,
            String key
    ) throws Exception {

        target.put(
                key,
                target.optDouble(key, 0.0)
                        + source.optDouble(key, 0.0)
        );
    }

    private void addLongValue(
            JSONObject target,
            JSONObject source,
            String key
    ) throws Exception {

        target.put(
                key,
                target.optLong(key, 0)
                        + source.optLong(key, 0)
        );
    }

    private void setMaximumDouble(
            JSONObject target,
            JSONObject source,
            String key
    ) throws Exception {

        target.put(
                key,
                Math.max(
                        target.optDouble(key, 0.0),
                        source.optDouble(key, 0.0)
                )
        );
    }

    private void setMaximumLong(
            JSONObject target,
            JSONObject source,
            String key
    ) throws Exception {

        target.put(
                key,
                Math.max(
                        target.optLong(key, 0),
                        source.optLong(key, 0)
                )
        );
    }

    private void showStatisticsBlock(
            JSONObject statisticsBlock
    ) {
        try {
            JSONObject summaryJson =
                    statisticsBlock.optJSONObject("summary");

            JSONObject recordsJson =
                    statisticsBlock.optJSONObject("records");

            JSONObject speedDistributionJson =
                    statisticsBlock.optJSONObject(
                            "speedDistribution"
                    );

            JSONObject distanceDistributionJson =
                    statisticsBlock.optJSONObject(
                            "distanceDistribution"
                    );

            if (summaryJson == null
                    || recordsJson == null
                    || speedDistributionJson == null
                    || distanceDistributionJson == null) {

                showEmptyStatistics();
                return;
            }

            int totalRideCount =
                    summaryJson.optInt("totalRideCount", 0);

            double totalDistance =
                    summaryJson.optDouble("totalDistance", 0.0);

            long totalRideTime =
                    summaryJson.optLong("totalRideTime", 0);

            double totalMovingTime =
                    summaryJson.optDouble("totalMovingTime", 0.0);

            double totalStopTime =
                    summaryJson.optDouble("totalStopTime", 0.0);

            double maxSingleRideDistance =
                    recordsJson.optDouble("maxSingleRideDistance", 0.0);

            long maxSingleRideTime =
                    recordsJson.optLong("maxSingleRideTime", 0);

            double maxAverageSpeed =
                    recordsJson.optDouble("maxAverageSpeed", 0.0);

            double maxGpsSpeed =
                    recordsJson.optDouble("maxGpsSpeed", 0.0);

            int totalStopCount =
                    summaryJson.optInt("totalStopCount", 0);

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

            //速度分布
            double time0to5 =
                    speedDistributionJson.optDouble("time0to5", 0.0);

            double time5to10 =
                    speedDistributionJson.optDouble("time5to10", 0.0);

            double time10to15 =
                    speedDistributionJson.optDouble("time10to15", 0.0);

            double time15to20 =
                    speedDistributionJson.optDouble("time15to20", 0.0);

            double time20to25 =
                    speedDistributionJson.optDouble("time20to25", 0.0);

            double time25to30 =
                    speedDistributionJson.optDouble("time25to30", 0.0);

            double time30Over =
                    speedDistributionJson.optDouble("time30Over", 0.0);

            //距離分布
            int ride0to5km =
                    distanceDistributionJson.optInt("ride0to5km", 0);

            int ride5to10km =
                    distanceDistributionJson.optInt("ride5to10km", 0);

            int ride10to20km =
                    distanceDistributionJson.optInt("ride10to20km", 0);

            int ride20to50km =
                    distanceDistributionJson.optInt("ride20to50km", 0);

            int ride50kmOver =
                    distanceDistributionJson.optInt("ride50kmOver", 0);

            //トータルの時間を求める
            double totalSpeedDistributionTime =
                    time0to5
                            + time5to10
                            + time10to15
                            + time15to20
                            + time20to25
                            + time25to30
                            + time30Over;

            statTotalRideCount.setText(
                    "総走行回数: " + totalRideCount + "回"
            );

            statTotalDistance.setText(String.format(
                    Locale.JAPAN,
                    "総走行距離: %.2f km",
                    totalDistance / 1000.0
            ));

            statTotalRideTime.setText(
                    "総走行時間: " + formatStatisticsTime(totalRideTime)
            );

            statTotalMovingTime.setText(
                    "総移動時間: " + formatStatisticsTime((long) totalMovingTime)
            );

            statTotalStopTime.setText(
                    "総停止時間: " + formatStatisticsTime((long) totalStopTime)
            );

            statTotalStopCount.setText(String.format(
                    Locale.JAPAN,
                    "累計停止回数: %d回",
                    totalStopCount
            ));

            statMaxMovingAverageSpeed.setText(String.format(
                    Locale.JAPAN,
                    "最高移動中平均速度: %.1f km/h",
                    maxMovingAverageSpeed * 3.6
            ));

            statLongestStopTime.setText(
                    "最長停止時間: "
                            + formatStatisticsTime((long) longestStopTime)
            );

            statMaxDistance.setText(String.format(
                    Locale.JAPAN,
                    "最長1回走行距離: %.2f km",
                    maxSingleRideDistance / 1000.0
            ));

            statMaxRideTime.setText(
                    "最長1回走行時間: " + formatStatisticsTime(maxSingleRideTime)
            );

            statMaxAverageSpeed.setText(String.format(
                    Locale.JAPAN,
                    "最高平均速度: %.1f km/h",
                    maxAverageSpeed * 3.6
            ));

            statMaxGpsSpeed.setText(String.format(
                    Locale.JAPAN,
                    "最高GPS速度: %.1f km/h",
                    maxGpsSpeed * 3.6
            ));

            //速度分布
            statSpeed0to5.setText(String.format(
                    Locale.JAPAN,
                    "0～5 km/h: %.1f%%（%s）",
                    calculatePercentage(time0to5, totalSpeedDistributionTime),
                    formatStatisticsTime((long) time0to5)
            ));

            statSpeed5to10.setText(String.format(
                    Locale.JAPAN,
                    "5～10 km/h: %.1f%%（%s）",
                    calculatePercentage(time5to10, totalSpeedDistributionTime),
                    formatStatisticsTime((long) time5to10)
            ));

            statSpeed10to15.setText(String.format(
                    Locale.JAPAN,
                    "10～15 km/h: %.1f%%（%s）",
                    calculatePercentage(time10to15, totalSpeedDistributionTime),
                    formatStatisticsTime((long) time10to15)
            ));

            statSpeed15to20.setText(String.format(
                    Locale.JAPAN,
                    "15～20 km/h: %.1f%%（%s）",
                    calculatePercentage(time15to20, totalSpeedDistributionTime),
                    formatStatisticsTime((long) time15to20)
            ));

            statSpeed20to25.setText(String.format(
                    Locale.JAPAN,
                    "20～25 km/h: %.1f%%（%s）",
                    calculatePercentage(time20to25, totalSpeedDistributionTime),
                    formatStatisticsTime((long) time20to25)
            ));

            statSpeed25to30.setText(String.format(
                    Locale.JAPAN,
                    "25～30 km/h: %.1f%%（%s）",
                    calculatePercentage(time25to30, totalSpeedDistributionTime),
                    formatStatisticsTime((long) time25to30)
            ));

            statSpeed30Over.setText(String.format(
                    Locale.JAPAN,
                    "30 km/h以上: %.1f%%（%s）",
                    calculatePercentage(time30Over, totalSpeedDistributionTime),
                    formatStatisticsTime((long) time30Over)
            ));

            //距離分布
            statDistance0to5.setText(String.format(
                    Locale.JAPAN,
                    "0～5 km: %d回",
                    ride0to5km
            ));

            statDistance5to10.setText(String.format(
                    Locale.JAPAN,
                    "5～10 km: %d回",
                    ride5to10km
            ));

            statDistance10to20.setText(String.format(
                    Locale.JAPAN,
                    "10～20 km: %d回",
                    ride10to20km
            ));

            statDistance20to50.setText(String.format(
                    Locale.JAPAN,
                    "20～50 km: %d回",
                    ride20to50km
            ));

            statDistance50Over.setText(String.format(
                    Locale.JAPAN,
                    "50 km以上: %d回",
                    ride50kmOver
            ));

            Log.d("STATISTICS", "統計画面の読み込み成功");

        } catch (Exception e) {
            e.printStackTrace();
            showEmptyStatistics();
        }
    }

    private void showEmptyStatistics() {
        statTotalRideCount.setText("総走行回数: 0回");
        statTotalDistance.setText("総走行距離: 0.00 km");
        statTotalRideTime.setText("総走行時間: 0分00秒");
        statTotalMovingTime.setText("総移動時間: 0分00秒");
        statTotalStopTime.setText("総停止時間: 0分00秒");
        statTotalStopCount.setText("累計停止回数: 0回");

        statMaxDistance.setText("最長1回走行距離: 0.00 km");
        statMaxRideTime.setText("最長1回走行時間: 0分00秒");
        statMaxAverageSpeed.setText("最高平均速度: 0.0 km/h");
        statMaxMovingAverageSpeed.setText(
                "最高移動中平均速度: 0.0 km/h"
        );
        statMaxGpsSpeed.setText("最高GPS速度: 0.0 km/h");
        statLongestStopTime.setText("最長停止時間: 0分00秒");

        statSpeed0to5.setText("0～5 km/h: 0.0%（0分00秒）");
        statSpeed5to10.setText("5～10 km/h: 0.0%（0分00秒）");
        statSpeed10to15.setText("10～15 km/h: 0.0%（0分00秒）");
        statSpeed15to20.setText("15～20 km/h: 0.0%（0分00秒）");
        statSpeed20to25.setText("20～25 km/h: 0.0%（0分00秒）");
        statSpeed25to30.setText("25～30 km/h: 0.0%（0分00秒）");
        statSpeed30Over.setText("30 km/h以上: 0.0%（0分00秒）");

        statDistance0to5.setText("0～5 km: 0回");
        statDistance5to10.setText("5～10 km: 0回");
        statDistance10to20.setText("10～20 km: 0回");
        statDistance20to50.setText("20～50 km: 0回");
        statDistance50Over.setText("50 km以上: 0回");

        if (statDistanceChart != null) {
            statDistanceChart.clearData();
        }

        if (statDistanceChartTitle != null) {
            statDistanceChartTitle.setText(
                    "走行距離グラフ"
            );
        }
    }

    //グラフの更新関数
    private void updateDistanceChart(
            String selectedType,
            String periodKey
    ) {
        if (currentStatisticsRoot == null) {
            statDistanceChart.clearData();
            return;
        }

        if (selectedType.equals("全体")) {
            showYearlyDistanceChart();

        } else if (selectedType.equals("年")) {
            showMonthlyDistanceChart(periodKey);

        } else if (selectedType.equals("月")) {
            showWeeklyDistanceChart(periodKey);

        } else if (selectedType.equals("週")) {
            showDailyDistanceChart(periodKey);
        }
    }

    //それぞれの期間のグラフ用時間数
    //年ごとの走行距離グラフ
    private void showYearlyDistanceChart() {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Double> values = new ArrayList<>();

        JSONObject yearlyJson =
                currentStatisticsRoot.optJSONObject("yearly");

        if (yearlyJson == null) {
            statDistanceChart.clearData();
            return;
        }

        ArrayList<String> yearKeys =
                getSortedJsonKeysAscending(yearlyJson);

        for (String yearKey : yearKeys) {
            JSONObject block =
                    yearlyJson.optJSONObject(yearKey);

            if (block == null) {
                continue;
            }

            labels.add(yearKey);

            values.add(
                    getStatisticsBlockDistanceKm(block)
            );
        }

        statDistanceChartTitle.setText(
                "年ごとの走行距離"
        );

        statDistanceChart.setData(
                labels,
                values
        );
    }

    //選択年の月ごとの走行距離グラフ
    private void showMonthlyDistanceChart(
            String yearKey
    ) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Double> values = new ArrayList<>();

        JSONObject monthlyJson =
                currentStatisticsRoot.optJSONObject("monthly");

        for (int month = 1; month <= 12; month++) {
            String monthKey =
                    String.format(
                            Locale.JAPAN,
                            "%s-%02d",
                            yearKey,
                            month
                    );

            labels.add(month + "月");

            double distanceKm = 0.0;

            if (monthlyJson != null) {
                JSONObject block =
                        monthlyJson.optJSONObject(monthKey);

                if (block != null) {
                    distanceKm =
                            getStatisticsBlockDistanceKm(block);
                }
            }

            values.add(distanceKm);
        }

        statDistanceChartTitle.setText(
                yearKey + "年の月ごとの走行距離"
        );

        statDistanceChart.setData(
                labels,
                values
        );
    }

    //選択月の週ごとの走行距離グラフ
    private void showWeeklyDistanceChart(
            String monthKey
    ) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Double> values = new ArrayList<>();

        JSONObject dailyJson =
                currentStatisticsRoot.optJSONObject("daily");

        if (dailyJson == null) {
            statDistanceChart.clearData();
            return;
        }

        ArrayList<String> dayKeys =
                getSortedJsonKeysAscending(dailyJson);

        ArrayList<String> weekStartKeys =
                new ArrayList<>();

        ArrayList<Double> weekDistances =
                new ArrayList<>();

        for (String dayKey : dayKeys) {
            /*
             * 選択した月の日付だけを対象にする
             */
            if (!dayKey.startsWith(monthKey + "-")) {
                continue;
            }

            String weekStartKey =
                    getWeekStartKey(dayKey);

            if (weekStartKey == null) {
                continue;
            }

            JSONObject dayBlock =
                    dailyJson.optJSONObject(dayKey);

            double distanceKm =
                    getStatisticsBlockDistanceKm(dayBlock);

            int weekIndex =
                    weekStartKeys.indexOf(weekStartKey);

            if (weekIndex == -1) {
                weekStartKeys.add(weekStartKey);
                weekDistances.add(distanceKm);

            } else {
                weekDistances.set(
                        weekIndex,
                        weekDistances.get(weekIndex)
                                + distanceKm
                );
            }
        }

        for (int i = 0; i < weekStartKeys.size(); i++) {
            String weekStart =
                    weekStartKeys.get(i);

            String weekEnd =
                    addDaysToDateKey(
                            weekStart,
                            6
                    );

            labels.add(
                    formatShortDateKey(weekStart)
                            + "～"
                            + formatShortDateKey(weekEnd)
            );

            values.add(
                    weekDistances.get(i)
            );
        }

        statDistanceChartTitle.setText(
                formatMonthKeyForLabel(monthKey)
                        + "の週ごとの走行距離"
        );

        statDistanceChart.setData(
                labels,
                values
        );
    }

    //選択週の日ごとの走行距離グラフ
    private void showDailyDistanceChart(
            String weekStartKey
    ) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Double> values = new ArrayList<>();

        JSONObject dailyJson =
                currentStatisticsRoot.optJSONObject("daily");

        String[] dayNames = {
                "月",
                "火",
                "水",
                "木",
                "金",
                "土",
                "日"
        };

        for (int i = 0; i < 7; i++) {
            String dayKey =
                    addDaysToDateKey(
                            weekStartKey,
                            i
                    );

            labels.add(
                    dayNames[i]
                            + "\n"
                            + formatShortDateKey(dayKey)
            );

            double distanceKm = 0.0;

            if (dailyJson != null) {
                JSONObject dayBlock =
                        dailyJson.optJSONObject(dayKey);

                if (dayBlock != null) {
                    distanceKm =
                            getStatisticsBlockDistanceKm(dayBlock);
                }
            }

            values.add(distanceKm);
        }

        String weekEndKey =
                addDaysToDateKey(
                        weekStartKey,
                        6
                );

        statDistanceChartTitle.setText(
                formatDateKeyForLabel(weekStartKey)
                        + "～"
                        + formatDateKeyForLabel(weekEndKey)
                        + "の日ごとの走行距離"
        );

        statDistanceChart.setData(
                labels,
                values
        );
    }

    //統計ブロックから距離を取得する関数
    private double getStatisticsBlockDistanceKm(
            JSONObject statisticsBlock
    ) {
        if (statisticsBlock == null) {
            return 0.0;
        }

        JSONObject summaryJson =
                statisticsBlock.optJSONObject("summary");

        if (summaryJson == null) {
            return 0.0;
        }

        double distanceMeter =
                summaryJson.optDouble(
                        "totalDistance",
                        0.0
                );

        return distanceMeter / 1000.0;
    }

    //グラフ用に古い順でキーを取得するための関数
    private ArrayList<String> getSortedJsonKeysAscending(
            JSONObject jsonObject
    ) {
        ArrayList<String> keys =
                new ArrayList<>();

        if (jsonObject == null) {
            return keys;
        }

        Iterator<String> iterator =
                jsonObject.keys();

        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }

        Collections.sort(keys);

        return keys;
    }

    //日付・月の表示用関数
    private String formatShortDateKey(
            String dayKey
    ) {
        try {
            Date date =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.JAPAN
                    ).parse(dayKey);

            return new SimpleDateFormat(
                    "M/d",
                    Locale.JAPAN
            ).format(date);

        } catch (Exception e) {
            return dayKey;
        }
    }

    private String formatMonthKeyForLabel(
            String monthKey
    ) {
        try {
            Date date =
                    new SimpleDateFormat(
                            "yyyy-MM",
                            Locale.JAPAN
                    ).parse(monthKey);

            return new SimpleDateFormat(
                    "yyyy年M月",
                    Locale.JAPAN
            ).format(date);

        } catch (Exception e) {
            return monthKey;
        }
    }

    //メンテナンス追加ダイアログを作る関数
    private void showAddMaintenanceDialog() {
        showMaintenanceEditDialog(null);
    }

    private void showMaintenanceEditDialog(
            JSONObject editingRecord
    ) {
        boolean isEditing =
                editingRecord != null;

        LinearLayout dialogLayout =
                new LinearLayout(this);

        dialogLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        int padding = dpInt(20);

        dialogLayout.setPadding(
                padding,
                padding,
                padding,
                0
        );

        /*
         * メンテナンス種類
         */
        Spinner typeSpinner =
                new Spinner(this);

        String[] maintenanceTypes = {
                "空気圧",
                "チェーン",
                "ブレーキ",
                "タイヤ",
                "清掃",
                "ライト",
                "その他"
        };

        ArrayAdapter<String> typeAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.spinner_item,
                        maintenanceTypes
                );

        typeAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item
        );

        typeSpinner.setAdapter(typeAdapter);

        /*
         * 距離による次回目安
         */
        TextView distanceIntervalLabel =
                new TextView(this);

        distanceIntervalLabel.setText(
                "走行距離による次回目安"
        );
        distanceIntervalLabel.setTextSize(16);
        distanceIntervalLabel.setTextColor(
                Color.BLACK
        );
        distanceIntervalLabel.setPadding(
                0,
                dpInt(12),
                0,
                dpInt(4)
        );

        Spinner distanceIntervalSpinner =
                new Spinner(this);

        ArrayAdapter<String> distanceIntervalAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.spinner_item,
                        MAINTENANCE_DISTANCE_INTERVALS
                );

        distanceIntervalAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item
        );

        distanceIntervalSpinner.setAdapter(
                distanceIntervalAdapter
        );

        /*
         * 作業内容
         */
        EditText titleInput =
                new EditText(this);

        titleInput.setHint("作業内容");
        titleInput.setTextColor(Color.BLACK);
        titleInput.setHintTextColor(Color.GRAY);

        /*
         * メモ
         */
        EditText memoInput =
                new EditText(this);

        memoInput.setHint("メモ");
        memoInput.setMinLines(3);
        memoInput.setSingleLine(false);
        memoInput.setTextColor(Color.BLACK);
        memoInput.setHintTextColor(Color.GRAY);

        /*
         * 費用
         */
        EditText costInput =
                new EditText(this);

        costInput.setHint(
                "費用（任意、例：1200）"
        );

        costInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
        );

        costInput.setTextColor(Color.BLACK);
        costInput.setHintTextColor(Color.GRAY);

        /*
         * 次回予定
         */
        TextView intervalLabel =
                new TextView(this);

        intervalLabel.setText("次回予定");
        intervalLabel.setTextSize(16);
        intervalLabel.setTextColor(Color.BLACK);
        intervalLabel.setPadding(
                0,
                dpInt(12),
                0,
                dpInt(4)
        );

        Spinner intervalSpinner =
                new Spinner(this);

        ArrayAdapter<String> intervalAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.spinner_item,
                        MAINTENANCE_INTERVALS
                );

        intervalAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item
        );

        intervalSpinner.setAdapter(
                intervalAdapter
        );

        //ダイアログの表示
        dialogLayout.addView(typeSpinner);
        dialogLayout.addView(titleInput);
        dialogLayout.addView(memoInput);
        dialogLayout.addView(costInput);
        dialogLayout.addView(intervalLabel);
        dialogLayout.addView(intervalSpinner);
        dialogLayout.addView(distanceIntervalLabel);
        dialogLayout.addView(distanceIntervalSpinner);

        /*
         * 編集時は既存の値を入力欄に表示
         */
        if (isEditing) {
            String savedType =
                    editingRecord.optString(
                            "type",
                            "その他"
                    );

            selectSpinnerItem(
                    typeSpinner,
                    savedType
            );

            titleInput.setText(
                    editingRecord.optString(
                            "title",
                            ""
                    )
            );

            memoInput.setText(
                    editingRecord.optString(
                            "memo",
                            ""
                    )
            );

            int savedCost =
                    editingRecord.optInt(
                            "cost",
                            0
                    );

            if (savedCost > 0) {
                costInput.setText(
                        String.valueOf(savedCost)
                );
            }

            String savedInterval =
                    editingRecord.optString(
                            "nextInterval",
                            "なし"
                    );

            selectSpinnerItem(
                    intervalSpinner,
                    savedInterval
            );

            String savedDistanceInterval =
                    editingRecord.optString(
                            "nextDistanceIntervalLabel",
                            "なし"
                    );

            selectSpinnerItem(
                    distanceIntervalSpinner,
                    savedDistanceInterval
            );

        } else {
            /*
             * 新規追加時は、最初に選択されている種類の
             * 推奨期間を設定
             */
            String firstType =
                    typeSpinner
                            .getSelectedItem()
                            .toString();

            selectSpinnerItem(
                    intervalSpinner,
                    getDefaultMaintenanceInterval(
                            firstType
                    )
            );

            selectSpinnerItem(
                    distanceIntervalSpinner,
                    getDefaultMaintenanceDistanceInterval(
                            firstType
                    )
            );
        }

        /*
         * 種類を変更したら推奨期間も変更する
         *
         * 編集中は既存の設定を勝手に変えないよう、
         * ユーザーが種類を操作した時だけ反映する。
         */
        final boolean[] firstSelection = {
                true
        };

        typeSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        if (firstSelection[0]) {
                            firstSelection[0] = false;
                            return;
                        }

                        String selectedType =
                                maintenanceTypes[position];

                        String defaultInterval =
                                getDefaultMaintenanceInterval(
                                        selectedType
                                );

                        selectSpinnerItem(
                                intervalSpinner,
                                defaultInterval
                        );

                        String defaultDistanceInterval =
                                getDefaultMaintenanceDistanceInterval(
                                        selectedType
                                );

                        selectSpinnerItem(
                                distanceIntervalSpinner,
                                defaultDistanceInterval
                        );
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                    }
                }
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                isEditing
                                        ? "メンテナンス記録を編集"
                                        : selectedMaintenanceDateKey
                                          + " のメンテナンス"
                        )
                        .setView(dialogLayout)
                        .setPositiveButton(
                                isEditing ? "更新" : "保存",
                                null
                        )
                        .setNegativeButton(
                                "キャンセル",
                                null
                        )
                        .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {

                String type =
                        typeSpinner
                                .getSelectedItem()
                                .toString();

                String title =
                        titleInput
                                .getText()
                                .toString()
                                .trim();

                String memo =
                        memoInput
                                .getText()
                                .toString()
                                .trim();

                String costText =
                        costInput
                                .getText()
                                .toString()
                                .trim();

                int cost = 0;

                if (!costText.isEmpty()) {
                    try {
                        cost =
                                Integer.parseInt(
                                        costText
                                );

                    } catch (NumberFormatException e) {
                        Toast.makeText(
                                this,
                                "費用は整数で入力してください",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }
                }

                String nextInterval =
                        intervalSpinner
                                .getSelectedItem()
                                .toString();

                String nextDistanceIntervalLabel =
                        distanceIntervalSpinner
                                .getSelectedItem()
                                .toString();

                if (title.isEmpty()) {
                    Toast.makeText(
                            this,
                            "作業内容を入力してください",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                if (isEditing) {
                    updateMaintenanceRecord(
                            editingRecord.optLong(
                                    "id",
                                    -1
                            ),
                            type,
                            title,
                            memo,
                            cost,
                            nextInterval,
                            nextDistanceIntervalLabel
                    );

                } else {
                    saveMaintenanceRecord(
                            type,
                            title,
                            memo,
                            cost,
                            nextInterval,
                            nextDistanceIntervalLabel
                    );
                }

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    //Spinnerの項目を選択する補助関数
    private void selectSpinnerItem(
            Spinner spinner,
            String value
    ) {
        if (spinner == null || value == null) {
            return;
        }

        for (int i = 0;
             i < spinner.getCount();
             i++) {

            Object item =
                    spinner.getItemAtPosition(i);

            if (item != null
                    && value.equals(item.toString())) {

                spinner.setSelection(i);
                return;
            }
        }
    }

    //メンテナンス記録をJSONに保存する関数
    private void saveMaintenanceRecord(
            String type,
            String title,
            String memo,
            int cost,
            String nextInterval,
            String nextDistanceIntervalLabel
    ) {
        try {
            JSONObject rootJson =
                    loadMaintenanceJson();

            JSONArray recordsArray =
                    rootJson.getJSONArray(
                            "records"
                    );

            String nextDate =
                    calculateNextMaintenanceDate(
                            selectedMaintenanceDateKey,
                            nextInterval
                    );

            // メンテナンス実施時点の累計距離
            double distanceAtMaintenance =
                    getCurrentTotalDistanceMeters();

            // 選択された次回までの距離
            double nextDistanceInterval =
                    getMaintenanceDistanceMeters(
                            nextDistanceIntervalLabel
                    );

            // 次回の累計距離目安
            double nextDistance = 0.0;

            if (nextDistanceInterval > 0.0) {
                nextDistance =
                        distanceAtMaintenance
                                + nextDistanceInterval;
            }

            JSONObject recordJson =
                    new JSONObject();

            recordJson.put(
                    "id",
                    System.currentTimeMillis()
            );

            recordJson.put(
                    "date",
                    selectedMaintenanceDateKey
            );

            recordJson.put(
                    "type",
                    type
            );

            recordJson.put(
                    "title",
                    title
            );

            recordJson.put(
                    "memo",
                    memo
            );

            recordJson.put(
                    "cost",
                    cost
            );

            recordJson.put(
                    "nextInterval",
                    nextInterval
            );

            recordJson.put(
                    "nextDate",
                    nextDate
            );

            recordJson.put(
                    "distanceAtMaintenance",
                    distanceAtMaintenance
            );

            recordJson.put(
                    "nextDistanceInterval",
                    nextDistanceInterval
            );

            recordJson.put(
                    "nextDistanceIntervalLabel",
                    nextDistanceIntervalLabel
            );

            recordJson.put(
                    "nextDistance",
                    nextDistance
            );

            recordsArray.put(recordJson);

            saveMaintenanceJson(rootJson);

            loadMaintenanceList(
                    selectedMaintenanceDateKey
            );

            updateMaintenanceCalendar();//カレンダーの更新

            Toast.makeText(
                    this,
                    "メンテナンス記録を保存しました",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            Log.e(
                    "MAINTENANCE",
                    "メンテナンス記録の保存に失敗",
                    e
            );

            Toast.makeText(
                    this,
                    "メンテナンス記録の保存に失敗しました",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    //選択日の記録を読み込む関数
    private void loadMaintenanceList(
            String dateKey
    ) {
        maintenanceList.removeAllViews();

        File file =
                new File(
                        getFilesDir(),
                        "maintenance.json"
                );

        if (!file.exists()) {
            showEmptyMaintenanceMessage();
            return;
        }

        try {
            JSONObject rootJson =
                    new JSONObject(
                            readTextFile(file)
                    );

            JSONArray recordsArray =
                    rootJson.optJSONArray(
                            "records"
                    );

            if (recordsArray == null) {
                showEmptyMaintenanceMessage();
                return;
            }

            int matchCount = 0;

            // 現在の累計走行距離
            double currentTotalDistance =
                    getCurrentTotalDistanceMeters();

            /*
             * 後から保存した記録を上に表示するため、
             * 配列の後ろから読み込む
             */
            for (int i = recordsArray.length() - 1;
                 i >= 0;
                 i--) {

                JSONObject recordJson =
                        recordsArray.getJSONObject(i);

                // 実際にメンテナンスを行った日
                String recordDate =
                        recordJson.optString(
                                "date",
                                ""
                        );

                // 次回メンテナンス予定日
                String nextDate =
                        recordJson.optString(
                                "nextDate",
                                ""
                        );

                // 選択日が実施日かどうか
                boolean isRecordForDay =
                        dateKey.equals(recordDate);

                // 選択日が予定日かどうか
                boolean isScheduleForDay =
                        dateKey.equals(nextDate);

                // 実施日でも予定日でもなければ表示しない
                if (!isRecordForDay
                        && !isScheduleForDay) {
                    continue;
                }

                matchCount++;

                String type =
                        recordJson.optString(
                                "type",
                                "その他"
                        );

                String title =
                        recordJson.optString(
                                "title",
                                ""
                        );

                String memo =
                        recordJson.optString(
                                "memo",
                                ""
                        );

                long recordId =
                        recordJson.optLong(
                                "id",
                                -1
                        );

                int cost =
                        recordJson.optInt(
                                "cost",
                                0
                        );

                String nextInterval =
                        recordJson.optString(
                                "nextInterval",
                                "なし"
                        );

                double nextDistance =
                        recordJson.optDouble(
                                "nextDistance",
                                0.0
                        );

                String nextDistanceIntervalLabel =
                        recordJson.optString(
                                "nextDistanceIntervalLabel",
                                "なし"
                        );

                TextView recordView =
                        new TextView(this);

                StringBuilder text =
                        new StringBuilder();

                /*
                 * 何として表示されているのかを明示
                 */
                if (isRecordForDay && isScheduleForDay) {
                    text.append("【実施記録・予定】\n");

                } else if (isRecordForDay) {
                    text.append("【実施記録】\n");

                } else {
                    text.append("【予定】\n");
                }

                text.append("【")
                        .append(type)
                        .append("】\n");

                text.append(title);

                if (!memo.isEmpty()) {
                    text.append("\n")
                            .append(memo);
                }

                /*
                 * 予定として表示している場合は、
                 * 元になった実施日を表示する
                 */
                if (isScheduleForDay && !isRecordForDay) {
                    if (!recordDate.isEmpty()) {
                        text.append("\n前回実施日: ")
                                .append(
                                        formatMaintenanceDate(
                                                recordDate
                                        )
                                );
                    }

                    text.append("\n予定日: ")
                            .append(
                                    formatMaintenanceDate(
                                            nextDate
                                    )
                            );
                }

                /*
                 * 費用は実施した日の記録だけに表示
                 */
                if (isRecordForDay && cost > 0) {
                    text.append("\n費用: ")
                            .append(
                                    String.format(
                                            Locale.JAPAN,
                                            "%,d円",
                                            cost
                                    )
                            );
                }

                /*
                 * 実施記録には、そこから計算された次回予定を表示
                 */
                if (isRecordForDay && !nextDate.isEmpty()) {
                    text.append("\n次回予定: ")
                            .append(
                                    formatMaintenanceDate(
                                            nextDate
                                    )
                            )
                            .append("（")
                            .append(nextInterval)
                            .append("）");
                }

                /*
                 * 距離による次回目安を表示
                 */
                if (nextDistance > 0.0) {
                    double remainingDistance =
                            nextDistance
                                    - currentTotalDistance;

                    if (remainingDistance > 0.0) {
                        text.append(
                                String.format(
                                        Locale.JAPAN,
                                        "\n距離目安: あと%.0f km（%s）",
                                        remainingDistance / 1000.0,
                                        nextDistanceIntervalLabel
                                )
                        );

                    } else {
                        text.append(
                                String.format(
                                        Locale.JAPAN,
                                        "\n距離目安: %.0f km超過",
                                        Math.abs(remainingDistance)
                                                / 1000.0
                                )
                        );
                    }
                }

                recordView.setText(
                        text.toString()
                );

                recordView.setTextSize(16);
                recordView.setTextColor(
                        Color.BLACK
                );

                long daysUntil =
                        getDaysUntilMaintenanceDate(
                                nextDate
                        );

                /*
                 * 日付による状態
                 */
                boolean overdueByDate =
                        daysUntil < 0;

                boolean comingSoonByDate =
                        daysUntil >= 0
                                && daysUntil <= 7;

                /*
                 * 距離による状態
                 */
                boolean overdueByDistance =
                        nextDistance > 0.0
                                && currentTotalDistance
                                >= nextDistance;

                /*
                 * 残り50km以下なら期限間近
                 */
                boolean comingSoonByDistance =
                        nextDistance > currentTotalDistance
                                && nextDistance
                                - currentTotalDistance
                                <= 50000.0;

                /*
                 * 色の優先順位
                 *
                 * 1. 期限超過：赤
                 * 2. 予定として表示：黄色
                 * 3. 期限間近：黄色
                 * 4. 通常の実施記録：灰色
                 */
                if (overdueByDate
                        || overdueByDistance) {

                    // 期限超過：薄い赤
                    recordView.setBackgroundColor(
                            Color.rgb(
                                    255,
                                    205,
                                    210
                            )
                    );

                } else if (isScheduleForDay
                        && !isRecordForDay) {

                    // 予定：薄い黄色
                    recordView.setBackgroundColor(
                            Color.rgb(
                                    255,
                                    249,
                                    196
                            )
                    );

                } else if (comingSoonByDate
                        || comingSoonByDistance) {

                    // 期限間近：薄い黄色
                    recordView.setBackgroundColor(
                            Color.rgb(
                                    255,
                                    249,
                                    196
                            )
                    );

                } else {

                    // 通常の実施記録：灰色
                    recordView.setBackgroundColor(
                            Color.rgb(
                                    235,
                                    235,
                                    235
                            )
                    );
                }

                recordView.setPadding(
                        dpInt(12),
                        dpInt(12),
                        dpInt(12),
                        dpInt(12)
                );

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );

                params.setMargins(
                        0,
                        0,
                        0,
                        dpInt(8)
                );

                recordView.setLayoutParams(
                        params
                );

                // この記録をタップしたとき、編集・削除メニューを表示
                recordView.setClickable(true);
                recordView.setFocusable(true);

                JSONObject selectedRecordJson = recordJson;

                recordView.setOnClickListener(v -> {
                    showMaintenanceRecordMenu(
                            recordView,
                            selectedRecordJson
                    );
                });

                maintenanceList.addView(
                        recordView
                );
            }

            if (matchCount == 0) {
                showEmptyMaintenanceMessage();
            }

        } catch (Exception e) {
            e.printStackTrace();

            showEmptyMaintenanceMessage();

            Toast.makeText(
                    this,
                    "メンテナンス記録の読み込みに失敗しました",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    //記録がない場合の表示関数
    private void showEmptyMaintenanceMessage() {
        TextView emptyView =
                new TextView(this);

        emptyView.setText(
                "この日の実施記録・予定はありません"
        );

        emptyView.setTextSize(16);
        emptyView.setTextColor(
                Color.DKGRAY
        );

        emptyView.setPadding(
                0,
                dpInt(12),
                0,
                dpInt(12)
        );

        maintenanceList.addView(
                emptyView
        );
    }

    //予定修理時間を初期値を返す関数
    private String getDefaultMaintenanceInterval(
            String maintenanceType
    ) {
        switch (maintenanceType) {
            case "空気圧":
                return "2週間後";

            case "チェーン":
                return "1か月後";

            case "ブレーキ":
                return "3か月後";

            case "タイヤ":
                return "3か月後";

            case "清掃":
                return "1か月後";

            case "ライト":
                return "3か月後";

            default:
                return "なし";
        }
    }

    /**
     * メンテナンス種類ごとの距離初期値
     */
    private String getDefaultMaintenanceDistanceInterval(
            String maintenanceType
    ) {
        switch (maintenanceType) {
            case "チェーン":
                return "300 km後";

            case "ブレーキ":
                return "500 km後";

            case "タイヤ":
                return "1,000 km後";

            case "空気圧":
            case "清掃":
            case "ライト":
            default:
                return "なし";
        }
    }

    /**
     * 「500 km後」などの選択値をメートルへ変換する
     */
    private double getMaintenanceDistanceMeters(
            String intervalLabel
    ) {
        if (intervalLabel == null) {
            return 0.0;
        }

        switch (intervalLabel) {
            case "100 km後":
                return 100000.0;

            case "300 km後":
                return 300000.0;

            case "500 km後":
                return 500000.0;

            case "1,000 km後":
                return 1000000.0;

            case "2,000 km後":
                return 2000000.0;

            case "3,000 km後":
                return 3000000.0;

            case "5,000 km後":
                return 5000000.0;

            default:
                return 0.0;
        }
    }

    //次回予定日を計算する関数
    private String calculateNextMaintenanceDate(
            String baseDateKey,
            String interval
    ) {
        if (interval == null || interval.equals("なし")) {
            return "";
        }

        try {
            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.JAPAN
                    );

            format.setLenient(false);

            Date baseDate = format.parse(baseDateKey);

            if (baseDate == null) {
                return "";
            }

            Calendar calendar =
                    Calendar.getInstance(Locale.JAPAN);

            calendar.setTime(baseDate);

            switch (interval) {
                case "1週間後":
                    calendar.add(Calendar.WEEK_OF_YEAR, 1);
                    break;

                case "2週間後":
                    calendar.add(Calendar.WEEK_OF_YEAR, 2);
                    break;

                case "1か月後":
                    calendar.add(Calendar.MONTH, 1);
                    break;

                case "2か月後":
                    calendar.add(Calendar.MONTH, 2);
                    break;

                case "3か月後":
                    calendar.add(Calendar.MONTH, 3);
                    break;

                case "6か月後":
                    calendar.add(Calendar.MONTH, 6);
                    break;

                case "1年後":
                    calendar.add(Calendar.YEAR, 1);
                    break;

                default:
                    return "";
            }

            return format.format(calendar.getTime());

        } catch (Exception e) {
            Log.e(
                    "MAINTENANCE",
                    "次回予定日の計算に失敗しました",
                    e
            );

            return "";
        }
    }

    /**
     * statistics.jsonから現在の累計走行距離を取得する
     * 単位はメートル
     */
    private double getCurrentTotalDistanceMeters() {
        try {
            File file =
                    new File(
                            getFilesDir(),
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
                    "MAINTENANCE",
                    "累計走行距離の取得に失敗しました",
                    e
            );

            return 0.0;
        }
    }

    //JSONの読み書きを共通関数
    //読み込み
    private JSONObject loadMaintenanceJson()
            throws Exception {

        File file =
                new File(
                        getFilesDir(),
                        "maintenance.json"
                );

        if (!file.exists()) {
            JSONObject rootJson =
                    new JSONObject();

            rootJson.put("version", 1);
            rootJson.put(
                    "records",
                    new JSONArray()
            );

            return rootJson;
        }

        JSONObject rootJson =
                new JSONObject(
                        readTextFile(file)
                );

        if (rootJson.optJSONArray("records") == null) {
            rootJson.put(
                    "records",
                    new JSONArray()
            );
        }

        return rootJson;
    }

    //保存
    private void saveMaintenanceJson(
            JSONObject rootJson
    ) throws Exception {

        File file =
                new File(
                        getFilesDir(),
                        "maintenance.json"
                );

        FileOutputStream fos =
                new FileOutputStream(file);

        fos.write(
                rootJson
                        .toString(4)
                        .getBytes(StandardCharsets.UTF_8)
        );

        fos.close();
    }

    //編集処理の関数
    private void updateMaintenanceRecord(
            long recordId,
            String type,
            String title,
            String memo,
            int cost,
            String nextInterval,
            String nextDistanceIntervalLabel
    ) {
        if (recordId < 0) {
            Toast.makeText(
                    this,
                    "編集する記録を特定できません",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {
            JSONObject rootJson =
                    loadMaintenanceJson();

            JSONArray recordsArray =
                    rootJson.getJSONArray(
                            "records"
                    );

            boolean updated = false;

            for (int i = 0;
                 i < recordsArray.length();
                 i++) {

                JSONObject recordJson =
                        recordsArray.getJSONObject(i);

                long id =
                        recordJson.optLong(
                                "id",
                                -1
                        );

                if (id != recordId) {
                    continue;
                }

                String recordDate =
                        recordJson.optString(
                                "date",
                                selectedMaintenanceDateKey
                        );

                String nextDate =
                        calculateNextMaintenanceDate(
                                recordDate,
                                nextInterval
                        );

                /*
                 * 編集しても、メンテナンス実施時点の距離は
                 * 基本的に変更しない
                 */
                double distanceAtMaintenance =
                        recordJson.optDouble(
                                "distanceAtMaintenance",
                                getCurrentTotalDistanceMeters()
                        );

                double nextDistanceInterval =
                        getMaintenanceDistanceMeters(
                                nextDistanceIntervalLabel
                        );

                double nextDistance = 0.0;

                if (nextDistanceInterval > 0.0) {
                    nextDistance =
                            distanceAtMaintenance
                                    + nextDistanceInterval;
                }

                recordJson.put("type", type);
                recordJson.put("title", title);
                recordJson.put("memo", memo);
                recordJson.put("cost", cost);
                recordJson.put(
                        "nextInterval",
                        nextInterval
                );
                recordJson.put(
                        "nextDate",
                        nextDate
                );

                recordJson.put(
                        "distanceAtMaintenance",
                        distanceAtMaintenance
                );

                recordJson.put(
                        "nextDistanceInterval",
                        nextDistanceInterval
                );

                recordJson.put(
                        "nextDistanceIntervalLabel",
                        nextDistanceIntervalLabel
                );

                recordJson.put(
                        "nextDistance",
                        nextDistance
                );

                updated = true;
                break;
            }

            if (!updated) {
                Toast.makeText(
                        this,
                        "編集対象の記録が見つかりません",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            saveMaintenanceJson(rootJson);

            loadMaintenanceList(
                    selectedMaintenanceDateKey
            );

            updateMaintenanceCalendar();//カレンダーの更新

            Toast.makeText(
                    this,
                    "メンテナンス記録を更新しました",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            Log.e(
                    "MAINTENANCE",
                    "メンテナンス記録の更新に失敗",
                    e
            );

            Toast.makeText(
                    this,
                    "メンテナンス記録の更新に失敗しました",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    //削除距離の関数
    private void showDeleteMaintenanceConfirmDialog(
            long recordId
    ) {
        new AlertDialog.Builder(this)
                .setTitle("記録を削除")
                .setMessage(
                        "このメンテナンス記録を削除しますか？"
                )
                .setPositiveButton(
                        "削除",
                        (dialog, which) -> {
                            deleteMaintenanceRecord(
                                    recordId
                            );
                        }
                )
                .setNegativeButton(
                        "キャンセル",
                        null
                )
                .show();
    }

    private void deleteMaintenanceRecord(
            long recordId
    ) {
        try {
            JSONObject rootJson =
                    loadMaintenanceJson();

            JSONArray recordsArray =
                    rootJson.getJSONArray(
                            "records"
                    );

            boolean deleted = false;

            for (int i = 0;
                 i < recordsArray.length();
                 i++) {

                JSONObject recordJson =
                        recordsArray.getJSONObject(i);

                if (recordJson.optLong("id", -1)
                        == recordId) {

                    recordsArray.remove(i);
                    deleted = true;
                    break;
                }
            }

            if (!deleted) {
                Toast.makeText(
                        this,
                        "削除対象の記録が見つかりません",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            saveMaintenanceJson(rootJson);

            loadMaintenanceList(
                    selectedMaintenanceDateKey
            );

            updateMaintenanceCalendar();//カレンダーの更新

            Toast.makeText(
                    this,
                    "メンテナンス記録を削除しました",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            Log.e(
                    "MAINTENANCE",
                    "メンテナンス記録の削除に失敗",
                    e
            );

            Toast.makeText(
                    this,
                    "メンテナンス記録の削除に失敗しました",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    //タップした時の処理を追加
    private void showMaintenanceRecordMenu(
            View anchor,
            JSONObject recordJson
    ) {
        PopupMenu popupMenu =
                new PopupMenu(this, anchor);

        popupMenu.getMenu().add("編集");
        popupMenu.getMenu().add("削除");

        popupMenu.setOnMenuItemClickListener(item -> {
            String title =
                    item.getTitle().toString();

            if (title.equals("編集")) {
                showMaintenanceEditDialog(
                        recordJson
                );

                return true;
            }

            if (title.equals("削除")) {
                long recordId =
                        recordJson.optLong(
                                "id",
                                -1
                        );

                showDeleteMaintenanceConfirmDialog(
                        recordId
                );

                return true;
            }

            return false;
        });

        popupMenu.show();
    }

    //日付表示用関数
    private String formatMaintenanceDate(
            String dateKey
    ) {
        try {
            SimpleDateFormat inputFormat =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.JAPAN
                    );

            Date date =
                    inputFormat.parse(dateKey);

            if (date == null) {
                return dateKey;
            }

            return new SimpleDateFormat(
                    "yyyy年M月d日",
                    Locale.JAPAN
            ).format(date);

        } catch (Exception e) {
            return dateKey;
        }
    }

    /**
     * 指定日まであと何日かを返す
     *
     * 正数：予定日までの日数
     * 0：今日
     * 負数：予定日を過ぎている
     */
    private long getDaysUntilMaintenanceDate(
            String dateKey
    ) {
        if (dateKey == null || dateKey.isEmpty()) {
            return Long.MAX_VALUE;
        }

        try {
            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.JAPAN
                    );

            format.setLenient(false);

            Date targetDate =
                    format.parse(dateKey);

            if (targetDate == null) {
                return Long.MAX_VALUE;
            }

            Calendar today =
                    Calendar.getInstance(
                            Locale.JAPAN
                    );

            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar target =
                    Calendar.getInstance(
                            Locale.JAPAN
                    );

            target.setTime(targetDate);

            target.set(Calendar.HOUR_OF_DAY, 0);
            target.set(Calendar.MINUTE, 0);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);

            long difference =
                    target.getTimeInMillis()
                            - today.getTimeInMillis();

            return difference
                    / (24L * 60L * 60L * 1000L);

        } catch (Exception e) {
            Log.e(
                    "MAINTENANCE",
                    "日付期限の計算に失敗しました",
                    e
            );

            return Long.MAX_VALUE;
        }
    }

    /**
     * JSONから記録日一覧を読み込む関数
     * maintenance.jsonから、
     * 実施日と次回予定日を読み込む
     */
    private void loadMaintenanceCalendarDates() {
        // 再読み込み前に古い情報を消す
        maintenanceRecordDates.clear();
        maintenanceScheduledDates.clear();

        try {
            File file =
                    new File(
                            getFilesDir(),
                            "maintenance.json"
                    );

            if (!file.exists()) {
                return;
            }

            JSONObject rootJson =
                    new JSONObject(
                            readTextFile(file)
                    );

            JSONArray recordsArray =
                    rootJson.optJSONArray(
                            "records"
                    );

            if (recordsArray == null) {
                return;
            }

            for (int i = 0;
                 i < recordsArray.length();
                 i++) {

                JSONObject recordJson =
                        recordsArray.getJSONObject(i);

                /*
                 * メンテナンスを実施した日
                 */
                String recordDate =
                        recordJson.optString(
                                "date",
                                ""
                        );

                if (!recordDate.isEmpty()) {
                    maintenanceRecordDates.add(
                            recordDate
                    );
                }

                /*
                 * 次回メンテナンス予定日
                 */
                String nextDate =
                        recordJson.optString(
                                "nextDate",
                                ""
                        );

                if (!nextDate.isEmpty()) {
                    maintenanceScheduledDates.add(
                            nextDate
                    );
                }
            }

        } catch (Exception e) {
            Log.e(
                    "MAINTENANCE",
                    "カレンダー日付情報の読み込みに失敗しました",
                    e
            );
        }
    }

    /**
     * カレンダーの日付1マスを作る関数
     */
    private TextView createMaintenanceDayView(
            int position
    ) {
        TextView dayView =
                new TextView(this);

        dayView.setTextSize(16);
        dayView.setTextColor(
                Color.BLACK
        );

        dayView.setGravity(
                android.view.Gravity.CENTER
        );

        dayView.setClickable(true);
        dayView.setFocusable(true);

        /*
         * 42マスの位置から、
         * 何行目・何列目かを求める
         */
        int row =
                position / 7;

        int column =
                position % 7;

        GridLayout.LayoutParams params =
                new GridLayout.LayoutParams();

        params.width = 0;
        params.height = dpInt(48);

        /*
         * 列は7等分する
         */
        params.columnSpec =
                GridLayout.spec(
                        column,
                        1f
                );

        /*
         * 行番号を明示する
         * 行方向には重みを設定しない
         */
        params.rowSpec =
                GridLayout.spec(
                        row
                );

        params.setMargins(
                dpInt(2),
                dpInt(2),
                dpInt(2),
                dpInt(2)
        );

        dayView.setLayoutParams(params);

        return dayView;
    }

    /**
     * カレンダーを描画する関数
     * 表示中の年月に合わせてカレンダーを再描画する
     */
    private void updateMaintenanceCalendar() {
        if (maintenanceCalendarGrid == null
                || maintenanceDisplayCalendar == null) {
            return;
        }

        /*
         * JSONはここで1回だけ読み込む
         * 各日付セルではcontainsだけで判定する
         */
        loadMaintenanceCalendarDates();

        maintenanceCalendarGrid.removeAllViews();

        int year =
                maintenanceDisplayCalendar.get(
                        Calendar.YEAR
                );

        int month =
                maintenanceDisplayCalendar.get(
                        Calendar.MONTH
                );

        textCalendarMonth.setText(
                String.format(
                        Locale.JAPAN,
                        "%d年%d月",
                        year,
                        month + 1
                )
        );

        Calendar firstDay =
                Calendar.getInstance(
                        Locale.JAPAN
                );

        firstDay.set(
                year,
                month,
                1
        );

        firstDay.set(
                Calendar.HOUR_OF_DAY,
                0
        );
        firstDay.set(Calendar.MINUTE, 0);
        firstDay.set(Calendar.SECOND, 0);
        firstDay.set(Calendar.MILLISECOND, 0);

        /*
         * 日曜日を0、月曜日を1、…、土曜日を6に変換
         *
         * Calendar.SUNDAY    = 1
         * Calendar.MONDAY    = 2
         * ...
         * Calendar.SATURDAY  = 7
         */
        int startPosition =
                firstDay.get(
                        Calendar.DAY_OF_WEEK
                ) - 1;

        int maxDay =
                firstDay.getActualMaximum(
                        Calendar.DAY_OF_MONTH
                );

        /*
         * 7列×6行なので42マス作る
         */
        for (int position = 0;
             position < 42;
             position++) {

            TextView dayView =
                    createMaintenanceDayView(
                            position
                    );

            int day =
                    position
                            - startPosition
                            + 1;

            /*
             * その月に存在しない空白セル
             */
            if (day < 1 || day > maxDay) {
                dayView.setText("");
                dayView.setClickable(false);

                maintenanceCalendarGrid.addView(
                        dayView
                );

                continue;
            }

            dayView.setText(
                    String.valueOf(day)
            );

            String dateKey =
                    String.format(
                            Locale.JAPAN,
                            "%04d-%02d-%02d",
                            year,
                            month + 1,
                            day
                    );

            boolean hasRecord =
                    maintenanceRecordDates.contains(
                            dateKey
                    );

            boolean hasSchedule =
                    maintenanceScheduledDates.contains(
                            dateKey
                    );

            boolean isSelected =
                    dateKey.equals(
                            selectedMaintenanceDateKey
                    );

            /*
             * 背景色の優先順位
             *
             * 1. 選択中：青
             * 2. 実施記録あり：緑
             * 3. 次回予定あり：黄色
             * 4. 何もなし：白
             */
            if (isSelected) {

                // 選択中：薄い青
                dayView.setBackgroundColor(
                        Color.rgb(
                                187,
                                222,
                                251
                        )
                );

            } else if (hasRecord) {

                // 実施記録あり：薄い緑
                dayView.setBackgroundColor(
                        Color.rgb(
                                200,
                                230,
                                201
                        )
                );

            } else if (hasSchedule) {

                // 次回予定あり：薄い黄色
                dayView.setBackgroundColor(
                        Color.rgb(
                                255,
                                249,
                                196
                        )
                );

            } else {

                // 通常
                dayView.setBackgroundColor(
                        Color.WHITE
                );
            }

            /*
             * 選択中の日付は薄い青を優先
             */
            if (dateKey.equals(
                    selectedMaintenanceDateKey
            )) {
                dayView.setBackgroundColor(
                        Color.rgb(
                                187,
                                222,
                                251
                        )
                );
            }

            /*
             * position % 7
             * 0=日、1=月、…、6=土
             */
            int weekColumn =
                    position % 7;

            if (weekColumn == 0) {

                // 日曜日：赤
                dayView.setTextColor(
                        Color.rgb(
                                198,
                                40,
                                40
                        )
                );

            } else if (weekColumn == 6) {

                // 土曜日：青
                dayView.setTextColor(
                        Color.rgb(
                                21,
                                101,
                                192
                        )
                );

            } else {

                // 平日：黒
                dayView.setTextColor(
                        Color.BLACK
                );
            }

            final int selectedYear = year;
            final int selectedMonth = month;
            final int selectedDay = day;
            final String selectedDateKey = dateKey;

            dayView.setOnClickListener(v -> {
                selectedMaintenanceDateKey =
                        selectedDateKey;

                maintenanceSelectedDate.setText(
                        String.format(
                                Locale.JAPAN,
                                "選択日: %d年%d月%d日",
                                selectedYear,
                                selectedMonth + 1,
                                selectedDay
                        )
                );

                loadMaintenanceList(
                        selectedMaintenanceDateKey
                );

                /*
                 * 選択中の日付色を更新
                 */
                updateMaintenanceCalendar();
            });

            maintenanceCalendarGrid.addView(
                    dayView
            );
        }
    }
}