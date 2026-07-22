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

public class DistanceBarChartView extends View {

    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<String> labels = new ArrayList<>();
    private List<Double> values = new ArrayList<>();

    public DistanceBarChartView(Context context) {
        super(context);
        initialize();
    }

    public DistanceBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public DistanceBarChartView(
            Context context,
            AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        axisPaint.setColor(Color.DKGRAY);
        axisPaint.setStrokeWidth(dp(1.5f));

        barPaint.setColor(Color.rgb(92, 107, 192));

        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(dp(11));
        textPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(dp(1));
    }

    public void setData(
            List<String> labels,
            List<Double> values
    ) {
        this.labels = new ArrayList<>(labels);
        this.values = new ArrayList<>(values);

        invalidate();
    }

    public void clearData() {
        labels.clear();
        values.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        float leftMargin = dp(48);
        float rightMargin = dp(12);
        float topMargin = dp(20);
        float bottomMargin = dp(48);

        float chartLeft = leftMargin;
        float chartRight = width - rightMargin;
        float chartTop = topMargin;
        float chartBottom = height - bottomMargin;

        if (labels.isEmpty() || values.isEmpty()) {
            textPaint.setTextSize(dp(15));
            textPaint.setTextAlign(Paint.Align.CENTER);

            canvas.drawText(
                    "走行データがありません",
                    width / 2,
                    height / 2,
                    textPaint
            );

            return;
        }

        double maxValue = 0.0;

        for (double value : values) {
            if (value > maxValue) {
                maxValue = value;
            }
        }

        /*
         * 全部0の場合でもグラフを描けるようにする
         */
        if (maxValue <= 0.0) {
            maxValue = 1.0;
        }

        // 横方向の補助線と距離表示
        int gridCount = 4;

        textPaint.setTextSize(dp(10));
        textPaint.setTextAlign(Paint.Align.RIGHT);

        for (int i = 0; i <= gridCount; i++) {
            float ratio = i / (float) gridCount;

            float y =
                    chartBottom
                            - (chartBottom - chartTop) * ratio;

            canvas.drawLine(
                    chartLeft,
                    y,
                    chartRight,
                    y,
                    gridPaint
            );

            double distanceValue =
                    maxValue * ratio;

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

        // 縦軸・横軸
        canvas.drawLine(
                chartLeft,
                chartTop,
                chartLeft,
                chartBottom,
                axisPaint
        );

        canvas.drawLine(
                chartLeft,
                chartBottom,
                chartRight,
                chartBottom,
                axisPaint
        );

        float availableWidth =
                chartRight - chartLeft;

        float itemWidth =
                availableWidth / values.size();

        float barWidth =
                itemWidth * 0.6f;

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(10));

        for (int i = 0; i < values.size(); i++) {
            double value = values.get(i);

            float barHeight =
                    (float) (
                            value / maxValue
                                    * (chartBottom - chartTop)
                    );

            float centerX =
                    chartLeft
                            + itemWidth * i
                            + itemWidth / 2;

            float barLeft =
                    centerX - barWidth / 2;

            float barRight =
                    centerX + barWidth / 2;

            float barTop =
                    chartBottom - barHeight;

            canvas.drawRect(
                    barLeft,
                    barTop,
                    barRight,
                    chartBottom,
                    barPaint
            );

            // 棒の上に距離を表示
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

            // 横軸ラベル
            canvas.drawText(
                    labels.get(i),
                    centerX,
                    chartBottom + dp(18),
                    textPaint
            );
        }

        // 縦軸単位
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(dp(10));

        canvas.drawText(
                "km",
                dp(4),
                chartTop + dp(5),
                textPaint
        );
    }

    private float dp(float value) {
        return value
                * getResources()
                .getDisplayMetrics()
                .density;
    }
}