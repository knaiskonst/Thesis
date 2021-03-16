 
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

//Struct to put 
//customize this to whatever variable types and names you need
typedef struct measure_struct{
  byte start;
  byte MSB1;
  byte LSB1;
  byte MSB2;
  byte LSB2;
  byte MSB3;
  byte LSB3;
};


//the packet size will be the number of bytes (1byte = 8bits) of all the variables we defined in the structure above
//in our case it's: 4 bytes (the first 4 variables in the struct) + 2 bytes (the uint16_t is 2*8 bytes) + 4bytes (the float) + 1 byte (the last variable)
const int union_size = sizeof(measure_struct);

/* Now we define a union, basically the ways we want to write or read this data
 * in our case we want one way to be the structure above
 * and another way to be a byte array of appropriate size.
 * I named this 'btPacket_t' because I use it for bluetooth, name it whatever you want.
 * You can define as many types inside as you need, just make sure the types you define are all the same size in bytes
 */
typedef union btPacket_t{
 measure_struct structure;
 byte byteArray[union_size]; /* you can use other variable types if you want. Like: a 32bit integer if you have 4 8bit variables in your struct */
};

//create a variable using this new union we defined
btPacket_t measurements;  

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
  start=micros();
  sendTime=millis();
  input1 = highPassFilter.input(analogRead(analogPin1)); // read the input pin1  lowPassFilter.input(highPassFilter.input(
  input2 = highPassFilter.input(analogRead(analogPin2)); // read the input pin2
  input3 = highPassFilter.input(analogRead(analogPin3)); // read the input pin3
  //1120us -> 1.12ms
  if(maxValue<abs(input1)){
      maxValue=abs(input1);
      Serial.println(maxValue);
  }
  
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
  startFlag=1;
  if(startFlag){// If start flag = 1 sd card is inserted and its safe to start writing
    
    sendBlue(2,input1,input2,input3);//7450us ->7.45ms

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
    

    //Bluetooth Start
    if((sendTime - currentMillis)>60000){//1 minuite has passed
      currentMillis=millis();
      batteryLvl = analogRead(batteryPin);
      //Serial.println(batteryLvl);

      sendBlue(3,batteryLvl,num,0);


    }
  }
  //Tottal time: 3500us->3.5ms -> 285.71Hz
  res=micros()-start;
}

void sendBlue(int what, int x, int y, int z){
    measurements.byteArray[0] = what;    //3-Battery lvl 2-Measurements
    measurements.byteArray[1] =  x >> 8;//MSB x
    measurements.byteArray[2] =  x;     //LSB x
    measurements.byteArray[3] =  y >> 8;//MSB y
    measurements.byteArray[4] =  y;     //LSB y
    measurements.byteArray[5] =  z >> 8;//MSB z
    measurements.byteArray[6] =  z;     //LSB z
    mySerial.write(measurements.byteArray, union_size);
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
    batteryLvl = analogRead(batteryPin);
    sendBlue(3,batteryLvl,num,0);
    // if the file opened okay, write to it:
    if (myFile) {
      myFile.println("ECG1, ECG2, ECG3, Time");
    }
    return true;
  }else{
    return false;
   }  
}
