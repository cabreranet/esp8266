/**
 *	Copyright 2015 Charles Schwer
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 *	ESP8266 Based Contact Sensor
 *
 *	Author: cschwer
 *	Date: 2016-01-23
 */
 preferences {
	input("ip", "text", title: "IP Address", description: "ip", required: true)
	input("port", "number", title: "Port", description: "port", default: 9060, required: true)
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
			state("default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh")
		}
		main "contact"
		details (["contact", "refresh"])
	}
}

def updated() {
	log.debug("Updated with settings: $settings")
	state.dni = ""
	updateDNI()
	updateSettings()
}

// parse events into attributes
def parse(String description) {
	def msg = parseLanMessage(description)
	if (!state.mac || state.mac != msg.mac) {
		log.debug "Setting deviceNetworkId to MAC address ${msg.mac}"
		state.mac = msg.mac
	}
	def result = []
	def bodyString = msg.body
	def value = ""
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

private getHostAddress() {
	def ip = settings.ip
	def port = settings.port

	//log.debug "Using ip: ${ip} and port: ${port} for device: ${device.id}"
	return ip + ":" + port
}

private def updateDNI() {
	if (!state.dni || state.dni != device.deviceNetworkId || (state.mac && state.mac != device.deviceNetworkId)) {
		device.setDeviceNetworkId(createNetworkId(settings.ip, settings.port))
		state.dni = device.deviceNetworkId
	}
}

private String createNetworkId(ipaddr, port) {
	if (state.mac) {
		return state.mac
	}
	def hexIp = ipaddr.tokenize('.').collect {
		String.format('%02X', it.toInteger())
	}.join()
	def hexPort = String.format('%04X', port.toInteger())
	return "${hexIp}:${hexPort}"
}

def updateSettings() {
	def headers = [:] 
	headers.put("HOST", getHostAddress())
	headers.put("Content-Type", "application/json")
	groovy.json.JsonBuilder json = new groovy.json.JsonBuilder ()
	def map = json {
		hubIp device.hub.getDataValue("localIP")
		hubPort device.hub.getDataValue("localSrvPortTCP").toInteger()
		deviceName device.name
	}	 
	return new physicalgraph.device.HubAction(
		method: "POST",
		path: "/updateSettings",
		body: json.toString(),
		headers: headers
	)
}

def refresh() {
	log.debug "Executing 'refresh' ${getHostAddress()}"
	updateDNI()
	return new physicalgraph.device.HubAction(
		method: "GET",
		path: "/getstatus",
		headers: [
				HOST: "${getHostAddress()}"
		]
	)
}
