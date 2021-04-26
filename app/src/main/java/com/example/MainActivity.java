package com.example;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.camera2.Camera2Activity;
import com.example.camerax.CameraXActivity;
import com.example.camerax.R;


//saus: https://codelabs.developers.google.com/codelabs/camerax-getting-started/

public class MainActivity extends AppCompatActivity implements View.OnClickListener {



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_camera2).setOnClickListener(this);
        findViewById(R.id.btn_cameraX).setOnClickListener(this);

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btn_camera2:
                startActivity(new Intent(this, Camera2Activity.class));
                break;
            case R.id.btn_cameraX:
                startActivity(new Intent(this, CameraXActivity.class));
                break;
        }
    }
}
