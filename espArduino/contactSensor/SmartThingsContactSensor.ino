/*
    This sketch demonstrates how to set up a simple REST-like server
    to monitor a door/window opening and closing.
    A reed switch is connected to gnd and the other end is
    connected to gpio2 with a pullup resistor to vcc, when door 
    is closed, reed switch should be closed and vice versa.
    ip is the IP address of the ESP8266 module if you want to hard code it,
    Otherwise remove ip, gateway and subnet and also WiFi.config to use dhcp.
    
    Status of contact is returned as json: {"name":"contact","status":0}
    To get status: http://{ip}:{serverPort}/getstatus
    
    Any time status changes, the same json above will be posted to {hubIp}:{hubPort}.
*/

#include <ESP8266WiFi.h>

// Your wifi settings go here
const char* ssid = "SSID";
const char* password = "SSIDPASSWORD";

// the following 3 ip addresses are not necessary if you are using dhcp
IPAddress ip(192, 168, 1, 21); // hardcode ip for esp
IPAddress gateway(192, 168, 1, 1); //router gateway
IPAddress subnet(255, 255, 255, 0); //lan subnet
const unsigned int serverPort = 9060; // port to run the http server on

// Smartthings hub information
IPAddress hubIp(192, 168, 1, 43); // smartthings hub ip
const unsigned int hubPort = 39500; // smartthings hub port

byte oldSensorState, currentSensorState;
long debounceDelay = 10;    // the debounce time; increase if false positives

WiFiServer server(serverPort); //server
WiFiClient client; //client
String readString;

void setup() {
  delay(10);

  // prepare GPIO2
  pinMode(2, INPUT);

  // Connect to WiFi network
  WiFi.begin(ssid, password);
  // Comment out this line if you want ip assigned by router
  WiFi.config(ip, gateway, subnet);


  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
  }

  // Start the server
  server.begin();
}

// send json data to client connection
void sendJSONData(WiFiClient client) {
  client.println(F("CONTENT-TYPE: application/json"));
  client.println(F("CONTENT-LENGTH: 29"));
  client.println();
  client.print("{\"name\":\"contact\",\"status\":");
  client.print(currentSensorState);
  client.println("}");
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
        if (readString.substring(readString.indexOf('/'), readString.indexOf('/') + 10) == "/getstatus") {
          client.println("HTTP/1.1 200 OK"); //send new page
          sendJSONData(client);
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
    sendJSONData(client);
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


boolean sensorChanged() {
  boolean retVal = false;
  currentSensorState = digitalRead(2);
  if(currentSensorState != oldSensorState) {
    // make sure we didnâ€™t get a false reading
    delay(debounceDelay);
    currentSensorState = digitalRead(2);
    if(currentSensorState != oldSensorState) {
      retVal = true;
    }
  }
  return retVal;
}

void loop() {
  if (sensorChanged()) {
    if (sendNotify()) {
      // update old sensor state after weâ€™ve sent the notify
      oldSensorState = currentSensorState;
    }
  }

  // Handle any incoming requests
  WiFiClient client = server.available();
  if (client) {
    handleRequest(client);
  }
}
