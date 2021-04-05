package com.example.bluetootharduino;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Message;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Arrays;

import android.os.Handler;

import androidx.annotation.RequiresApi;

import static java.lang.Math.abs;

/*
 * Konstantinos Knais 8967
 * Android application used in my thesis - 7 lead holter monitor
 * To be used along with the designed holter monitor
 * Manages incoming and outcomming data to arduino
 * */
public class ConnectedThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    public static final int RESPONSE_MESSAGE = 10;
    public static final int MESSAGE_MESSAGE = 12;
    public static final int max_measurments = 80;

    Handler uih;

    public Handler mHandler;

    public ConnectedThread(BluetoothSocket socket,Handler uih){
        mmSocket=socket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;
        this.uih=uih;
        Log.i("[THREAD-CT]","Creating thread");
        try {
            tmpIn=socket.getInputStream();
            tmpOut=socket.getOutputStream();
        } catch (IOException e) {
            Log.e("[THREAD-CT]","Error:"+ e.getMessage());
        }
        mmInStream=tmpIn;
        mmOutStream=tmpOut;
        try{
            mmOutStream.flush();
        } catch (IOException e) {
            return;
        }
        Log.i("[THREAD-CT]","IO's obtained");
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void run(){
        int num_of_packets = 9;
        byte[] bytes = new byte[num_of_packets];
        int iterator = 0;
        boolean next2Flag=false;
        short[] measurements = new short[max_measurments*4];
        int measurements_num=0;
        BufferedReader br = new BufferedReader(new InputStreamReader(mmInStream));
        BufferedInputStream bis = new BufferedInputStream(mmInStream);

        Log.i("[THREAD-CT]","Starting thread");
        while (true){
            try{
                int bytesAvailable = mmInStream.available();
                if (bytesAvailable > 0) {
                    byte[] curBuf = new byte[bytesAvailable];
                    mmInStream.read(curBuf);
                    for (byte b : curBuf) {
                        if (b == 2 && iterator == num_of_packets) { //start of message, translate byte array collected and send
                            short[] shorts = new short[bytes.length/2];
                            // to turn bytes to shorts as either big endian or little endian.
                            byte[] modifiedBytes = Arrays.copyOfRange(bytes, 1, bytes.length); //Discard first byte
                            ByteBuffer.wrap(modifiedBytes).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(shorts);//Make numbers from the other bytes
                            if(shorts[0]>900){ //
                                shorts[0]=0;
                                shorts[1]=0;
                                shorts[2]=0;
                            }
                            //After 100 packets send to ardconn to view
                            if(measurements_num==max_measurments*4){
                                measurements_num=0;
                                //Send measurements to Ardconn
                                Message msg = new Message();
                                msg.what = RESPONSE_MESSAGE;
                                msg.obj =measurements;
                                uih.sendMessage(msg);
                            }
                            //Add shorts to a bigger array
                            System.arraycopy(shorts, 0, measurements, measurements_num, shorts.length);
                            //Added measurements, increase point on array
                            measurements_num=measurements_num+4;
/*
                            for(short s:shorts){
                                Log.d("[THREAD-CT]","shorts: "+s);
                            }
                            Log.d("[THREAD-CT]"," ");
*/
                            iterator = 0;
                            next2Flag=false;
                            bytes[iterator] = b;
                            //Log.d("[THREAD-CT]","bytes["+iterator+"]: "+bytes[iterator]);
                        } else if(iterator<num_of_packets) { //Save next byte
                            bytes[iterator] = b;
                            //Log.d("[THREAD-CT]","bytes["+iterator+"]: "+bytes[iterator]);
                        }else{ //if im here I lost the 2 for the start and i have to wait for the next
                            Log.d("[THREAD-CT]","next2Flag enabled");
                            //Should have a flag to wait for the next 2
                            next2Flag=true;
                        }
                        if(b==3 && iterator==num_of_packets){
                            //Code to send "Saved, you can eject the SD card"
                            Message msg = new Message();
                            msg.what = MESSAGE_MESSAGE;
                            msg.obj ="Saved, you can eject the SD card";
                            uih.sendMessage(msg);
                        }else if(b==4 && iterator==num_of_packets){
                            //Code to send "Insert SD Card"
                            Message msg = new Message();
                            msg.what = MESSAGE_MESSAGE;
                            msg.obj ="Insert SD card";
                            uih.sendMessage(msg);
                        }else if (b==5 && iterator==num_of_packets){
                            //Code to send  Battery low"
                            Message msg = new Message();
                            msg.what = MESSAGE_MESSAGE;
                            msg.obj ="Battery low";
                            uih.sendMessage(msg);
                        }
                        if (next2Flag){
                            //wait untill next 2
                            Log.d("[THREAD-CT]","next2Flag enabled");
                            iterator=num_of_packets;
                        }else {
                            iterator++;
                        }

                    }
                }
            } catch (IOException e) {
                break;
            }
        }
        Log.i("[THREAD-CT]","While loop ended");
    }

    public void write(byte[] bytes){
        try{
            Log.i("[THREAD-CT]", "Writting bytes");
            mmOutStream.write(bytes);
        }catch(IOException e){}
    }

    public void cancel(){
        try{
            mmSocket.close();
        }catch(IOException e){}
    }


}
