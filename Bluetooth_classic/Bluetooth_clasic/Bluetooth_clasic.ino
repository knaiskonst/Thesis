 
/*
  Konstantinos Knais - 8967
  This code is part of my univercity thesis.
  To be used along with an ecg amplifier. 
  Reads inputs at pins, passes them through low pass filter, writes them at sd card and sends them with bluetooth.
  This code is in the public domain.

 */
//Sd libraries-----------------
#include <SPI.h>
#include <SD.h>
//------------------------------
//Filter libraries--------------
#include <Filters.h>
//------------------------------
#include <SoftwareSerial.h>
//------------------------------

SoftwareSerial mySerial(8, 9); // RX, TX

int flag=0;
int startFlag=0;

int input1=0;
int input2=0;
int input3=0;

long sendTime=0;
int batteryLvl=0;
const int analogPin1 = A1; 
const int analogPin2 = A2;
const int analogPin3 = A3;
const int batteryPin = A5;  

byte measurementsArray[9];

float LowFrequency = 0.5;
FilterOnePole highPassFilter(HIGHPASS,LowFrequency);

File myFile;//SD card file
String fileName;
int num=0;
int i=0;
int j=0;
unsigned long currentMillis=0;
String values;

long start;
long res;
long dif=0;
long tic=0;
int maxValue=0;

void setup() {
  // Open serial communications and wait for port to open:
  Serial.begin(38400);

  // set the data rate for the SoftwareSerial port
  mySerial.begin(38400);

  if(setupSD()){
  }else{
  }
  analogReference(DEFAULT);
  
}

void loop() { // run over and over
  //Take measurements
  //mesurements = micros(); 
  sendTime=millis();
  input1 = highPassFilter.input(analogRead(analogPin1)); // read the input pin1 
  input2 = highPassFilter.input(analogRead(analogPin2)); // read the input pin2
  input3 = highPassFilter.input(analogRead(analogPin3)); // read the input pin3
  //1120us -> 1.12ms
  
  if(mySerial){//Receive commands from phone
      flag=mySerial.read();    
      if(flag==49){//Ascii code 49 = number 1
        //Stop recording
        startFlag=0;
        mySerial.println("Please wait, saving");
        
        myFile.flush(); //5ms
        myFile.close();
        
        mySerial.println("Saved, you can eject the SD card");
      }
      if(flag == 50){
        //Start recording
        startFlag=1;
        //Make sure sd card is inserted
        while(!setupSD()){
          mySerial.println("Insert SD Card");
          delay(2000);
        }
        //SetupSD initializes SD card if true
        mySerial.println("Starting...");
       }
  }
  
  if(startFlag){// If start flag = 1 sd card is inserted and its safe to start writing

    dif = micros()-tic;
    tic = micros();
    Serial.println(dif);
      sendBlue(2,input1,input2,input3,dif);//7450us ->7.45ms
    
    //start=micros();

    values = String(input1);
    values+= ", ";
    values+= String(input2);
    values+= ", ";
    values+= String(input3);
    values+=", ";
    values+=sendTime;
    myFile.println(values); //450us normal -> 3900us if flush
    
    i++;
    if(i==1000){
      i=0;
      myFile.flush(); //5ms
    }
  }
  //Tottal time: 3500us->3.5ms -> 285.71Hz
  //Serial.println(res);
}

void sendBlue(int what, int x, int y, int z, long t){
    measurementsArray[0] = what;    //3-Battery lvl 2-Measurements
    measurementsArray[1] =  x >> 8;//MSB x
    measurementsArray[2] =  x;     //LSB x
    measurementsArray[3] =  y >> 8;//MSB y
    measurementsArray[4] =  y;     //LSB y
    measurementsArray[5] =  z >> 8;//MSB z
    measurementsArray[6] =  z;     //LSB z
    measurementsArray[7] =  t >> 8;//MSB z
    measurementsArray[8] =  t;     //LSB z
    mySerial.write(measurementsArray, sizeof(measurementsArray));
}


bool setupSD(){
//Setup SD start
  if(SD.begin()) {  
    fileName = "ECG_" ;  
    fileName += num; 
    fileName += ".txt";
    num++;
    myFile = SD.open(fileName, FILE_WRITE);
    // if the file opened okay, write to it:
    if (myFile) {
      myFile.println("ECG1, ECG2, ECG3, Time");
    }
    return true;
  }else{
    return false;
   }  
}
