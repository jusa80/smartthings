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
 *  0.10    Juha Tanskanen      Support for RGB lights and multiple lights
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
        input "whiteBulbs", "capability.colorTemperature", title: "White Spectrum Light Bulb", multiple: true, required: false
        input "rgbBulbs", "capability.colorControl", title: "RGBW Light Bulb", multiple: true, required: false
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
    state.power = "off"
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
                log.debug "Bulb status $state.power"
                if (state.power == "on") {
                    whiteBulbs*.off()
                    rgbBulbs*.off()
                    state.power = "off"
                } else {
                    whiteBulbs*.on()
                    rgbBulbs*.on()
                    state.power = "on"
                }
                break
            case "pushed_2x":
                log.debug "Button clicked twice - Change Color"
                changeColorTemperature()
                changeColor()
                break
            case "pushed_3x":
                if (state.fullBrightness) {
                    log.debug "Button clicked treble - Return Dimmer Brigthness"
                    whiteBulbs*.setLevel(levelDevice.currentValue("level"))
                    rgbBulbs*.setLevel(levelDevice.currentValue("level"))
                    state.fullBrightness = 0
                } else {
                    log.debug "Button clicked treble - Full Brigthness"
                    whiteBulbs*.setLevel(100)
                    rgbBulbs*.setLevel(100)
                    state.fullBrightness = 1
                }
                break
        }
    } else {
        whiteBulbs.each {
            Integer currentLevel = it.currentValue("level")
            log.debug "Set level of White Spectrum bulb $currentLevel -> $value"
        }
        rgbBulbs.each {
            Integer currentLevel = it.currentValue("level")
            log.debug "Set level of RGB bulb $currentLevel -> $value"
        }
        whiteBulbs*.setLevel(value as Integer)
        rgbBulbs*.setLevel(value as Integer)
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
    whiteBulbs*.setColorTemperature(temp.get('temp'))
}

private changeColor() {

    def colors = [
        0: [name:'Soft White', hue: 23, saturation: 56],
        1: [name:'White', hue: 52, saturation: 19],
        2: [name:'Daylight', hue: 53, saturation: 91],
        3: [name:'Warm White', hue: 20, saturation: 83],
        4: [name:'Blue', hue: 70, saturation: 100],
        5: [name:'Green', hue: 39, saturation: 100],
        6: [name:'Yellow', hue: 25, saturation: 100],
        7: [name:'Orange', hue: 10, saturation: 100],
        8: [name:'Purple', hue: 75, saturation: 100],
        9: [name:'Pink', hue: 83, saturation: 100],
       10: [name:'Red', hue: 100, saturation: 100]
    ]

    def color = colors.get(state.color)
    def newValue = [hue: color.get('hue'), saturation: color.get('saturation'), level: levelDevice.currentValue("level")]
    state.color = (state.color + 1)%colors.size()
    rgbBulbs*.setColor(newValue)
}
