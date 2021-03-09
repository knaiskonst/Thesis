 
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

float input1=0;
float input2=0;
float input3=0;
long sendTime=0;
float batteryLvl=0;
const int analogPin1 = A1; 
const int analogPin2 = A2;
const int analogPin3 = A3;
const int batteryPin = A5;  
int i=0;


float LowFrequency = 0.5;
FilterOnePole highPassFilter(HIGHPASS,LowFrequency);

File myFile;//SD card file
String fileName;
int num=0;
unsigned long currentMillis=0;
String values;
void setup() {
  // Open serial communications and wait for port to open:
  Serial.begin(9600);

  // set the data rate for the SoftwareSerial port
  mySerial.begin(9600);

  if(setupSD()){
  }else{
  }
  analogReference(DEFAULT);
  
}

void loop() { // run over and over
  //Take measurements
  sendTime=millis();
  input1 = highPassFilter.input(analogRead(analogPin1))*5/1023; // read the input pin1  lowPassFilter.input(highPassFilter.input(
  input2 = highPassFilter.input(analogRead(analogPin2))*5/1023; // read the input pin2
  input3 = highPassFilter.input(analogRead(analogPin3))*5/1023; // read the input pin3
  values = String(input1);
  values+= ", ";
  values+= String(input2);
  values+= ", ";
  values+= String(input3);
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
    //SD write start
    mySerial.println(values);
    values+=", ";
    values+=sendTime;
    myFile.println(values);
  
    //Bluetooth Start
    if((sendTime - currentMillis)>60000){//1 minuite has passed
      currentMillis=millis();
      batteryLvl = analogRead(batteryPin)*5/1023.0;
      //Serial.println(batteryLvl);
      mySerial.print("Voltage: ");
      mySerial.println(batteryLvl);
      mySerial.println(fileName);
    }
  }
}


bool setupSD(){
//Setup SD start
  if(SD.begin()) {  
    fileName = "ECG_" ;  
    fileName += num; 
    fileName += ".txt";
    num++;
    myFile = SD.open(fileName, FILE_WRITE);
    mySerial.println(fileName);
    batteryLvl = analogRead(batteryPin)*5/1023.0;
    mySerial.print("Voltage: ");
    mySerial.println(batteryLvl);
    // if the file opened okay, write to it:
    if (myFile) {
      myFile.println("ECG1, ECG2, ECG3, Time");
    }
    return true;
  }else{
    return false;
   }  
}
