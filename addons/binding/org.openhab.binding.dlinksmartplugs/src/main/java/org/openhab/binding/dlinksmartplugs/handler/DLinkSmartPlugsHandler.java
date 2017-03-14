/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.dlinksmartplugs.handler;

import static org.openhab.binding.dlinksmartplugs.DLinkSmartPlugsBindingConstants.*;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.dlinksmartplugs.helpers.SoapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DLinkSmartPlugsHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * This code is a port of the node.js module found here:
 * https://github.com/bikerp/dsp-w215-hnap
 *
 * @author Jereme Guenther - Initial contribution
 */
public class DLinkSmartPlugsHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(DLinkSmartPlugsHandler.class);
    private SoapClient client = new SoapClient();
    private String IPaddress = "";
    private String Pincode = "";
    private String clientStatus = "";

    public DLinkSmartPlugsHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channel = channelUID.getId();
        startCommand(channel, command);
    }

    @Override
    public void initialize() {
        /*
         * Get config values
         */
        int refresh = 10;
        try {
            Configuration config = this.getConfig();
            IPaddress = config.get("ipaddress").toString();
            Pincode = config.get("pincode").toString();
            refresh = ((BigDecimal) config.get(REFRESH)).intValue();
        } catch (NullPointerException e) {
            // keep default
        }

        /*
         * Test initial plug connection
         */
        if (IPaddress == null || IPaddress == "" || Pincode == null || Pincode == "") {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid configuration, ipaddress and pincode are required");
            return;
        }

        logger.info("Dlink Plug initializing with ipaddress={} and pincode={}", IPaddress, Pincode);
        clientStatus = client.Login("admin", Pincode, "http://" + IPaddress + "/HNAP1/");

        if (clientStatus == null || clientStatus.length() == 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Can not contact device");
        } else if (!clientStatus.equals("success")) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Can not access device, login failed");
        } else {
            updateStatus(ThingStatus.ONLINE);
        }

        /*
         * Setup a periodic refresh of the plugs status
         */

        Runnable refreshHVACUnits = new Runnable() {
            @Override
            public void run() {
                try {
                    Thing t = getThing();
                    DLinkSmartPlugsHandler h = (DLinkSmartPlugsHandler) t.getHandler();
                    h.startCommand("", null);

                } catch (Exception e) {
                    // logging happens inside the main method
                }
            }
        };
        scheduler.scheduleWithFixedDelay(refreshHVACUnits, 0, refresh, TimeUnit.SECONDS);
    }

    public void startCommand(String channel, Command command) {
        /*
         * Check to make sure we are online, if not try to get back online.
         */
        clientStatus = client.Login("admin", Pincode, "http://" + IPaddress + "/HNAP1/"); // run every time to keep
                                                                                          // security hash fresh
        if (!clientStatus.equals("success")) {
            clientStatus = client.Login("admin", Pincode, "http://" + IPaddress + "/HNAP1/");
            if (clientStatus.equals("success")) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Device " + IPaddress + " is offline.");
                return;
            }
        }

        /*
         * Attempt to run the command twice, sometimes the plug loses connection
         * and needs the second attempt to be successful
         */

        String commandresult = runCommand(channel, command);
        if (commandresult.equals("ERROR")) {
            commandresult = runCommand(channel, command);
        }
        logger.debug("DLINK PLUGS " + commandresult);
    }

    private String runCommand(String channel, Command command) {
        String ret = "";

        try {
            if (channel.equals(CHANNEL_Power) && command instanceof OnOffType) {
                if (((OnOffType) command) == OnOffType.ON) {
                    ret = client.Plug_On();
                } else {
                    ret = client.Plug_Off();
                }
            } else if (channel.equals(CHANNEL_Reboot) && command instanceof OnOffType) {
                if (((OnOffType) command) == OnOffType.ON) {
                    ret = client.Plug_Reboot();
                    clientStatus = ""; // reset the plug so we get new credentials when it comes back online
                    updateState(CHANNEL_Reboot, OnOffType.OFF);
                }
            } else {
                ret = client.Plug_State();
                updateState(CHANNEL_Power, ret.equals("ON") ? OnOffType.ON : OnOffType.OFF);
                setStatus(CHANNEL_State, ret);
            }
        } catch (Exception e) {
            logger.debug("Failed to set channel {} -> {}: {}", channel, command, e.getMessage());
            updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Device " + IPaddress + " returned an error.");
        }

        return ret;
    }

    private void setStatus(String channel, String value) {
        if (value.equals("ERROR")) {
            logger.info("Device {} failed to get channel: {}", IPaddress, channel);
        }

        updateState(channel, new StringType(value));
    }
}
