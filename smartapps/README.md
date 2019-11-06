# SmartApps

### Sonos Remote Control
SmartApp for controlling Sonos device with Ikea Symfonisk Controller. Supported functions are:
- Volume (dimmer up/down)
- Play/Stop (Click)
- Next Track (Double click)
- Previous Track (Treble click)

**Volume:** Volume control tries to prevent accidental "too big volume change" in case of Sonos device was previously controlled
through some other device (directly from device, phone, etc.). This happens by syncing the dimmer level with Sonos volume so level of controlling device might change "automatically" in some cases.

### Light Control
SmartApp for controlling light bulbs with Ikea Symfonisk Controller. Supported functions are:
- Brightness (dimmer up/down)
- ON/OFF (Click)
- Color Temperature (Double click)
- Full Brightness (Treble click)

**Color Temperature:** Double clicking steps through colors "candle light", "warm white", "cool white".

**Full Brightness:** Treble click sets the brightness of the light to 100%. Treble clicking again returns to previous brightness. Notice that using dimmer when after setting up "Full Brightness" continues from level which was set before enabling it.
