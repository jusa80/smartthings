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
    name: "Sonos Remote Control",
    namespace: "smartthings",
    author: "Juha Tanskanen",
    description: "Control your Sonos system with Ikea SYMFONISK Sound Controller",
    category: "SmartThings Internal",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png"
)

preferences {
    section("Select your devices") {
        input "buttonDevice", "capability.button", title: "Sonos Control", multiple: false, required: true
        input "levelDevice", "capability.switchLevel", title: "Sonos Volume Control", multiple: false, required: true
        input "sonos", "capability.audioVolume", title: "Sonos", multiple: false, required: true
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
            case "clicked":
                log.debug "Button clicked - Play/Pause"
                def currentStatus = sonos.currentValue("playbackStatus")
                log.debug "Sonos status $currentStatus"
                if (currentStatus == "playing") {
                    sonos.pause()
                } else {
                    sonos.play()
                }
                break
            case "clicked twice":
                log.debug "Button clicked twice - Next Track"
                sonos.nextTrack()
                break
            case "clicked treble":
                log.debug "Button clicked treble - Previous Track"
                sonos.previousTrack()
                break
        }
    } else {
        def currentVolume = sonos.currentValue("volume")
        def newVolume = ((BigInteger)currentVolume).intValue() + value.toInteger()
        log.debug "Set volume $currentVolume -> $newVolume"
        sonos.setVolume(newVolume)
    }
}
