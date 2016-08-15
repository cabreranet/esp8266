/**
 *  Copyright 2015 Charles Schwer
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
 *	ESP8266 Based Contact Sensor
 *
 *	Author: cschwer
 *	Date: 2016-01-23
 */
 preferences {
	input("ip", "text", title: "IP Address", description: "ip")
	input("port", "text", title: "Port", description: "port")
	input("mac", "text", title: "MAC Addr", description: "mac")
}

 metadata {
	definition (name: "ESP8266 Contact Sensor", namespace: "cschwer", author: "Charles Schwer") {
		capability "Refresh"
		capability "Sensor"
        	capability "Contact Sensor"
	}

	// simulator metadata
	simulator {}

	// UI tile definitions
	tiles {
        standardTile("contact", "device.contact", width: 2, height: 2) {
			state("open", label:'${name}', icon:"st.contact.contact.open", backgroundColor:"#ffa81e")
			state("closed", label:'${name}', icon:"st.contact.contact.closed", backgroundColor:"#79b821")
		}
		standardTile("refresh", "device.backdoor", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		main "contact"
		details (["contact", "refresh"])
	}
}

// parse events into attributes
def parse(String description) {
	def msg = parseLanMessage(description)
	def headerString = msg.header

	def result = []
	def bodyString = msg.body
    def value = "";
	if (bodyString) {
        def json = msg.json;
        if( json?.name == "contact") {
        	value = json.status == 1 ? "open" : "closed"
            log.debug "contact status ${value}"
			result << createEvent(name: "contact", value: value)
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
	if(device.deviceNetworkId!=settings.mac) {
    	log.debug "setting device network id"
    	device.deviceNetworkId = settings.mac;
    }

	log.debug "Executing 'refresh' ${getHostAddress()}"
	new physicalgraph.device.HubAction(
    	method: "GET",
    	path: "/getstatus",
    	headers: [
        	HOST: "${getHostAddress()}"
    	]
	)
}
