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
import android.widget.RadioGroup;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.views.overlay.MapEventsOverlay;
import android.graphics.Point;
import androidx.appcompat.app.AlertDialog;
import android.widget.EditText;

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
    private View mapLayout;//マップ表示
    private View historyLayout;//走行履歴表示
    private View recordPanel;//記録関係のパネル
    private View roadEditPanel;//色分けのパネル
    private TextView titleBar;//画面上部のタイトルバー
    private LinearLayout historyList;//走行履歴の表示用リスト
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

        //各種ボタンの機能実装
        btnHistoryMode.setOnClickListener(v -> changeMode(AppMode.HISTORY));
        btnRoadEditMode.setOnClickListener(v -> changeMode(AppMode.EDIT_ROAD));
        btnBackMapFromHistory.setOnClickListener(v -> changeMode(AppMode.MAP));

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
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (currentMode == AppMode.EDIT_ROAD) {
                    clearSelectedRoadSegment();

                    RoadType selectedType = getSelectedRoadType();

                    if (editingRoad == null) {
                        editingRoad = new RoadSegment(selectedType);
                    }

                    editingRoad.type = selectedType;
                    editingRoad.points.add(p);

                    roadPreviewLine.setPoints(editingRoad.points);
                    roadPreviewLine.setColor(getColorByRoadType(editingRoad.type));

                    map.invalidate();
                    return true;
                }

                if (currentMode == AppMode.MAP) {
                    int index = findNearestRoadSegmentIndex(p);

                    if (index != -1) {
                        showRoadSegmentMemoDialog(index);
                        return true;
                    }
                }

                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                if (currentMode == AppMode.EDIT_ROAD) {
                    int index = findNearestRoadSegmentIndex(p);

                    if (index == -1) {
                        clearSelectedRoadSegment();
                        Toast.makeText(MainActivity.this, "近くに線がありません", Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    selectRoadSegment(index);
                    Toast.makeText(MainActivity.this, "線を選択しました", Toast.LENGTH_SHORT).show();
                    return true;
                }

                return false;
            }
        };

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
                        currentPoint.getLatitude(),//緯度
                        currentPoint.getLongitude(),//経度
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
            isFollowingCurrentLocation = false;
            endTime = System.currentTimeMillis();//終了時刻を記録

            saveRouteToJson();//JSONファイルに保存

            //終了を通知
            Toast.makeText(this, "記録を停止して保存しました", Toast.LENGTH_SHORT).show();
        });

        //走行履歴の詳細データの表示用パネルの設定
        routeDetailPanel.setOnClickListener(v -> {
            if (detailPanelOpen) {//詳細欄を触ったら、下から上に上がってくるモーションをする。
                routeDetailPanel.animate().translationY(dp(240)).setDuration(250).start();
                detailPanelOpen = false;
            } else {//閉じるときのアニメーションを追加
                routeDetailPanel.animate().translationY(0f).setDuration(250).start();
                detailPanelOpen = true;
            }
        });
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

                        //追跡中は移動距離が4mを超えたときに現在地を中心に移動
                        if(isFollowingCurrentLocation){
                            if(result[0] > 4.0){
                                map.getController().animateTo(currentPoint);//現在地を画面の中心に移動
                            }
                        }
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

                currentMarker.setPosition(currentPoint);//現在地にピンの位置を更新
                currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);//マーカーを立てる

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

        if (mode == AppMode.HISTORY) {//履歴モードの場合
            historyLayout.setVisibility(View.VISIBLE);//履歴用のレイアウトを表示
            isRecording = false;//記録中なら記録を終了する
            loadHistoryList();//走行履歴のリストを表示
            return;
        }

        if (mode == AppMode.HISTORY_DETAIL) {//走行ルートの場合
            historyDetailLayout.setVisibility(View.VISIBLE);//ルート表示用のレイアウトを表示
            isRecording = false;//記録中なら記録を終了する
            return;
        }

        //これ以降はすべてMAPレイアウト状に表示するモードである
        mapLayout.setVisibility(View.VISIBLE);

        if (mode == AppMode.MAP) {//通常モードの場合
            titleBar.setText("自転車安全マップ");//タイトルバーの表記を変更
            titleBar.setBackgroundColor(Color.rgb(67, 160, 71));//タイトルバーの色を変更
            recordPanel.setVisibility(View.VISIBLE);//記録と保存のボタンを表示
            roadEditPanel.setVisibility(View.GONE);//色分け用のボタンを非表示に
        } else if (mode == AppMode.EDIT_ROAD) {//色分けモードの場合
            titleBar.setText("色分けモード");//タイトルバーの表記を変更
            titleBar.setBackgroundColor(Color.rgb(70, 170, 220));//タイトルバーの色を変更
            recordPanel.setVisibility(View.GONE);//記録用のボタンを非表示に
            roadEditPanel.setVisibility(View.VISIBLE);//色分け用のボタンを表示
            isRecording = false;
        } else if (mode == AppMode.RECORDING) {//記録中の場合
            titleBar.setText("記録中");//タイトルバーの表記を変更
            recordPanel.setVisibility(View.VISIBLE);//記録と保存のボタンを表示
            roadEditPanel.setVisibility(View.GONE);//色分け用のボタンを非表示に
        }
    }

    private void loadHistoryList() {
        historyList.removeAllViews();//リストの初期化

        File routeDir = new File(getFilesDir(), "routes");//ディレクトリの読み込み

        if (!routeDir.exists()) {//ない場合は「保存された走行履歴はありません」と表示
            TextView emptyText = new TextView(this);
            emptyText.setText("保存された走行履歴はありません");
            emptyText.setTextSize(18);
            historyList.addView(emptyText);
            return;
        }

        File[] files = routeDir.listFiles();//ディレクトリ内のファイルを読み込み

        if (files == null || files.length == 0) {//ない場合は「保存された走行履歴はありません」と表示
            TextView emptyText = new TextView(this);
            emptyText.setText("保存された走行履歴はありません");
            emptyText.setTextSize(18);
            historyList.addView(emptyText);
            return;
        }

        for (File file : files) {
            if (!file.getName().endsWith(".json")) {//ファイルの拡張子が.json出ない場合はスキップ
                continue;
            }

            try {//ファイルの中身を見る
                String jsonText = readTextFile(file);
                JSONObject json = new JSONObject(jsonText);
                //見出しに使う譲歩を取得
                long start = json.getLong("startTime");//記録開始時刻
                long end = json.getLong("endTime");//記録終了時刻
                double distance = json.getDouble("totalDistance");//走行距離

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
                //ボタンを作成(タイトル、走行距離、走行時間を表示)押されるとその履歴のルート等を見れるようになります
                Button historyButton = new Button(this);
                historyButton.setText(
                        dateText + "\n" +
                                distanceText + "\n" +
                                timeText
                );
                //押されたときの処理
                historyButton.setOnClickListener(v -> {
                    loadRouteOnHistoryMap(file);//ファイルを読み込み走行履歴を表示
                    changeMode(AppMode.HISTORY_DETAIL);//モードを変更
                });

                historyList.addView(historyButton);//listにボタンを追加

            } catch (Exception e) {//エラー処理
                e.printStackTrace();
            }
        }
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

            historyRouteLine.setPoints(geoPoints);//点をマップに追加

            if (!geoPoints.isEmpty()) {
                historyMap.getController().setZoom(18.0);
                historyMap.getController().animateTo(geoPoints.get(0));
            }

            historyMap.invalidate();//表示

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "履歴の読み込みに失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

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
            JSONObject rootJson = new JSONObject();
            JSONArray segmentsArray = new JSONArray();

            for (RoadSegment segment : roadSegments) {
                JSONObject segmentJson = new JSONObject();

                segmentJson.put("type", segment.type.name());
                segmentJson.put("memo", segment.memo);

                JSONArray pointsArray = new JSONArray();

                for (GeoPoint p : segment.points) {
                    JSONObject pointJson = new JSONObject();
                    pointJson.put("lat", p.getLatitude());
                    pointJson.put("lon", p.getLongitude());
                    pointsArray.put(pointJson);
                }

                segmentJson.put("points", pointsArray);
                segmentsArray.put(segmentJson);
            }

            rootJson.put("segments", segmentsArray);

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
            File file = new File(getFilesDir(), "roads.json");

            if (!file.exists()) {
                return;
            }

            String jsonText = readTextFile(file);
            JSONObject rootJson = new JSONObject(jsonText);
            JSONArray segmentsArray = rootJson.getJSONArray("segments");

            // 既存の色分け線を一度消す
            for (Polyline line : roadLines) {
                map.getOverlays().remove(line);
            }

            roadLines.clear();
            roadSegments.clear();

            for (int i = 0; i < segmentsArray.length(); i++) {
                JSONObject segmentJson = segmentsArray.getJSONObject(i);

                String typeText = segmentJson.getString("type");
                RoadType type = RoadType.valueOf(typeText);

                RoadSegment segment = new RoadSegment(type);
                segment.memo = segmentJson.optString("memo", "");

                JSONArray pointsArray = segmentJson.getJSONArray("points");

                for (int j = 0; j < pointsArray.length(); j++) {
                    JSONObject pointJson = pointsArray.getJSONObject(j);

                    double lat = pointJson.getDouble("lat");
                    double lon = pointJson.getDouble("lon");

                    segment.points.add(new GeoPoint(lat, lon));
                }

                roadSegments.add(segment);

                Polyline line = new Polyline();
                line.setPoints(new ArrayList<>(segment.points));
                line.setColor(getColorByRoadType(segment.type));
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

    //選択の関数
    private void selectRoadSegment(int index) {
        clearSelectedRoadSegment();

        selectedRoadIndex = index;

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
        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;

        Point tapScreenPoint = new Point();
        map.getProjection().toPixels(tapPoint, tapScreenPoint);

        for (int i = 0; i < roadSegments.size(); i++) {
            RoadSegment segment = roadSegments.get(i);

            if (segment.points.size() < 2) {
                continue;
            }

            for (int j = 0; j < segment.points.size() - 1; j++) {
                Point p1 = new Point();
                Point p2 = new Point();

                map.getProjection().toPixels(segment.points.get(j), p1);
                map.getProjection().toPixels(segment.points.get(j + 1), p2);

                double distance = distancePointToSegment(
                        tapScreenPoint.x,
                        tapScreenPoint.y,
                        p1.x,
                        p1.y,
                        p2.x,
                        p2.y
                );

                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestIndex = i;
                }
            }
        }

        // 画面上でこの距離以内なら選択できる
        double threshold = 40.0;

        if (nearestDistance <= threshold) {
            return nearestIndex;
        }

        return -1;
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

        if (dx == 0 && dy == 0) {
            double diffX = px - x1;
            double diffY = py - y1;
            return Math.sqrt(diffX * diffX + diffY * diffY);
        }

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
        EditText editText = new EditText(this);
        editText.setHint("例：道が狭い、車が多い、夜暗い など");
        editText.setMinLines(3);
        editText.setSingleLine(false);

        if (editingRoad != null && editingRoad.memo != null) {
            editText.setText(editingRoad.memo);
        }

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
}