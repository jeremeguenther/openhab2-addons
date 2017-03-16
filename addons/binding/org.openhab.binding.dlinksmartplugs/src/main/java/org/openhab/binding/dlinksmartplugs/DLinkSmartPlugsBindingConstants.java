/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.dlinksmartplugs;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link DLinkSmartPlugsBinding} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jereme Guenther - Initial contribution
 */
public class DLinkSmartPlugsBindingConstants {

    public static final String BINDING_ID = "dlinksmartplugs";

    // List of all Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_PLUG = new ThingTypeUID(BINDING_ID, "dlinkplug");

    // List of all Channel ids
    public final static String CHANNEL_Power = "power";
    public final static String CHANNEL_Reboot = "reboot";
    public final static String CHANNEL_State = "state";
    public final static String CHANNEL_Consumption = "consumption";
    public final static String CHANNEL_TotalConsumption = "totalconsumption";
    public final static String CHANNEL_Temperature = "temperature";
    public final static String CHANNEL_PowerWarning = "powerwarning";

    // Config variables
    public static final String REFRESH = "refresh";
    public static final String IPADDRESS = "ipaddress";
    public static final String PINCODE = "pincode";
    public static final String TIMEOUT = "timeout";

}
