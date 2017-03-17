# DLinkSmartPlugs Binding

This binding allows the controlling of DLink's Smart Plugs.

## Supported Things

Currently, the binding supports a single type of Thing, being the ```dlinkplug``` Thing.

## Binding Configuration

The binding requires at a minimum an ip address to the plug, and the pin code found on the back of the plug.
Optional parameters:
refresh - this tells the binding how often to query the plug for updated stats information, it defaults to 10 seconds.
timeout - this tells the binding long long to wait for a soap request to come back, it defaults to 3 seconds.

This code is a port of the node.js module found here:
https://github.com/bikerp/dsp-w215-hnapNote.

## Thing Configuration

The `dlinkplug` Thing supports multiple different channels, however not all these channels are supported by all plugs:

For each smart plug a separate Thing has to be defined.

```
dlinksmartplugs:dlinkplug:<customplugname> [ ipaddress="<The ip address>", pincode="<pin code on plug>", refresh=<seconds>, timeout=<milliseconds> ]
```

## Channels

All Things support the following channels, however not all these channels are supported by all plugs.
If a channel is not supported by the plug you will not receive a valid status update for it.
Basic DLink plugs only support the first three channels (power, reboot, state).

| Channel Type ID | Item Type    | Description  |
|-----------------|------------------------|--------------|----------------- |------------- |
| power | Switch       | Trigger the ON/OFF state of the plug |
| reboot | String       | Any string passed in will trigger a reboot |
| state | String       | The current power state of the plug ON/OFF/Rebooting/Offline |
| consumption | String       | The current consumption level for the plug |
| totalconsumption | String       | The total consumption level for the plug |
| temperature | String       | The current plug temperature |
| powerwarning | String       | If the plug is in a critical state |

## Full Example

**demo.things**

```
dlinksmartplugs:dlinkplug:<customplugname> [ ipaddress="<The ip address>", pincode="<pin code on plug>", refresh=<seconds>, timeout=<milliseconds> ]
dlinksmartplugs:dlinkplug:testplug [ ipaddress="192.168.1.10", pincode="123456", refresh=10, timeout=3000 ]
```

**demo.items**

```
Switch <custom item name>          "<visible text>"      [ "<tag, optional>" ]  { channel="dlinksmartplugs:dlinkplug:<customplugname>:<channel>" }
Switch testplug_power          "Test Plug"      [ "Switchable" ]  { channel="dlinksmartplugs:dlinkplug:testplug:power" }
```