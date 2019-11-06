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
 *  0.10    Juha Tanskanen      Standard button enums taken in use, switch level is now the real level
 *
 */

import groovy.json.JsonOutput
import physicalgraph.zigbee.zcl.DataType

metadata {
    definition (name: "SYMFONISK Sound Controller", namespace: "smartthings", author: "Juha Tanskanen", ocfDeviceType: "x.com.st.d.remotecontroller", mcdSync: true) {
        capability "Actuator"
        capability "Battery"
        capability "Button"
        capability "Configuration"
        capability "Holdable Button"
        capability "Sensor"
        capability "Switch"
        capability "Switch Level"
        capability "Health Check"

        fingerprint inClusters: "0000, 0001, 0003, 0020, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000", manufacturer: "IKEA of Sweden", model: "SYMFONISK Sound Controller"
    }

    simulator {
        // TODO: define status and reply messages here
    }

    // UI tile definitions
    tiles {
        standardTile("button", "device.button", width: 2, height: 2) {
            state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
            state "pushed", label: "clicked", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#00A0DC"
            state "pushed_2x", label: "clicked twice", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#00A0DC"
            state "pushed_3x", label: "clicked treble", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#00A0DC"
        }

        valueTile("level", "device.level", width: 2, height: 2) {
            state "level", label: '${currentValue}%'
        }

        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false) {
            state "battery", label:'${currentValue}% battery', unit:""
        }
    }

    main "level"
    details(["button", "level", "battery"])
}

private getCLUSTER_GROUPS() { 0x0004 }

private getREMOTE_BUTTONS() {
    [
        CONTROL_BUTTON:1,
        VOLUME_BUTTON:2
    ]
}

private getIkeaSoundControlNames() {
    [
        "control button",   // "Control button",
        "volume button",    // "increase volume button"
    ]
}

private channelNumber(String dni) {
    dni.split(":")[-1] as Integer
}

// parse events into attributes
def parse(String description) {
    log.debug "Parsing message from device: '$description'"

    def event = zigbee.getEvent(description)
    if (event) {
        log.debug "Creating event: ${event}"
        sendEvent(event)
    } else {
        if ((description?.startsWith("catchall:")) || (description?.startsWith("read attr -"))) {
            log.debug "Catch all: $description"
            log.debug zigbee.parseDescriptionAsMap(description)
            def descMap = zigbee.parseDescriptionAsMap(description)
            if (descMap.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.attrInt == 0x0021) {
                event = getBatteryEvent(zigbee.convertHexToInt(descMap.value))
            } else if (descMap.clusterInt == CLUSTER_SCENES ||
                     descMap.clusterInt == zigbee.ONOFF_CLUSTER ||
                     descMap.clusterInt == zigbee.LEVEL_CONTROL_CLUSTER) {
                event = getButtonEvent(descMap)
            }
        }

        def result = []
        if (event) {
            log.debug "Creating event: ${event}"
            result = createEvent(event)
        } else if (isBindingTableMessage(description)) {
            Integer groupAddr = getGroupAddrFromBindingTable(description)
            if (groupAddr != null) {
                List cmds = addHubToGroup(groupAddr)
                result = cmds?.collect { new physicalgraph.device.HubAction(it) }
            } else {
                groupAddr = 0x0000
                List cmds = addHubToGroup(groupAddr) +
                        zigbee.command(CLUSTER_GROUPS, 0x00, "${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr, 4))} 00")
                result = cmds?.collect { new physicalgraph.device.HubAction(it) }
            }
        }

        return result
    }
}

def installed() {
    def numberOfButtons = 2

    createChildButtonDevices(numberOfButtons)

    sendEvent(name: "supportedButtonValues", value: ["pushed", "pushed_2x", "pushed_3x"].encodeAsJSON(), displayed: false)
    sendEvent(name: "numberOfButtons", value: numberOfButtons, displayed: false)
    sendEvent(name: "button", value: "pushed", data: [buttonNumber: REMOTE_BUTTONS.CONTROL_BUTTON], displayed: false)

    def descriptionText = "Switch Level was changed to 0"
    sendEvent(name: "level", value: 0, descriptionText: descriptionText, isStateChange: true)

    sendEvent(name: "DeviceWatch-Enroll", value: JsonOutput.toJson([protocol: "zigbee", scheme:"untracked"]), displayed: false)

    state.level = 0
    state.start = now()
}

def updated() {
    if (childDevices && device.label != state.oldLabel) {
        childDevices.each {
            def newLabel = getButtonName(channelNumber(it.deviceNetworkId))
            it.setLabel(newLabel)
        }
        state.oldLabel = device.label
    }
}

def configure() {
    log.debug "Configuring device ${device.getDataValue("model")}"

    def cmds = zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x21, DataType.UINT8, 30, 21600, 0x01) +
            zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x21) +
            zigbee.addBinding(zigbee.ONOFF_CLUSTER) +
            zigbee.levelConfig()
            readDeviceBindingTable() // Need to read the binding table to see what group it's using

    cmds
}

def setLevel(value) {
    def descriptionText = "Switch Level was changed to $value"

    def valueaux = value as Integer
    state.level = Math.max(Math.min(valueaux, 99), 0)
    sendEvent(name: "level", value: state.level, descriptionText: descriptionText, isStateChange: true)
}

private Map getBatteryEvent(value) {
    def result = [:]
    result.value = value
    result.name = 'battery'
    result.descriptionText = "${device.displayName} battery was ${result.value}%"
    return result
}

private Map getButtonEvent(Map descMap) {
    def buttonState = ""
    def buttonNumber = 0
    Map result = [:]

    if (descMap.clusterInt == zigbee.ONOFF_CLUSTER) {
        buttonState = "pushed"
        if (descMap.commandInt == 0x02) {
            buttonNumber = 1
        }
    } else if (descMap.clusterInt == zigbee.LEVEL_CONTROL_CLUSTER) {
        switch (descMap.commandInt) {
            case 0x02:
                if (descMap.data[0] == "00") {
                    buttonState = "pushed_2x"
                } else {
                    buttonState = "pushed_3x"
                }
                buttonNumber = REMOTE_BUTTONS.CONTROL_BUTTON
                break;
            case 0x01:
                if (descMap.data[0] == "00") {
                    buttonState = "up"
                } else {
                    buttonState = "down"
                }
                buttonNumber = REMOTE_BUTTONS.VOLUME_BUTTON
                break;
            case 0x03:
                buttonState = "held"
                buttonNumber = REMOTE_BUTTONS.VOLUME_BUTTON
                break;
            default:
                break;
        }
    }

    if (buttonNumber != 0) {
        def descriptionText = "${getButtonName(buttonNumber)} was $buttonState"
        result = [name: "button", value: buttonState, data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true]

        // Create and send component event
        sendButtonEvent(buttonNumber, buttonState)
        sendLevelEvent(buttonNumber, buttonState)
    }
    result
}

private sendButtonEvent(buttonNumber, buttonState) {

    if (buttonNumber == REMOTE_BUTTONS.CONTROL_BUTTON) {
        def child = childDevices?.find { channelNumber(it.deviceNetworkId) == buttonNumber }

        if (child) {
            def descriptionText = "$child.displayName was $buttonState"

            child?.sendEvent([name: "button", value: buttonState, data: [buttonNumber: 1], descriptionText: descriptionText, isStateChange: true])
        } else {
            log.debug "Child device $buttonNumber not found!"
        }
    }
}

private sendLevelEvent(buttonNumber, buttonState) {

    if (buttonNumber == REMOTE_BUTTONS.VOLUME_BUTTON) {
        switch (buttonState) {
            case "up":
                state.start = now()
                state.direction = 1
                break
            case "down":
                state.start = now()
                state.direction = 0
                break
            case "held":
                long iTime = now() - state.start
                Integer iChange = 0

                // Ignore turns over 4 seconds, probably a lag issue
                if (iTime > 4000) {
                    iTime = 0
                }

                // Change based on 4 seconds for full 0-100 change in brightness
                iChange = iTime/4000 * 100

                state.level = state.direction ? state.level + iChange : state.level - iChange

                if (state.level < 0) {
                    state.level = 0
                } else if (state.level > 100) {
                    state.level = 100
                }

                def descriptionText = "Switch Level was changed to $state.level"
                sendEvent(name: "level", value: state.level, descriptionText: descriptionText, isStateChange: true)
                break
        }
    }
}

private getButtonLabel(buttonNum) {
    def label = "Button ${buttonNum}"

    label = ikeaSoundControlNames[buttonNum - 1]

    return label
}

private getButtonName(buttonNum) {
    return "${device.displayName} " + getButtonLabel(buttonNum)
}

private void createChildButtonDevices(numberOfButtons) {
    state.oldLabel = device.label

    log.debug "Creating $numberOfButtons children"

    for (i in 1..numberOfButtons) {
        log.debug "Creating child $i"
        def supportedButtons = (i == REMOTE_BUTTONS.CONTROL_BUTTON) ? ["pushed", "pushed_2x", "pushed_3x"] : ["held", "up", "down"]
        def child = addChildDevice("Child Button", "${device.deviceNetworkId}:${i}", device.hubId,
                [completedSetup: true, label: getButtonName(i),
                 isComponent: true, componentName: "button$i", componentLabel: getButtonLabel(i)])

        child.sendEvent(name: "supportedButtonValues", value: supportedButtons.encodeAsJSON(), displayed: false)
        child.sendEvent(name: "numberOfButtons", value: 1, displayed: false)
        child.sendEvent(name: "button", value: supportedButtons[0], data: [buttonNumber: 1], displayed: false)
    }
}

private Integer getGroupAddrFromBindingTable(description) {
    log.info "Parsing binding table - '$description'"
    def btr = zigbee.parseBindingTableResponse(description)
    def groupEntry = btr?.table_entries?.find { it.dstAddrMode == 1 }
    if (groupEntry != null) {
        log.info "Found group binding in the binding table: ${groupEntry}"
        Integer.parseInt(groupEntry.dstAddr, 16)
    } else {
        log.info "The binding table does not contain a group binding"
        null
    }
}

private List addHubToGroup(Integer groupAddr) {
    ["st cmd 0x0000 0x01 ${CLUSTER_GROUPS} 0x00 {${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr,4))} 00}",
     "delay 200"]
}

private List readDeviceBindingTable() {
    ["zdo mgmt-bind 0x${device.deviceNetworkId} 0", "delay 200"]
}
