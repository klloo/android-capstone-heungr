package com.midisheetmusic;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.yarolegovich.discretescrollview.DSVOrientation;
import com.yarolegovich.discretescrollview.DiscreteScrollView;
import com.yarolegovich.discretescrollview.InfiniteScrollAdapter;
import com.yarolegovich.discretescrollview.transform.ScaleTransformer;

import java.io.File;
import java.util.ArrayList;

import es.dmoral.toasty.Toasty;

public class MainActivity extends AppCompatActivity implements DiscreteScrollView.OnItemChangedListener{

    public  static Context mContext;

    private DiscreteScrollView itemPicker;
    private InfiniteScrollAdapter infiniteAdapter;
    ArrayList<Data> data = new ArrayList<>();

    Data currentData = null;


    public void setAlbum(){

        data = new ArrayList<>();
        String path = Environment.getExternalStorageDirectory().getAbsolutePath()+"/Capstone";
        File directory = new File(path);

        if(directory.exists()) {
            File[] files = directory.listFiles();
            for(int i=0;i<files.length;i++) {
                File[] tmp = files[i].listFiles();
                int n = 0;
                if(tmp != null)
                    n = tmp.length;
                if(files[i].getName().equals("banju"))
                    continue;
                data.add(new Data(files[i].getName(), n));
            }

        }

        itemPicker = (DiscreteScrollView) findViewById(R.id.item_picker);
        itemPicker.setOrientation(DSVOrientation.HORIZONTAL);
        itemPicker.addOnItemChangedListener(this);
        infiniteAdapter = InfiniteScrollAdapter.wrap(new AlbumAdaptor(data));
        itemPicker.setAdapter(infiniteAdapter);
        itemPicker.setItemTransformer(new ScaleTransformer.Builder()
                .setMinScale(0.8f)
                .build());

    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toasty.Config.reset();
        mContext = this;
        setAlbum();
        //폴더 추가 버튼
        Button addBtn = findViewById(R.id.addBtn);
        //퀵버튼
        Button quickBtn = findViewById(R.id.quickBtn);

        Button start = findViewById(R.id.gogobtn);



        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //폴더 생성하는 팝업
                Intent intent = new Intent(getApplicationContext(), AddFolderPopupActivity.class);
                startActivityForResult(intent, 1);
            }
        });
        quickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), SetFileNameActivity.class);
                intent.putExtra("folderName","Quick");
                startActivity(intent);
            }
        });
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(currentData == null){
                    // Notification ?


                }
                else{
                    Intent intent = new Intent(getApplicationContext(),ChooseSongActivity.class);

                    intent.putExtra("folderName",currentData.title);
                    startActivity(intent);

                }
            }
        });

    }

    //새로 추가한 폴더명과 사진을 리스트에 추가하기
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                setAlbum();
                Toasty.custom(this, "폴더를 생성했습니다", R.drawable.music_96, R.color.Greenery,  Toast.LENGTH_SHORT, true, true).show();


            }
            else if( resultCode == RESULT_CANCELED ){
                //존재합니다 알림
                Toasty.custom(this, "폴더를 생성하지 못하였습니다", R.drawable.music_96, R.color.Faded_Denim,  Toast.LENGTH_SHORT, true, true).show();
            }
        }
    }


    private void onItemChanged(Data item) {
        currentData = item;
    }
    @Override
    public void onCurrentItemChanged(@Nullable RecyclerView.ViewHolder viewHolder, int position) {
        int positionInDataSet = infiniteAdapter.getRealPosition(position);
        onItemChanged(data.get(positionInDataSet));
    }
}


class Data{
    String title;
    int tracknum;
    Data(String title,int tracknum){
        this.title = title;
        this.tracknum = tracknum;
    }

    public String getTitle() {
        return title;
    }

    public int getTracknum() {
        return tracknum;
    }
}


