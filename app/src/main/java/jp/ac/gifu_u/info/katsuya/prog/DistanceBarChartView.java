package jp.ac.gifu_u.info.katsuya.prog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 走行距離を棒グラフとして表示するための自作View
 *
 * MainActivityからラベルと距離データを受け取り、
 * Canvasを使って軸、補助線、棒、文字を描画する。
 */
public class DistanceBarChartView extends View {

    //縦軸・横軸を描画するためのPaint
    private final Paint axisPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    //棒グラフの棒を描画するためのPaint
    private final Paint barPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    //距離、ラベル、単位などの文字を描画するためのPaint
    private final Paint textPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    //横方向の補助線を描画するためのPaint
    private final Paint gridPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    //横軸に表示する年、月、日などのラベル
    private List<String> labels =
            new ArrayList<>();

    //各ラベルに対応する走行距離。単位はkm
    private List<Double> values =
            new ArrayList<>();

    /**
     * JavaコードからViewを生成するときに呼ばれるコンストラクタ
     */
    public DistanceBarChartView(
            Context context
    ) {
        super(context);

        //描画に使うPaintの初期設定
        initialize();
    }

    /**
     * XMLレイアウトからViewを生成するときに呼ばれるコンストラクタ
     */
    public DistanceBarChartView(
            Context context,
            AttributeSet attrs
    ) {
        super(context, attrs);

        //描画に使うPaintの初期設定
        initialize();
    }

    /**
     * XMLのスタイル属性も含めてViewを生成するときに呼ばれるコンストラクタ
     */
    public DistanceBarChartView(
            Context context,
            AttributeSet attrs,
            int defStyleAttr
    ) {
        super(
                context,
                attrs,
                defStyleAttr
        );

        //描画に使うPaintの初期設定
        initialize();
    }

    /**
     * グラフ描画に使う色、線の太さ、文字サイズなどを設定する
     */
    private void initialize() {
        //軸の色と太さを設定
        axisPaint.setColor(
                Color.DKGRAY
        );
        axisPaint.setStrokeWidth(
                dp(1.5f)
        );

        //棒グラフの色を設定
        barPaint.setColor(
                Color.rgb(
                        92,
                        107,
                        192
                )
        );

        //文字の色、サイズ、基準位置を設定
        textPaint.setColor(
                Color.DKGRAY
        );
        textPaint.setTextSize(
                dp(11)
        );
        textPaint.setTextAlign(
                Paint.Align.CENTER
        );

        //補助線の色と太さを設定
        gridPaint.setColor(
                Color.LTGRAY
        );
        gridPaint.setStrokeWidth(
                dp(1)
        );
    }

    /**
     * 棒グラフに表示するデータを設定する
     *
     * labelsとvaluesは同じ添字のデータ同士が対応する。
     * 例：labels[0]が「1月」、values[0]が1月の走行距離
     */
    public void setData(
            List<String> labels,
            List<Double> values
    ) {
        /*
         * 呼び出し元のリストをそのまま保持せず、
         * 新しいArrayListへコピーして保存する。
         */
        this.labels =
                new ArrayList<>(labels);

        this.values =
                new ArrayList<>(values);

        //データ変更後にViewの再描画を要求
        invalidate();
    }

    /**
     * 現在表示しているグラフデータをすべて削除する
     */
    public void clearData() {
        labels.clear();
        values.clear();

        //データを消した状態で再描画
        invalidate();
    }

    /**
     * Viewの内容をCanvasへ描画する処理
     *
     * invalidate()が呼ばれたときや、
     * 初めて画面に表示されるときなどにAndroidから呼ばれる。
     */
    @Override
    protected void onDraw(
            Canvas canvas
    ) {
        //親クラスの基本描画処理を実行
        super.onDraw(canvas);

        //現在のView全体の幅と高さを取得
        float width =
                getWidth();

        float height =
                getHeight();

        /*
         * グラフの周囲に確保する余白
         *
         * 左：縦軸の目盛り
         * 下：横軸ラベル
         * 上：棒の上に表示する数値
         */
        float leftMargin =
                dp(48);

        float rightMargin =
                dp(12);

        float topMargin =
                dp(20);

        float bottomMargin =
                dp(48);

        //実際にグラフを描く範囲を計算
        float chartLeft =
                leftMargin;

        float chartRight =
                width - rightMargin;

        float chartTop =
                topMargin;

        float chartBottom =
                height - bottomMargin;

        /*
         * データがない場合はグラフを描かず、
         * 中央にメッセージを表示する。
         */
        if (labels.isEmpty()
                || values.isEmpty()) {

            textPaint.setTextSize(
                    dp(15)
            );

            textPaint.setTextAlign(
                    Paint.Align.CENTER
            );

            canvas.drawText(
                    "走行データがありません",
                    width / 2,
                    height / 2,
                    textPaint
            );

            return;
        }

        /*
         * 最も大きい走行距離を探す。
         * この値を縦軸の最大値として使用する。
         */
        double maxValue = 0.0;

        for (double value : values) {
            if (value > maxValue) {
                maxValue = value;
            }
        }

        /*
         * 全データが0kmでも、
         * 0で割らずにグラフを描けるよう最大値を1にする。
         */
        if (maxValue <= 0.0) {
            maxValue = 1.0;
        }

        /*
         * 横方向の補助線と縦軸の数値を描画する。
         *
         * gridCountが4の場合、
         * 0%, 25%, 50%, 75%, 100%の5本を描く。
         */
        int gridCount = 4;

        textPaint.setTextSize(
                dp(10)
        );

        //目盛りの文字を縦軸の左側へ揃える
        textPaint.setTextAlign(
                Paint.Align.RIGHT
        );

        for (int i = 0;
             i <= gridCount;
             i++) {

            //現在の補助線が最大値の何割の位置かを求める
            float ratio =
                    i / (float) gridCount;

            /*
             * Canvasのy座標は下方向ほど大きくなるため、
             * chartBottomから上方向へ移動してy座標を求める。
             */
            float y =
                    chartBottom
                            - (chartBottom - chartTop)
                            * ratio;

            //横方向の補助線を描画
            canvas.drawLine(
                    chartLeft,
                    y,
                    chartRight,
                    y,
                    gridPaint
            );

            //この補助線に対応する距離を計算
            double distanceValue =
                    maxValue * ratio;

            //縦軸の距離目盛りを表示
            canvas.drawText(
                    String.format(
                            Locale.JAPAN,
                            "%.1f",
                            distanceValue
                    ),
                    chartLeft - dp(6),
                    y + dp(4),
                    textPaint
            );
        }

        /*
         * 縦軸を描画
         * chartLeftの位置で上から下へ線を引く。
         */
        canvas.drawLine(
                chartLeft,
                chartTop,
                chartLeft,
                chartBottom,
                axisPaint
        );

        /*
         * 横軸を描画
         * chartBottomの位置で左から右へ線を引く。
         */
        canvas.drawLine(
                chartLeft,
                chartBottom,
                chartRight,
                chartBottom,
                axisPaint
        );

        //棒を並べられる横方向の幅を計算
        float availableWidth =
                chartRight - chartLeft;

        //1項目あたりに割り当てる横幅を計算
        float itemWidth =
                availableWidth
                        / values.size();

        //棒の幅を1項目分の60%に設定
        float barWidth =
                itemWidth * 0.6f;

        //棒の上の値と横軸ラベルは中央揃え
        textPaint.setTextAlign(
                Paint.Align.CENTER
        );

        textPaint.setTextSize(
                dp(10)
        );

        //各データについて棒、値、ラベルを描画
        for (int i = 0;
             i < values.size();
             i++) {

            //i番目の走行距離を取得
            double value =
                    values.get(i);

            /*
             * 最大値に対する割合から棒の高さを計算する。
             *
             * 最大値と同じ値ならグラフ領域いっぱいの高さになる。
             */
            float barHeight =
                    (float) (
                            value / maxValue
                                    * (chartBottom
                                    - chartTop)
                    );

            //i番目の項目の中心となるx座標
            float centerX =
                    chartLeft
                            + itemWidth * i
                            + itemWidth / 2;

            //棒の左端
            float barLeft =
                    centerX
                            - barWidth / 2;

            //棒の右端
            float barRight =
                    centerX
                            + barWidth / 2;

            //棒の上端。値が大きいほど上側になる
            float barTop =
                    chartBottom
                            - barHeight;

            //棒グラフの長方形を描画
            canvas.drawRect(
                    barLeft,
                    barTop,
                    barRight,
                    chartBottom,
                    barPaint
            );

            /*
             * 棒の上に実際の距離を表示する。
             *
             * Math.maxにより、文字がグラフ領域の上へ
             * はみ出しすぎないよう制限している。
             */
            canvas.drawText(
                    String.format(
                            Locale.JAPAN,
                            "%.1f",
                            value
                    ),
                    centerX,
                    Math.max(
                            barTop - dp(5),
                            chartTop + dp(10)
                    ),
                    textPaint
            );

            //棒の下に年、月、日などの横軸ラベルを表示
            canvas.drawText(
                    labels.get(i),
                    centerX,
                    chartBottom + dp(18),
                    textPaint
            );
        }

        //縦軸の単位「km」を左上へ表示
        textPaint.setTextAlign(
                Paint.Align.LEFT
        );

        textPaint.setTextSize(
                dp(10)
        );

        canvas.drawText(
                "km",
                dp(4),
                chartTop + dp(5),
                textPaint
        );
    }

    /**
     * dpを端末の画面密度に応じたピクセル値へ変換する
     *
     * 端末ごとに画面密度が異なっても、
     * 文字や線の見た目の大きさをそろえるために使用する。
     */
    private float dp(
            float value
    ) {
        return value
                * getResources()
                .getDisplayMetrics()
                .density;
    }
}