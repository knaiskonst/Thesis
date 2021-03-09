package com.example.bluetootharduino;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.UUID;

import static android.os.SystemClock.elapsedRealtime;

//Initialize Bluetooth and make a connection
public class Ardconn extends AppCompatActivity {
    public final static String MODULE_MAC = "98:D3:31:FD:78:D9";//Change mac address if different arduino
    public final static int REQUEST_ENABLE_BT=1;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private GestureDetectorCompat mDetector;

    BluetoothAdapter bta;
    BluetoothDevice mmDevice;
    ConnectedThread btt=null;

    TextView response;
    ProgressBar batteryLvl;
    public Handler mHandler;

    public LineChart chart;
    ArrayList<Entry> valuesECG1 = new ArrayList<>();
    long time_ecg;
    long start;
    ArrayList<Entry> valuesECG2 = new ArrayList<>();
    ArrayList<Entry> valuesECG3 = new ArrayList<>();
    int showChart=1;
    boolean scrolling = true;
    long sum;
    int num;
    final int ARRAY_LIMIT = 100000; //Change if i want more

    BluetoothSocket mmSocket = null;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        batteryLvl= findViewById(R.id.progressBar); // initiate the progress bar
        batteryLvl.setMax(100);

        Log.i("[BLUETOOTH]", "Creating listeners");
//        response = (TextView)findViewById(R.id.textView);
//        response.setMovementMethod(new ScrollingMovementMethod());
        bta = BluetoothAdapter.getDefaultAdapter();
        //If bluetooth is not enabled create intent for user to turn it on
        if(!bta.isEnabled()){
            setRequestEnableBt();
        }else{
            initiateBluetoothProcess();
        }

        chart=findViewById(R.id.chart);
        setupChart(chart, Color.rgb(255,255,255));
        start = System.currentTimeMillis();

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.my_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.connectOption:
                if(mmSocket!=null){
                    try {
                        mmSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                initiateBluetoothProcess();
                return true;
            case R.id.startOption:
                //Send the number 2 to arduino
                Log.d("[Ardconn]","Sending 2");
                btt.write("2".getBytes());
                return true;
            case R.id.stopOption:
                //Send the number 1 to arduino
                Log.d("[Ardconn]","Sending 1");
                btt.write("1".getBytes());
                return true;
            case R.id.scrollOption:
                scrolling = !scrolling;
                return true;
            case R.id.chanel1:
                //Show ecg1
                showChart=1;
                return true;
            case R.id.chanel2:
                //Show ecg2
                showChart=2;
                return true;
            case R.id.chanel3:
                //Show ecg3
                showChart=3;
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setRequestEnableBt(){
        Intent enableBTIntent= new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBTIntent,REQUEST_ENABLE_BT);
    }
    //Catch result of on off dialog
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(resultCode==RESULT_OK && requestCode==REQUEST_ENABLE_BT){
            initiateBluetoothProcess();
        }else{
            toastMessage("Please Enable Bluetooth");
        }
    }
    private void initiateBluetoothProcess() {
        Log.d("[Ardconn]","initiateBluetoothProcess");
        if(bta.isEnabled()){
            //Attempt to connect to bluetooth module
            BluetoothSocket tmp;
            mmDevice = bta.getRemoteDevice(MODULE_MAC);
            toastMessage("Searching...");
            //Create socket
            try {
                tmp=mmDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                mmSocket=tmp;
                mmSocket.connect();
                Log.i("[BLUETOOTH]","Connected to: "+mmDevice.getName());
                toastMessage("Connected to: "+mmDevice.getName());
            } catch (IOException e) {
                try {
                    mmSocket.close();
                }catch (IOException i){
                    i.printStackTrace();
                }
            }
            Log.i("[BLUETOOTH]", "Creating handler");
            mHandler= new Handler(Looper.getMainLooper()){
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void handleMessage(Message msg){
                    //super.handleMessage(msg);
                    if(msg.what == ConnectedThread.RESPONSE_MESSAGE){
                        //Get message from connected thread
                        String txt = (String)msg.obj;
                        //Log.d("[Ardconn]","txt: "+txt+" length: "+txt.length());

                        if(txt.startsWith("Voltage")){
                            //Should show somehow the voltage
                            int voltagePer =(int) (((getVoltage(txt)-3)/2)*100);
                            Log.d("[Ardconn]", "voltagePer: "+voltagePer);
                            if(voltagePer>0){
                                batteryLvl.setProgress(voltagePer); // 50 default progress value for the progress bar
                            }
                        }else if(txt.startsWith("ECG")) {
                            Log.d("[Ardconn]", "txt: " + txt + " length: " + txt.length());
                            getSupportActionBar().setTitle(txt);
                        }else if(txt.matches("[a-zA-Z, /,.?]+")){
                                toastMessage(txt);
                        }else if( txt.matches("[0-9, /,.-]+") && txt.length()<20 && txt.length()>15) {
                            float[] valuesFloat = seperateData(txt);
                            if (valuesFloat.length == 3) {
                                previewData(valuesFloat);
                            }
                        }
                    }
                }
            };
            Log.i("[BLUETOOTH]", "Creating and running Thread");
            btt = new ConnectedThread(mmSocket,mHandler);
            btt.start();
        }else{
            setRequestEnableBt();
        }
    }

    private float[] seperateData(String str){
        String[] valuesString = str.split("\\s*,\\s*"); //Make an array from each ,
        float[] valuesFloat = new float[valuesString.length];
        for(int i = 0;i < valuesString.length;i++)
        {
                valuesFloat[i] = Float.parseFloat(valuesString[i].trim());
        }
        return  valuesFloat;
    }

    private float getVoltage(String str){
        String[] valuesString = str.split("\\s*: \\s*"); //Make an array from each ,
        float valuesFloat;
        valuesFloat = Float.parseFloat(valuesString[1].trim());
        Log.d("[Ardconn]","valuesFloat: "+valuesFloat);
        return valuesFloat;
    }

    private void setupChart(LineChart chart,int color){
        Log.d("[LedControl]","setupChart called");
        // no description text
        chart.getDescription().setEnabled(true);
        chart.getDescription().setTextColor(Color.BLACK);

        // chart.setDrawHorizontalGrid(false);
        //
        // enable / disable grid background
        chart.setDrawGridBackground(true);

        // enable touch gestures
        chart.setTouchEnabled(true);

        // enable scaling and dragging
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);

        // if disabled, scaling can be done on x- and y-axis separately
        chart.setPinchZoom(false);
        chart.setScaleYEnabled(false);
        chart.setBackgroundColor(color);

        // set custom chart offsets (automatic offset calculation is hereby disabled)
        //chart.setViewPortOffsets(10, 0, 10, 0);
        chart.getXAxis().setDrawLimitLinesBehindData(true);
        chart.getAxisLeft().setEnabled(true);
        chart.getAxisLeft().setSpaceTop(40);
        //chart.getAxisLeft().setLabelCount(50,true);
        chart.getAxisLeft().setSpaceBottom(40);
        chart.getAxisRight().setEnabled(false);
        //chart.getXAxis().setEnabled(true);get
        chart.getXAxis().setLabelCount(20,false);

        chart.getAxisLeft().setDrawGridLines(true);
        chart.getXAxis().setDrawGridLines(true);
        chart.getXAxis().setLabelRotationAngle(-30);

        // animate calls invalidate()...
        //chart.animateX(2500);
        chart.invalidate();
    }
    private void toastMessage(String message){
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void previewData(float[] data){
        //Log.d("[LedControl]","previewCharts called");
        //Log.d("[previewData]","Previewing data[0]: "+data[0]);
        if(valuesECG1.size()==ARRAY_LIMIT){
            Log.d("[Ardconn]","Clearing arrays");
            valuesECG1.clear();
            valuesECG2.clear();
            valuesECG3.clear();
        }
        long time_prev = time_ecg;
        time_ecg= (System.currentTimeMillis() - start);
        //Log.d("Ardconn","time_ecg " + time_ecg);
        sum = sum+(time_ecg-time_prev);
        num++;
        if(num==1000){
            Log.d("[Ardconn]","AverageTime: "+ sum/num);
            num=0;
            sum=0;
        }
        valuesECG1.add(new Entry(time_ecg,data[0]));
        valuesECG2.add(new Entry(time_ecg,data[1]));
        valuesECG3.add(new Entry(time_ecg,data[2]));


        LineDataSet set1 = new LineDataSet(valuesECG1,"ECG1");
        LineDataSet set2 = new LineDataSet(valuesECG2,"ECG2");
        LineDataSet set3 = new LineDataSet(valuesECG3,"ECG3");
        LineData lineData1 = new LineData(set1);
        LineData lineData2 = new LineData(set2);
        LineData lineData3 = new LineData(set3);

        switch(showChart){
            case 1:
                chart.getDescription().setText("ECG1");
                updateChart(chart,lineData1);
                break;
            case 2:
                chart.getDescription().setText("ECG2");
                updateChart(chart,lineData2);
                break;
            case 3:
                chart.getDescription().setText("ECG3");
                updateChart(chart,lineData3);
                break;
        }
    }

    private void updateChart(LineChart chart, LineData data) {
        //Log .d("[LedControl]","updateChart called");
        ((LineDataSet) data.getDataSetByIndex(0)).setLineWidth(1.75f);
        ((LineDataSet) data.getDataSetByIndex(0)).setColor(Color.BLACK);
        ((LineDataSet) data.getDataSetByIndex(0)).setHighLightColor(Color.BLACK);
        ((LineDataSet) data.getDataSetByIndex(0)).setDrawValues(true);
        ((LineDataSet) data.getDataSetByIndex(0)).setDrawCircles(false);

        // add data
        chart.setVisibleXRangeMaximum(4000);
        //chart.setVisibleYRangeMaximum(2,);
        if(scrolling){
            chart.moveViewToX(data.getXMax());
        }

        // get the legend (only possible after setting data)
        Legend l = chart.getLegend();
        l.setEnabled(false);
        //Refresh

        chart.notifyDataSetChanged();
        chart.setData(data);
        chart.invalidate();
    }
}
