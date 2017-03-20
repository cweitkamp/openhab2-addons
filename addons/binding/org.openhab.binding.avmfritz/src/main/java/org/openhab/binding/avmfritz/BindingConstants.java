/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.avmfritz;

import java.util.Set;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

import com.google.common.collect.ImmutableSet;

/**
 * This class defines common constants, which are used across the whole binding.
 *
 * @author Robert Bausdorf
 * @author Christoph Weitkamp
 *
 */
public class BindingConstants {

    public static final String BINDING_ID = "avmfritz";
    public static final String IP_ADDRESS = "ipAddress";
    public static final String BRIDGE_FRITZBOX = "fritzbox";
    public static final String SERIAL_NUMBER = "serialNumber";
    public static final String DEVICE_ID = "deviceId";
    public static final String BRIDGE_MODEL_NAME = "FRITZ!Box";
    public static final String PL546E_MODEL_NAME = "FRITZ!Powerline";
    public static final String THING_AIN = "ain";
    public static final String THING_TSOLL = "tsoll";

    // List of main device types
    public static final String DEVICE_DECT300 = "FRITZ_DECT_300";
    public static final String DEVICE_DECT210 = "FRITZ_DECT_210";
    public static final String DEVICE_DECT200 = "FRITZ_DECT_200";
    public static final String DEVICE_DECT100 = "FRITZ_DECT_Repeater_100";
    public static final String DEVICE_PL546E = "FRITZ_Powerline_546E";
    public static final String DEVICE_PL546E_STANDALONE = "FRITZ_Powerline_546E_Solo";
    public static final String DEVICE_COMETDECT = "Comet_DECT";

    // List of all Thing Type UIDs
    public final static ThingTypeUID BRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID, BRIDGE_FRITZBOX);

    public final static ThingTypeUID DECT300_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_DECT300);
    public final static ThingTypeUID DECT210_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_DECT210);
    public final static ThingTypeUID DECT200_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_DECT200);
    public final static ThingTypeUID DECT100_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_DECT100);
    public final static ThingTypeUID PL546E_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_PL546E);
    public final static ThingTypeUID PL546E_STANDALONE_THING_TYPE = new ThingTypeUID(BINDING_ID,
            DEVICE_PL546E_STANDALONE);
    public final static ThingTypeUID COMETDECT_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_COMETDECT);

    // List of all Channel ids
    public final static String CHANNEL_ONLINE = "online";
    public final static String CHANNEL_TEMP = "temperature";
    public final static String CHANNEL_ENERGY = "energy";
    public final static String CHANNEL_POWER = "power";
    public final static String CHANNEL_SWITCH = "outlet";
    public final static String CHANNEL_OUTLET_MODE = "outlet_mode";
    public final static String CHANNEL_LOCKED = "locked";
    public final static String CHANNEL_DEVICE_LOCKED = "device_locked";
    public final static String CHANNEL_ACTUALTEMP = "actual_temp";
    public final static String CHANNEL_SETTEMP = "set_temp";
    public final static String CHANNEL_ECOTEMP = "eco_temp";
    public final static String CHANNEL_COMFORTTEMP = "comfort_temp";
    public final static String CHANNEL_NEXTCHANGE = "next_change";
    public final static String CHANNEL_NEXTTEMP = "next_temp";
    public final static String CHANNEL_BATTERY = "battery_low";
    public final static String CHANNEL_SET_MODE = "set_mode";

    // List of string type channel options as defined in thing-types.xml
    public static final String CHANNEL_SET_MODE_OFF = "1";
    public static final String CHANNEL_SET_MODE_ON = "2";
    public static final String CHANNEL_SET_MODE_BOOST = "3";

    // List of all Input tags
    public final static String INPUT_PRESENT = "present";
    public final static String INPUT_MODE = "mode";
    public final static String INPUT_LOCKED = "lock";
    public final static String INPUT_DEVICE_LOCKED = "device_lock";
    public final static String INPUT_ACTUALTEMP = "tist";
    public final static String INPUT_SETTEMP = "tsoll";
    public final static String INPUT_ECOTEMP = "absenk";
    public final static String INPUT_COMFORTTEMP = "komfort";
    public final static String INPUT_NEXTCHANGE = "endperiod";
    public final static String INPUT_NEXTTEMP = "tchange";
    public final static String INPUT_BATTERY = "batterylow";

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = ImmutableSet.of(DECT100_THING_TYPE,
            DECT200_THING_TYPE, DECT210_THING_TYPE, DECT300_THING_TYPE, PL546E_THING_TYPE, BRIDGE_THING_TYPE,
            PL546E_STANDALONE_THING_TYPE, COMETDECT_THING_TYPE);

    public final static Set<ThingTypeUID> SUPPORTED_DEVICE_THING_TYPES_UIDS = ImmutableSet.of(DECT100_THING_TYPE,
            DECT200_THING_TYPE, DECT210_THING_TYPE, DECT300_THING_TYPE, PL546E_THING_TYPE, COMETDECT_THING_TYPE);

    public final static Set<ThingTypeUID> SUPPORTED_BRIDGE_THING_TYPES_UIDS = ImmutableSet.of(BRIDGE_THING_TYPE,
            PL546E_STANDALONE_THING_TYPE);
}
