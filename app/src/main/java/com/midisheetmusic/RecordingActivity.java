package com.midisheetmusic;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static java.lang.Math.pow;

public class RecordingActivity extends AppCompatActivity implements
        RecordingSampler.CalculateVolumeListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final int RequestPermissionCode = 1;

    // Recording Info
    private RecordingSampler mRecordingSampler;

    // View
    private VisualizerView mVisualizerView;
    private VisualizerView mVisualizerView2;
    private VisualizerView mVisualizerView3;
    private FloatingActionButton mFloatingActionButton;


    //Metronome
    ReadyThread readyThread = new ReadyThread();
    MetronomeThread metronomeThread; //= new MetronomeThread();
    Spinner spinner;
    ArrayList<Integer> arr = new ArrayList<>();
    ImageView imageview;
    ImageView countview;
    SoundPool soundPool;
    int clap;
    public int[] countArray = {R.drawable.count3, R.drawable.count2, R.drawable.count1};


    //tarsoDSP
    TarsosDSPAudioFormat tarsosDSPAudioFormat;
    AudioDispatcher dispatcher;
    File file;
    TextView pitchTextView;
    String filename = "recorded_sound.wav";


    int spinnerBPM = 60;
    int count = 0;
    int sampleNumber = 0;
    long startTime;
    long now;
    long length;
    int gap=0;



    // Pitch Detection data
    ArrayList<Double> humming = new ArrayList<>();

    //MidiFile 생성을 위함
    static final int DEMISEMIQUAVER = 2; //32분음표(반의반의반박)
    static final int SEMIQUAVER = 4; //16분음표 (반의 반박)
    static final int QUAVER = 8; //8분음표 (|)
    static final int CROTCHET = 16; //4분음표 (V)
    static final int MINIM = 32; //2분음표 (VV)
    static final int SEMIBREVE = 64; //온음표 (VVVV)
    private Context context;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);

        soundPool = new SoundPool(1,AudioManager.STREAM_MUSIC, 0);
        clap = soundPool.load(this, R.raw.clap, 1);

        //tarsoDSP 객체 설정
        tarsosDSPAudioFormat=new TarsosDSPAudioFormat(
                TarsosDSPAudioFormat.Encoding.PCM_SIGNED, //encoding형식
                22050, //sampleRate
                2 * 8, // SampleSizeInBit
                1, // Channels
                2 * 1, // frameSize
                22050*2,//frameRate
                ByteOrder.BIG_ENDIAN.equals(ByteOrder.nativeOrder()) // 바이트 순서 형식
        );

        pitchTextView = findViewById(R.id.pitchTextView);
        File sdCard = Environment.getExternalStorageDirectory();
        file = new File(sdCard, filename);

        countview = findViewById(R.id.countView);
        imageview = findViewById(R.id.imageView);
        if( countview == null){
            Log.d("TAG","countView is NULL");
        }
        else{

            Log.d("TAG", "countView  "+countview);

            readyThread.setImageView( (ImageView) countview);
        }

        {
            mVisualizerView = (VisualizerView) findViewById(R.id.visualizer);
            ViewTreeObserver observer = mVisualizerView.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mVisualizerView.setBaseY(mVisualizerView.getHeight());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {

                        mVisualizerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    } else {
                        mVisualizerView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    }
                }
            });
        }
        {
            mVisualizerView2 = (VisualizerView) findViewById(R.id.visualizer2);
            ViewTreeObserver observer = mVisualizerView2.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mVisualizerView2.setBaseY(mVisualizerView2.getHeight() / 5);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        mVisualizerView2.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    } else {
                        mVisualizerView2.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    }
                }
            });
        }
        mVisualizerView3 = (VisualizerView) findViewById(R.id.visualizer3);
        mFloatingActionButton = (FloatingActionButton) findViewById(R.id.fab);

        // create AudioRecord
        mRecordingSampler = new RecordingSampler();
        mRecordingSampler.setVolumeListener(this);
        mRecordingSampler.setSamplingInterval(100);
        mRecordingSampler.link(mVisualizerView);
        mRecordingSampler.link(mVisualizerView2);
        mRecordingSampler.link(mVisualizerView3);


        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d("TAG", "Icon Clicked");
                if(checkPermission()) {

                    Log.d("TAG", "OK");
                    if (mRecordingSampler.isRecording()) {

                        //녹음 끝나고 바로 넘길거면 여기다가 다음 액티비티로 넘기는 코드 넣으면 됨 그리고 어떤 파일 형식을 원하는지 몰라서 일단 놔뒀음 RecordingSampler 함수에서 뭐 getAudioSource이런거 만들어서 넘기면 될듯


                        mFloatingActionButton.setImageResource(R.drawable.ic_mic);
                        mRecordingSampler.stopRecording();

                        //tasroDSP
                        stopRecording();

                        //메트로놈
                        if (metronomeThread.isPlaying()) {
                            metronomeThread.setPlaying(false);
                            metronomeThread.interrupt();
                            metronomeThread = null;
                        }

                        imageview.setImageResource(R.drawable.a1);



                    } else { // 녹음 시작

                        metronomeThread = new MetronomeThread();
                        metronomeThread.setBpm(spinnerBPM);
                        metronomeThread.setImageView(imageview);
                        mFloatingActionButton.setImageResource(R.drawable.ic_mic_off);


                        new CountDownTimer(3000, 1000) {

                            public void onTick(long millisUntilFinished) {

                                //  mTextField.setText("seconds remaining: " + millisUntilFinished / 1000);

                                countview.setVisibility(View.VISIBLE);
                                countview.setImageResource(countArray[count]);
                                soundPool.play(clap,1f,1f,0,0,1f);
                                count++;
                                Log.d("TAG", "Count "+ (count+1) );


                            }

                            public void onFinish() {
                                //mTextField.setText("done!");
                                // mTextField.setVisibility(View.GONE);

                                countview.setVisibility(View.GONE);

                                mRecordingSampler.startRecording();
                                //tarsoDSP
                                now= SystemClock.currentThreadTimeMillis();
                                recordAudio();


                                //메트로놈
                                metronomeThread.start();

                            }
                        }.start();



                    }


                } else {

                    Log.d("TAG", "No");
                    requestPermission();
                }





            }
        });


        //Metronome

        imageview = findViewById(R.id.imageView);


        //bpm 설정
        spinner = findViewById(R.id.bpmSpinner);
        arr.add(60);
        arr.add(80);
        arr.add(100);
        arr.add(110);
        arr.add(120);
        arr.add(130);
        ArrayAdapter<Integer> arrayAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, arr);
        spinner.setAdapter(arrayAdapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                spinnerBPM = ((Integer) parent.getSelectedItem());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });



    }

    /* 아래 함수는 frequence를 Sequence로 만들기 위한 함수들 */
    public static int freq2MidiNum(double freq){
        int MidiNum = 0;
        int octa = 0;
        int mul;
        double C , Db , D, Eb , E, F, Gb, G,Ab, A, Bb, B;
        C = 32.703;
        Db = 34.648;
        D = 36.708;
        Eb = 38.891;
        E = 41.203;
        F = 43.654;
        Gb = 46.249;
        G = 48.999;
        Ab = 51.913;
        A = 55.000;
        Bb = 58.270;
        B = 61.735;

        //계산

        //setp 1 옥타브 계산
        {
            if (C <= freq && freq < 2 * C)
                octa = 1;
            else if (2 * C <= freq && freq < 4 * C)
                octa = 2;
            else if (4 * C <= freq && freq < 8 * C)
                octa = 3;
            else if (8 * C <= freq && freq < 16 * C)
                octa = 4;
            else if (16 * C <= freq && freq < 32 * C)
                octa = 5;
            else if (32 * C <= freq && freq < 64 * C)
                octa = 6;
            else if (64 * C <= freq && freq < 128 * C)
                octa = 7;
            else if (128 * C <= freq && freq < 256 * C)
                octa = 8;

            mul = (int) pow(2, octa - 1); // 1옥타브는 C 2옥타브는 C*2 3옥타브는 C * 2^2 4옥타브는 C*2^3

            //setp 2 음정 계산

            if (freq >= (C * mul) && freq < (Db * mul)) {
                //C
                if ((freq - (C * mul)) <= ((Db * mul) - freq))
                    MidiNum = 12 * octa + 12;
                else // Db
                    MidiNum = 12 * octa + 12 + 1;
            } else if (freq >= (Db * mul) && freq < (D * mul)) {
                //Db
                if ((freq - (Db * mul)) <= ((D * mul) - freq))
                    MidiNum = 12 * octa + 12 + 1;
                else //D
                    MidiNum = 12 * octa + 12 + 2;
            } else if (freq >= (D * mul) && freq < (Eb * mul)) {
                //D
                if ((freq - (D * mul)) <= ((Eb * mul) - freq))
                    MidiNum = 12 * octa + 12 + 2;
                else // Eb
                    MidiNum = 12 * octa + 12 + 3;
            } else if (freq >= (Eb * mul) && freq < (E * mul)) {
                //Eb
                if ((freq - (Eb * mul)) <= ((E * mul) - freq))
                    MidiNum = 12 * octa + 12 + 3;
                else // E
                    MidiNum = 12 * octa + 12 + 4;
            } else if (freq >= (E * mul) && freq < (F * mul)) {
                //E
                if ((freq - (E * mul)) <= ((F * mul) - freq))
                    MidiNum = 12 * octa + 12 + 4;
                else // F
                    MidiNum = 12 * octa + 12 + 5;
            } else if (freq >= (F * mul) && freq <= (Gb * mul)) {
                //F
                if ((freq - (F * mul)) <= ((Gb * mul) - freq))
                    MidiNum = 12 * octa + 12 + 5;
                else // Gb
                    MidiNum = 12 * octa + 12 + 6;
            } else if (freq >= (Gb * mul) && freq < (G * mul)) {
                //Gb
                if ((freq - (Gb * mul)) <= ((G * mul) - freq))
                    MidiNum = 12 * octa + 12 + 6;
                else // G
                    MidiNum = 12 * octa + 12 + 7;
            } else if (freq >= (G * mul) && freq < (Ab * mul)) {
                //G
                if ((freq - (G * mul)) <= ((Ab * mul) - freq))
                    MidiNum = 12 * octa + 12 + 7;
                else // Ab
                    MidiNum = 12 * octa + 12 + 8;
            } else if (freq >= (Ab * mul) && freq < (A * mul)) {
                //Ab
                if ((freq - (Ab * mul)) <= ((A * mul) - freq))
                    MidiNum = 12 * octa + 12 + 8;
                else // A
                    MidiNum = 12 * octa + 12 + 9;
            } else if (freq >= (A * mul) && freq < (Bb * mul)) {
                //A
                if ((freq - (A * mul)) <= ((Bb * mul) - freq))
                    MidiNum = 12 * octa + 12 + 9;
                else
                    MidiNum = 12 * octa + 12 + 10;
            } else if (freq >= (Bb * mul) && freq < (B * mul)) {
                //Bb
                if ((freq - (Bb * mul)) <= ((B * mul) - freq))
                    MidiNum = 12 * octa + 12 + 10;
                else // B
                    MidiNum = 12 * octa + 12 + 11;
            } else if (freq >= (B * mul) && freq < (2 * C * mul)) {
                //B
                if ((freq - (B * mul)) <= ((2 * C * mul) - freq))
                    MidiNum = 12 * octa + 12 + 11;
                else // C
                    MidiNum = 12 * octa + 12 + 12;
            }
        }
        //       System.out.println("octa " + octa + " freq  "+freq + " || MidiNum" + MidiNum);

        return MidiNum;
    }
    //음계 변환해서 list에 담고 return하는 함수
    public static ArrayList<Integer> store( ArrayList<Double>  freq){
        ArrayList<Integer> list = new ArrayList();
        for(int i =0; i<freq.size(); i++){
            if( freq.get(i) == -1){
                list.add(0);
                continue;
            }
            list.add(freq2MidiNum(freq.get(i)));
        }
        return list;
    }
    // 녹음 된 시간 측정...
    public static double CalSec(double size){
        double sec = size/60.0;
        return  sec;
    }
    //음계저장한 리스트를 개수 뽑아서 "1차 시퀀스(음계, 개수)" 로 나타내기
    public static ArrayList<Integer> CountMidiNum(ArrayList<Integer> scalelist) {

        ArrayList<Integer> Sequence1 = new ArrayList<>();
        int curMidi;
        int count = 1;
        curMidi = scalelist.get(0);


        for(int i =0; i<scalelist.size();i++){
            if(i==0){
                Sequence1.add(curMidi);
            }

            if(curMidi != scalelist.get(i)){
                Sequence1.add(count);
                curMidi = scalelist.get(i);
                Sequence1.add(curMidi);
                count = 1;
            }
            else{
                count++;
            }

            if(i==scalelist.size()-1){
                Sequence1.add(count);
            }

        }

        if(Sequence1.get(0) == 0){
            Sequence1.remove(0);
            Sequence1.remove(0);
        }

        for(int i=1; i<Sequence1.size();i+=2){
            System.out.println("MidiNum : " + Sequence1.get(i-1) + " || Counts : "+ Sequence1.get(i));

        }

        return Sequence1; //{음계, 음계개수}
    }
    //2차 시퀀스(1차 시퀀스 정리 및 개수를 노트로 변환)
    public static ArrayList<Integer> ReturnSequence(ArrayList<Integer> Sequence1, int gap, int bpm){

        int quarter_note; //4분음표 결정개수
        int sixteenth_note;
        if(bpm ==180){
            quarter_note = 25/gap;
        }
        else if(bpm == 120) quarter_note = 50/gap;
        else quarter_note = 100/gap;//(bpm == 60)

        int white_note = quarter_note*4; //온음표
        int dot_half_note = quarter_note*3;
        int half_note = quarter_note*2; //2분음표 결정개수
        int dot_quarter_note = (int)(quarter_note*1.5);
        int dot_eighth_note = (int)(quarter_note*0.75);
        int eighth_note = quarter_note/2; //8분음표 결정개수
        sixteenth_note = quarter_note/4; //16분음표 결정개수

        ArrayList<Integer> Sequence2 = new ArrayList<Integer>();

        //Sequence2 = {음계, 개수}정리 list (16분음표보다 작은 것 합치기)
        Sequence2.add(Sequence1.get(0));
        Sequence2.add(Sequence1.get(1));
        int count = Sequence1.get(3);
        int exCount = 0;
        int k;
        for(int i = 3; i<Sequence1.size();i+=2){

            count = Sequence1.get(i);
            k = Sequence2.size();
            if(count >= sixteenth_note){
                Sequence2.add(Sequence1.get(i-1));
                Sequence2.add(count);
            }
            else{
                exCount = Sequence2.get(k-1);
                Sequence2.set(k-1, exCount + count);
                if(Sequence2.get(1) <sixteenth_note && Sequence2.size() == 4){
                    int first = Sequence2.get(1);
                    int third = Sequence2.get(3);
                    Sequence2.set(3, first + third );
                    Sequence2.remove(0);
                    Sequence2.remove(0);
                }
            }
        }
        // test2
        for (int i = 1; i < Sequence2.size(); i += 2) {
            System.out.println("Second - MidiNum:" + Sequence2.get(i - 1) + " || Counts:" + Sequence2.get(i));
        }
/*
        System.out.println("\n");

        System.out.println("온음표 개수:"+white_note+" note:"+SEMIBREVE);
        System.out.println("점2분음표 개수:"+dot_half_note+" note:"+(MINIM+CROTCHET));
        System.out.println("2분음표 개수:"+half_note+" note:"+MINIM);
        System.out.println("점4분음표 개수:"+dot_quarter_note+" note:"+(CROTCHET + QUAVER));
        System.out.println("4분음표 개수:"+quarter_note+" note:"+CROTCHET);
        System.out.println("점8분음표 개수:"+dot_eighth_note+" note:"+(QUAVER + SEMIQUAVER));
        System.out.println("8분음표 개수:"+eighth_note+" note:"+QUAVER);
        System.out.println("16분음표 개수:"+sixteenth_note+" note:"+SEMIQUAVER);
*/

        int midi;
        int i = -1;
        while(true) {
            if(i == Sequence2.size()-1){
                break;
            }
            i+=2;

            count = Sequence2.get(i);
            midi = Sequence2.get(i-1);

            if(count > (white_note + dot_half_note) / 2) { //count 온음표로 표기
                Sequence2.set(i, SEMIBREVE);
                if((count - white_note) > sixteenth_note ) {//count-온음표가 16분 음표보다 클 때, 붙임줄필요하고 다음 음표를 구한다.
                    count = (int)(count - white_note); //큰부분빼서
                    Sequence2.add(i+1,count);
                    Sequence2.add(i+1, midi);
                    continue;
                }
                continue;

            }else if(count > (dot_half_note + half_note) / 2) { //count가 점2분음표로 표기
                Sequence2.set(i, MINIM+CROTCHET);
                if((count - dot_half_note) >= sixteenth_note ) {//count-온음표가 16분 음표보다 클 때, 붙임줄필요하고 다음 음표를 구한다.
                    count = (int)(count - dot_half_note); //큰부분빼서
                    Sequence2.add(i+1, count);
                    Sequence2.add(i+1, midi);
                    continue;
                }
                continue;

            }else if(count > (half_note + dot_quarter_note) / 2){ //count 2분음표

                Sequence2.set(i, MINIM);
                if((count - half_note) >= sixteenth_note ) {//count-온음표가 16분 음표보다 클 때, 붙임줄필요하고 다음 음표를 구한다.
                    count = (int)(count - half_note); //큰부분빼서
                    Sequence2.add(i+1, count);
                    Sequence2.add(i+1, midi);
                    continue;
                }
                continue;

            }else if(count > (dot_quarter_note + quarter_note) / 2) { //점4분음표

                Sequence2.set(i, CROTCHET + QUAVER);
                if((count - dot_quarter_note) >= sixteenth_note ) {//count-온음표가 16분 음표보다 클 때, 붙임줄필요하고 다음 음표를 구한다.
                    count = (int)(count - dot_quarter_note); //큰부분빼서
                    Sequence2.add(i+1, count);
                    Sequence2.add(i+1, midi);
                    continue;
                }
                continue;

            }else if(count > (quarter_note + dot_eighth_note) / 2) { //4분음표

                Sequence2.set(i, CROTCHET);
                if((count - quarter_note) >= sixteenth_note ) {//count-온음표가 16분 음표보다 클 때, 붙임줄필요하고 다음 음표를 구한다.
                    count = (int)(count - quarter_note); //큰부분빼서
                    Sequence2.add(i+1, count);
                    Sequence2.add(i+1, midi);
                    continue;
                }
                continue;

            }else if(count > (dot_eighth_note + eighth_note) / 2) { //점 8분음표

                Sequence2.set(i, QUAVER + SEMIQUAVER);
                if((count - dot_eighth_note) >= sixteenth_note ) {//count-온음표가 16분 음표보다 클 때, 붙임줄필요하고 다음 음표를 구한다.
                    count = (int)(count - dot_eighth_note); //큰부분빼서
                    Sequence2.add(i+1, count);
                    Sequence2.add(i+1, midi);
                    continue;
                }
                continue;

            }else if(count > (eighth_note + sixteenth_note) / 2) { //8분음표

                Sequence2.set(i, QUAVER);
                if((count - eighth_note) >= sixteenth_note ) {//count-온음표가 16분 음표보다 클 때, 붙임줄필요하고 다음 음표를 구한다.
                    count = (int)(count - eighth_note); //큰부분빼서
                    Sequence2.add(i+1, count);
                    Sequence2.add(i+1, midi);
                    continue;
                }
                continue;

            }else { // 16분 음표
                Sequence2.set(i, SEMIQUAVER);
                continue;
            }

        }
        for ( i = 1; i < Sequence2.size(); i += 2) {
            //          System.out.println("Final - MidiNum:" + Sequence2.get(i - 1) + " || NoteNum:" + Sequence2.get(i));
        }

        return Sequence2;

    }


    private void requestPermission() {
        ActivityCompat.requestPermissions(RecordingActivity.this, new String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}, RequestPermissionCode);
    }

    public boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), WRITE_EXTERNAL_STORAGE);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(), RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {

        mRecordingSampler.release();

     /*   //메트로놈 정지
        metronomeThread.setPlaying(false);
        metronomeThread.interrupt();
        metronomeThread = null;
        playNstop.setChecked(false);*/
        super.onDestroy();
    }

    @Override
    public void onCalculateVolume(int volume) {
        // for custom implement
 //       Log.d(TAG, String.valueOf(volume));
    }

    public void recordAudio(){
        releaseDispatcher();
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050,1024,0);

        humming.clear();
        startTime = SystemClock.currentThreadTimeMillis();
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(file,"rw");
          //  AudioProcessor recordProcessor = new WriterProcessor(tarsosDSPAudioFormat, randomAccessFile);
           // dispatcher.addAudioProcessor(recordProcessor);

            PitchDetectionHandler pitchDetectionHandler = new PitchDetectionHandler() {
                @Override
                public void handlePitch(PitchDetectionResult res, AudioEvent e){
                    final float pitchInHz = res.getPitch();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            pitchTextView.setText(pitchInHz + "");
                            humming.add((double) pitchInHz);
                            sampleNumber++;
                            now = SystemClock.currentThreadTimeMillis();

                        }
                    });
                }
            };

            //Algorithm 체크 해야할듯함 ( 잡음 제거라던지..)
            AudioProcessor pitchProcessor = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, pitchDetectionHandler);
            dispatcher.addAudioProcessor(pitchProcessor);

            Thread audioThread = new Thread(dispatcher, "Audio Thread");
            audioThread.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopRecording(){
        releaseDispatcher();
        length = SystemClock.currentThreadTimeMillis() - startTime;
        Log.d("TAG",sampleNumber +"개의 sample");
        Log.d("TAG",length+"초 지남");


        gap = (int)(length/sampleNumber);

        int line = 0;
        for(int i = 0 ; i < humming.size() ; i++){

            System.out.print(humming.get(i) +",");
            if( line == 20 ){
                line = 0;
                System.out.println("");
            }
            line ++;

        }



        //MidiFile 생성
        MidiFileMaker midiFileMaker = new MidiFileMaker();
        ArrayList<Integer> scalelist = store(humming);
        ArrayList<Integer> sequence1 = CountMidiNum(scalelist);

        ArrayList<Integer> sequence = ReturnSequence(sequence1, gap, spinnerBPM );


        midiFileMaker.setTempo(60);
        midiFileMaker.setTimeSignature(4,4);
        midiFileMaker.noteSequenceFixedVelocity (sequence, 127);


        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Capstone");
        if(!dir.exists()){
            dir.mkdirs();
        }

        File file = new File(dir, "file.mid") ;
        midiFileMaker.writeToFile (file);





        Uri uri = Uri.parse(file.getPath());

        FileUri fileUri = new FileUri(uri, file.getPath());

        Intent intent = new Intent(Intent.ACTION_VIEW, fileUri.getUri() , this, SheetMusicActivity.class);
        intent.putExtra(SheetMusicActivity.MidiTitleID, file.toString());

        startActivity(intent);

    }

    public void releaseDispatcher(){
        if(dispatcher != null)
        {
            if(!dispatcher.isStopped())
                dispatcher.stop();
            dispatcher = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releaseDispatcher();
    }

}
