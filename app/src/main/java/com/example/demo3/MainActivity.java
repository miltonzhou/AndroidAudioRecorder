package com.example.demo3;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.example.nslib.nsUtil;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    //Initialize parameter
    public static final String EXTRA_MESSAGE = "com.example.demo3.caption";
    public static final String TAG = "AudioRecorder";
    private boolean isRecording = false;
    private Button startAudio;
    private Button stopAudio;
    private Button playAudio;
    private ScrollView mScrollView;
    private TextView tv_audio_success;
    private File file;
//    private File nrfile;

    static {
        System.loadLibrary("native-lib");
    }

    // Requesting permission to RECORD_AUDIO
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();
    }

    //OnCreate
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        //Initial layout view
        initView();

        //Noise reduction
        String inp = "/storage/emulated/0/Android/data/com.example.demo3/cache/audio_raw.pcm";
        String out = "/storage/emulated/0/Android/data/com.example.demo3/cache/audio_nr.pcm";
        nsUtil.nsProcess(inp, out);

    }

    //Initialize View
    private void initView() {

        mScrollView = (ScrollView) findViewById(R.id.mScrollView);
        tv_audio_success = (TextView) findViewById(R.id.tv_audio_succeess);
        printLog("Initialization successful");
        startAudio = (Button) findViewById(R.id.startAudio);
        startAudio.setOnClickListener(this);
        stopAudio = (Button) findViewById(R.id.stopAudio);
        stopAudio.setOnClickListener(this);
        playAudio = (Button) findViewById(R.id.playAudio);
        playAudio.setOnClickListener(this);
    }

    //Click event
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startAudio:
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        StartRecord();
                        Log.e(TAG,"start");
                    }
                });
                thread.start();
                printLog("start recording");
                ButtonEnabled(false, true, false);
                break;
            case R.id.stopAudio:
                isRecording = false;
                ButtonEnabled(true, false, true);
                printLog("Stop recording");
                break;
            case R.id.playAudio:
                PlayRecord();
                ButtonEnabled(true, false, false);
                printLog("Play recording");
                break;
            case R.id.confirm:
                Intent intent = new Intent(this,FinalResultActivity.class);
                EditText editText = (EditText)findViewById(R.id.edtTxtCaption);
                String caption = editText.getText().toString();
                intent.putExtra(EXTRA_MESSAGE,caption);
                startActivity(intent);
                break;
        }
    }

    //Print log
    private void printLog(final String resultString) {
        tv_audio_success.post(new Runnable() {
            @Override
            public void run() {
                tv_audio_success.append(resultString + "\n");
                mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    //Enable or disable buttons
    private void ButtonEnabled(boolean start, boolean stop, boolean play) {
        startAudio.setEnabled(start);
        stopAudio.setEnabled(stop);
        playAudio.setEnabled(play);
    }

    //start recording
    public void StartRecord() {
        Log.i(TAG,"start recording");
        //Sample rate in Hz
        int frequency = 16000;
        //Channel configuration
        int channelConfiguration = AudioFormat.CHANNEL_IN_STEREO;
        //Audio Format
        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

        //Generate PCM file
        file = new File(getExternalCacheDir().getAbsolutePath() + "/audio_raw.pcm");
        Log.i(TAG,"Generate file");

        //If it exists, delete and then create
        if (file.exists()) {
            file.delete();
        }
        Log.i(TAG,"Delete Files");
        try {
            file.createNewFile();
            Log.i(TAG,"Create a file");
            Log.i(TAG,"File at:"+file.toString());
        } catch (IOException e) {
            Log.i(TAG,"Failed to create");
            throw new IllegalStateException("Failed to create" + file.toString());
        }

        try {
            //Output stream
            OutputStream os = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);
            int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize);

            short[] buffer = new short[bufferSize];
            audioRecord.startRecording();
            Log.i(TAG, "start recording");
            isRecording = true;
            while (isRecording) {
                int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
                for (int i = 0; i < bufferReadResult; i++) {
                    dos.writeShort(buffer[i]);
                }
            }
            audioRecord.stop();
            dos.close();
        } catch (Throwable t) {
            Log.e(TAG, "Recording failed");
        }
    }

    //Play file
    public void PlayRecord() {

//        nrfile = new File("/storage/emulated/0/Android/data/com.example.demo3/cache/audio_nr.pcm");
//        (change file to nrfile if wanted to play the noise reduced file)

        if(file == null){
            return;
        }
        //Read file
        int musicLength = (int) (file.length() / 2);
        short[] music = new short[musicLength];
        try {
            InputStream is = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(is);
            DataInputStream dis = new DataInputStream(bis);
            int i = 0;
            while (dis.available() > 0) {
                music[i] = dis.readShort();
                i++;
            }
            dis.close();
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    16000, AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    musicLength * 2,
                    AudioTrack.MODE_STREAM);
            audioTrack.play();
            Log.i(TAG,"File played:"+file.toString());
            audioTrack.write(music, 0, musicLength);
            audioTrack.stop();
        } catch (Throwable t) {
            Log.e(TAG, "Playing failed");
        }
    }

}
