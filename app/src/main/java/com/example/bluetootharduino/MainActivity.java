package com.example.bluetootharduino;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
/*
 * Konstantinos Knais 8967
 * Android application used in my thesis - 7 lead holter monitor
 * To be used along with the designed holter monitor
 * Main Activity class
 * */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}