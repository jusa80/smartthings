/**
 *  Copyright 2019 Juha Tanskanen
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
 *  Sonos Speaker Control
 *
 *  Version Author              Note
 *  0.9     Juha Tanskanen      Initial release
 *
 */

definition(
    name: "Light Control",
    namespace: "smartthings",
    author: "Juha Tanskanen",
    description: "Control your lights with Ikea SYMFONISK Sound Controller",
    category: "SmartThings Internal",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png"
)

preferences {
    section("Select your devices") {
        input "buttonDevice", "capability.button", title: "Light ON/OFF Control", multiple: false, required: true
        input "levelDevice", "capability.switchLevel", title: "Light Level Control", multiple: false, required: true
        input "lightBulb", "capability.switch", title: "Light Bulb", multiple: false, required: true
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(buttonDevice, "button", buttonEvent)
    subscribe(levelDevice, "level", buttonEvent)
    state.color = 1
    state.temp = 1
    state.fullBrightness = 0
}

def buttonEvent(evt){
    def device = evt.name
    def value = evt.value
    log.debug "buttonEvent: $evt.name = $evt.value ($evt.data)"
    log.debug "command: $device, value: $value"

    def recentEvents = buttonDevice.eventsSince(new Date(now() - 2000)).findAll{it.value == evt.value && it.data == evt.data}
    log.debug "Found ${recentEvents.size()?:0} events in past 2 seconds"

    if(recentEvents.size <= 1){
        handleCommand(device, value)
    } else {
        log.debug "Found recent button press events for $device with value $value"
    }
}

def handleCommand(command, value) {
    if (command == "button") {
        log.debug "Handle $value"
        switch (value) {
            case "pushed":
                log.debug "Button clicked - ON/OFF"
                def currentStatus = lightBulb.currentValue("switch")
                log.debug "Bulb status $currentStatus"
                if (currentStatus == "on") {
                    lightBulb.off()
                } else {
                    lightBulb.on()
                }
                break
            case "pushed_2x":
                log.debug "Button clicked twice - Change Color"
                changeColorTemperature()
                break
            case "pushed_3x":
                if (state.fullBrightness) {
                    log.debug "Button clicked treble - Return Dimmer Brigthness"
                    lightBulb.setLevel(levelDevice.currentValue("level"))
                    state.fullBrightness = 0
                } else {
                    log.debug "Button clicked treble - Full Brigthness"
                    lightBulb.setLevel(100)
                    state.fullBrightness = 1
                }
                break
        }
    } else {
        Integer currentLevel = lightBulb.currentValue("level")
        log.debug "Set level $currentLevel -> $value"
        lightBulb.setLevel(value as Integer)
        state.fullBrightness = 0
    }
}



private changeColorTemperature() {

    def temps = [
        0: [name: 'Candle Light', temp: 2700],
        1: [name: 'Warm White', temp: 3000],
        2: [name: 'Cool White', temp: 4000]
    ]

    def temp = temps.get(state.temp)
    state.temp = (state.temp + 1)%temps.size()
    lightBulb.setColorTemperature(temp.get('temp'))
}

private changeColor() {

    def colors = [
        0: [name:'Soft White', hue: 23, saturation: 56],
        1: [name:'White', hue: 52, saturation: 19],
        2: [name:'Daylight', hue: 53, saturation: 91],
        3: [name:'Warm White', hue: 20, saturation: 80]
    ]

    def color = colors.get(state.color)
    def newValue = [hue: color.get('hue'), saturation: color.get('saturation'), level: levelDevice.currentValue("level")]
    state.color = (state.color + 1)%colors.size()
    lightBulb.setColor(newValue)
}
