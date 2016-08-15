/**
 *  Copyright 2016 Charles Schwer
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	ESP8266 Based Temperature Sensor
 *
 *	Author: cschwer
 *	Date: 2016-01-23
 */
 preferences {
	input("ip", "text", title: "IP Address", description: "ip", required: true)
	input("port", "text", title: "Port", description: "port", required: true)
	input("mac", "text", title: "MAC Addr", description: "mac")
}

metadata {
	definition (name: "ESP8266 Temperature Sensor", namespace: "cschwer", author: "Charles Schwer") {
		capability "Refresh"
        capability "Temperature Measurement"
		capability "Sensor"
        capability "Polling"
	}

	// UI tile definitions
	tiles {
		valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label:'${currentValue}Â°', unit:"F",
				backgroundColors:[
					[value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]
			)
		}
		standardTile("refresh", "device.backdoor", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        main "temperature"
		details("temperature","refresh")
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
	def msg = parseLanMessage(description)
	def headerString = msg.header

	def result = []
	def bodyString = msg.body
	def value = "";
	if (bodyString) {
		def json = msg.json;
		if( json?.name == "temperature") {
			if(getTemperatureScale() == "F"){
				value = (celsiusToFahrenheit(json.value) as Float).round(0) as Integer
			} else {
				value = json.value
			}
			log.debug "temperature value ${value}"
			result << createEvent(name: "temperature", value: value)
		}
	}
	result
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private getHostAddress() {
	def ip = settings.ip
	def port = settings.port

	log.debug "Using ip: ${ip} and port: ${port} for device: ${device.id}"
	return ip + ":" + port
}

def refresh() {
	log.debug "Executing 'refresh' ${getHostAddress()}"
	if(!settings.mac) {
		// if mac address is blank in settings, then use ip:port, but ST will not get updates it will only get Poll results.
		log.debug "setting device network id to ip:port"
		def hosthex = convertIPtoHex(settings.ip)
		def porthex = convertPortToHex(settings.port)
		device.deviceNetworkId = "$hosthex:$porthex" 
    } else {
		if(device.deviceNetworkId!=settings.mac) {
    		log.debug "setting device network id to mac"
    		device.deviceNetworkId = settings.mac;
    	}
	}
    poll()
}

def poll() {
	log.debug "Executing 'poll' ${getHostAddress()}"
	new physicalgraph.device.HubAction(
    	method: "GET",
    	path: "/getstatus",
    	headers: [
        	HOST: "${getHostAddress()}"
    	]
	)
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
    return hexport
}
