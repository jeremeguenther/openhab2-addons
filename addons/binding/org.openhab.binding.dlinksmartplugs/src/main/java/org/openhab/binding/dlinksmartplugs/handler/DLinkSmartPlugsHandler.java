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
import java.util.concurrent.ScheduledFuture;
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
import org.openhab.binding.dlinksmartplugs.internal.SoapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DLinkSmartPlugsHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jereme Guenther - Initial contribution
 */
public class DLinkSmartPlugsHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(DLinkSmartPlugsHandler.class);
    private SoapClient client = new SoapClient();
    private ScheduledFuture<?> pollingJob;

    private String IPaddress = "";
    private String Pincode = "";
    private String clientStatus = "";
    private int ConnectionTimeout = 3000;

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
            IPaddress = config.get(IPADDRESS).toString();
            Pincode = config.get(PINCODE).toString();
            refresh = ((BigDecimal) config.get(REFRESH)).intValue();
            ConnectionTimeout = ((BigDecimal) config.get(TIMEOUT)).intValue();
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
        clientStatus = client.Login("admin", Pincode, "http://" + IPaddress + "/HNAP1/", 2000);

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
        pollingJob = scheduler.scheduleWithFixedDelay(refreshHVACUnits, 0, refresh, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        pollingJob.cancel(true);
    }

    /**
     * Method to start processing for a channel command, or status refresh.
     *
     * @param channel
     * @param command
     */
    public void startCommand(String channel, Command command) {
        /*
         * Check to make sure we are online, if not try to get back online.
         * but, if already offline, don't waste a second call trying to get back online.
         * -- run every time to keep security hash fresh.
         */
        String currentStatus = clientStatus;

        clientStatus = client.Login("admin", Pincode, "http://" + IPaddress + "/HNAP1/", ConnectionTimeout);
        if (currentStatus.equals("success") && !clientStatus.equals("success")) {
            clientStatus = client.Login("admin", Pincode, "http://" + IPaddress + "/HNAP1/", ConnectionTimeout);
        }

        ThingStatus ts = getThing().getStatus();
        if (clientStatus.equals("success")) {
            if (!ts.equals(ThingStatus.ONLINE)) {
                updateStatus(ThingStatus.ONLINE);
            }
        } else {
            if (ts.equals(ThingStatus.ONLINE)) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Device " + IPaddress + " is offline.");
                updateState(CHANNEL_Power, OnOffType.OFF);
                if (isLinked(CHANNEL_State)) {
                    updateState(CHANNEL_State, new StringType("Offline"));
                }
            }
            return;
        }

        /*
         * Attempt to run the command twice, sometimes the plug loses connection
         * and needs the second attempt to be successful
         */

        String commandresult = runCommand(channel, command);
        if (commandresult.equals("ERROR")) {
            commandresult = runCommand(channel, command);
        }
        logger.debug("DLINK PLUGS commandResult {}", commandresult);
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
            } else if (channel.equals(CHANNEL_Reboot) && command instanceof StringType) {
                // if (((StringType) command).equals("GO")) {
                ret = client.Plug_Reboot();
                clientStatus = ""; // reset the plug so we get new credentials when it comes back online
                updateState(CHANNEL_Reboot, new StringType(""));
                updateState(CHANNEL_State, new StringType("Rebooting"));
                // }
            } else {
                ret = client.Plug_State();
                updateState(CHANNEL_Power, ret.equals("true") ? OnOffType.ON : OnOffType.OFF);
                if (isLinked(CHANNEL_State)) {
                    setStatus(CHANNEL_State, ret.equals("true") ? "ON" : "OFF");
                }
                if (isLinked(CHANNEL_Consumption)) {
                    setStatus(CHANNEL_Consumption, client.Plug_Consumption());
                }
                if (isLinked(CHANNEL_TotalConsumption)) {
                    setStatus(CHANNEL_TotalConsumption, client.Plug_TotalConsumption());
                }
                if (isLinked(CHANNEL_Temperature)) {
                    setStatus(CHANNEL_Temperature, client.Plug_Temperature());
                }
                if (isLinked(CHANNEL_PowerWarning)) {
                    setStatus(CHANNEL_PowerWarning, client.Plug_GetPowerWarning());
                }

            }
        } catch (Exception e) {
            logger.debug("Failed to set channel {} -> {}: {}", channel, command, e.getMessage());
            updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Device " + IPaddress + " returned an error.");
        }

        return ret;
    }

    private void setStatus(String channel, String value) {
        if (value.equals("ERROR") || value.equals("")) {
            logger.debug("Device {} failed to get channel: {}", IPaddress, channel);
        }

        updateState(channel, new StringType(value));
    }
}
