package jp.ac.gifu_u.info.katsuya.prog;

import android.content.Context;

import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

public class CameraView extends SurfaceView implements SurfaceHolder.Callback{
    // カメラを表すオブジェクト
    private Camera cam;
    // 画面（サーフェス）をホールドするためのオブジェクト
    private SurfaceHolder holder;

    // コンストラクタ
    public CameraView(Context context) {
        super(context);
        // ホルダーのコールバックに関する設定
        holder = getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // プレビューを開始
        cam.startPreview();
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // カメラを起動しプレビューをサーフェスに表示するよう指定
        cam = Camera.open(0);
        try { cam.setPreviewDisplay(holder); }
        catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
// コールバックを解除、プレビューを停止しカメラを終了
        cam.setPreviewCallback(null);
        cam.stopPreview();
        cam.release();
        cam = null;    }
}
