/*
  Created by Charles Schwer
 
 Code based on https://github.com/DennisSc/easyIoT-ESPduino/blob/master/sketches/ds18b20.ino
 
 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 version 2 as published by the Free Software Foundation.
 */
#include <ESP8266WiFi.h>
#include <OneWire.h>
#include <DallasTemperature.h>

//AP definitions
const char* ssid = "YOUR SSID";
const char* password = "YOUR PASSWORD";

#define ONE_WIRE_BUS 2  // DS18B20 pin
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature DS18B20(&oneWire);

// the following 3 ip addresses are not necessary if you are using dhcp
IPAddress ip(192, 168, 1, 55); // hardcode ip for esp
IPAddress gateway(192, 168, 1, 1); //router gateway
IPAddress subnet(255, 255, 255, 0); //lan subnet
const unsigned int serverPort = 8090; // port to run the http server on

// Smartthings hub information
IPAddress hubIp(192, 168, 1, 43); // smartthings hub ip
const unsigned int hubPort = 39500; // smartthings hub port

// default time to report temp changes
int reportInterval = 600; // in secs

WiFiServer server(serverPort); //server
WiFiClient client; //client
String readString;

int rptCnt;

void setup() {
  Serial.begin(115200);
  delay(10);
  
  // Connect to WiFi network
  WiFi.begin(ssid, password);
  // Comment out this line if you want ip assigned by router
  WiFi.config(ip, gateway, subnet);


  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
  }

  // Start the server
  server.begin();
  rptCnt=reportInterval;
}

float getTemp() {
  float temp;
  do {
    DS18B20.requestTemperatures(); 
    temp = DS18B20.getTempCByIndex(0);
    Serial.print("Temperature: ");
    Serial.println(temp);
  } while (temp == 85.0 || temp == (-127.0));
  return temp;
}

// send json data to client connection
void sendTempJSONData(WiFiClient client) {
  String tempString = String(getTemp());
  client.println(F("CONTENT-TYPE: application/json"));
  client.print(F("CONTENT-LENGTH: "));
  client.println(31+tempString.length());
  client.println();
  client.print(F("{\"name\":\"temperature\",\"value\":"));
  client.print(tempString);
  client.println(F("}"));
  // 31 chars plus temp;
}

// send json data to client connection
void sendRptIntJSONData(WiFiClient client) {
  String rptIntString = String(reportInterval);
  client.println(F("CONTENT-TYPE: application/json"));
  client.print(F("CONTENT-LENGTH: "));
  client.println(34+rptIntString.length());
  client.println();
  client.print(F("{\"name\":\"reportInterval\",\"value\":"));
  client.print(rptIntString);
  client.println(F("}"));
}

// send response to client for a request for status
void handleRequest(WiFiClient client)
{
  boolean currentLineIsBlank = true;
  while (client.connected()) {
    if (client.available()) {
      char c = client.read();
      //read char by char HTTP request
      if (readString.length() < 100) {
        //store characters to string
        readString += c;
      }
      if (c == '\n' && currentLineIsBlank) {
        //now output HTML data header
        if (readString.substring(readString.indexOf('/'), readString.indexOf('/') + 8) == "/getTemp") {
          client.println("HTTP/1.1 200 OK"); //send new page
          sendTempJSONData(client);
        } else if (readString.substring(readString.indexOf('/'), readString.indexOf('/') + 11) == "/setRptInt/") {
           String newVal =readString.substring(readString.indexOf('/') +11);
           reportInterval = newVal.substring(0,newVal.indexOf(' ')).toInt();
           rptCnt = reportInterval;
           client.println("HTTP/1.1 200 OK"); //send new page
           sendRptIntJSONData(client);
        } else if (readString.substring(readString.indexOf('/'), readString.indexOf('/') + 10) == "/getRptInt") {
           client.println("HTTP/1.1 200 OK"); //send new page
           sendRptIntJSONData(client);
        } else {
          client.println(F("HTTP/1.1 204 No Content"));
          client.println();
          client.println();
        }
        break;
      }
      if (c == '\n') {
        // you're starting a new line
        currentLineIsBlank = true;
      } else if (c != '\r') {
        // you've gotten a character on the current line
        currentLineIsBlank = false;
      }
    }
  }
  readString = "";

  delay(1);
  //stopping client
  client.stop();
}

// send data
int sendNotify() //client function to send/receieve POST data.
{
  int returnStatus = 1;
  if (client.connect(hubIp, hubPort)) {
    client.println(F("POST / HTTP/1.1"));
    client.print(F("HOST: "));
    client.print(hubIp);
    client.print(F(":"));
    client.println(hubPort);
    sendTempJSONData(client);
  }
  else {
    //connection failed
    returnStatus = 0;
  }

  // read any data returned from the POST
  while(client.connected() && !client.available()) delay(1); //waits for data
  while (client.connected() || client.available()) { //connected or data available
    char c = client.read();
  }

  delay(1);
  client.stop();
  return returnStatus;
}

void loop() {
  if(rptCnt<=0) {
    sendNotify();
    rptCnt=reportInterval;
  }

  if(rptCnt>0) {
    delay(1000);
    rptCnt--;
  }

  // Handle any incoming requests
  WiFiClient client = server.available();
  if (client) {
    handleRequest(client);
  }
}
