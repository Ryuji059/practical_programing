package jp.ac.gifu_u.info.katsuya.prog;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume(){
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO
            );
            return;
        }

        recordAndShowAmplitude();
    }
    @androidx.annotation.RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void recordAndShowAmplitude() {
        final int FREQUENCY = 8000;

        int bufsize = AudioRecord.getMinBufferSize(
                FREQUENCY,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (bufsize <= 0) {
            Toast.makeText(this, "バッファサイズ取得失敗", Toast.LENGTH_SHORT).show();
            return;
        }

        short[] buf = new short[bufsize];

        AudioRecord rec = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                FREQUENCY,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufsize
        );

        rec.startRecording();

        rec.read(buf, 0, buf.length);
        int datasize = rec.read(buf, 0, buf.length);

        int max = 0;
        for (int i = 0; i < datasize; i++) {
            int value = Math.abs(buf[i]);
            if (value > max) {
                max = value;
            }
        }

        Toast.makeText(this, Integer.toString(max), Toast.LENGTH_SHORT).show();

        rec.stop();
        rec.release();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {

                recordAndShowAmplitude();

            } else {
                Toast.makeText(this, "録音権限が必要です", Toast.LENGTH_SHORT).show();
            }
        }
    }
}