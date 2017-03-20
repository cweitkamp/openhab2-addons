/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.avmfritz.handler;

import static org.openhab.binding.avmfritz.BindingConstants.*;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.avmfritz.BindingConstants;
import org.openhab.binding.avmfritz.config.AvmFritzConfiguration;
import org.openhab.binding.avmfritz.internal.ahamodel.DeviceModel;
import org.openhab.binding.avmfritz.internal.ahamodel.HeatingModel;
import org.openhab.binding.avmfritz.internal.ahamodel.SwitchModel;
import org.openhab.binding.avmfritz.internal.hardware.FritzahaWebInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for a FRITZ!Box device. Handles polling of values from AHA devices.
 *
 * @author Robert Bausdorf
 * @author Christoph Weitkamp
 *
 */
public class BoxHandler extends BaseBridgeHandler implements IFritzHandler {
    /**
     * Logger
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * the refresh interval which is used to poll values from the fritzaha.
     * server (optional, defaults to 15 s)
     */
    private long refreshInterval = 15;
    /**
     * Interface object for querying the FRITZ!Box web interface
     */
    private FritzahaWebInterface connection;
    /**
     * Holder for last data received from the box.
     */
    private Map<String, DeviceModel> deviceList;
    /**
     * Job which will do the FRITZ!Box polling
     */
    private DeviceListPolling pollingRunnable;
    /**
     * Schedule for polling
     */
    private ScheduledFuture<?> pollingJob;

    /**
     * Constructor
     *
     * @param bridge
     *            Bridge object representing a FRITZ!Box
     */
    public BoxHandler(Bridge bridge) {
        super(bridge);
        this.deviceList = new TreeMap<String, DeviceModel>();
        this.pollingRunnable = new DeviceListPolling(this);
    }

    /**
     * Initializes the bridge.
     */
    @Override
    public void initialize() {
        logger.debug("About to initialize bridge " + BindingConstants.BRIDGE_FRITZBOX);
        Bridge bridge = this.getThing();
        AvmFritzConfiguration config = this.getConfigAs(AvmFritzConfiguration.class);

        logger.debug("discovered fritzaha bridge initialized: " + config.toString());

        this.refreshInterval = config.getPollingInterval();
        this.connection = new FritzahaWebInterface(config, this);
        if (config.getPassword() != null) {
            this.onUpdate();
        } else {
            bridge.setStatusInfo(
                    new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "no password set"));
        }
    }

    /**
     * Disposes the bridge.
     */
    @Override
    public void dispose() {
        logger.debug("Handler disposed.");
        if (pollingJob != null && !pollingJob.isCancelled()) {
            pollingJob.cancel(true);
            pollingJob = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeviceList(DeviceModel device) {
        try {
            logger.debug("set device model: {}", device.toString());
            this.deviceList.put(device.getIdentifier(), device);
            ThingUID thingUID = this.getThingUID(device);
            Thing thing = this.getThingByUID(thingUID);
            if (thing != null) {
                logger.debug("update thing {} with device model: {}", thingUID, device.toString());
                this.updateThingFromDevice(thing, device);
            }
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FritzahaWebInterface getWebInterface() {
        return this.connection;
    }

    /**
     * Updates things from device model.
     *
     * @param thing
     *            Thing to be updated.
     * @param device
     *            Device model with new data.
     */
    private void updateThingFromDevice(Thing thing, DeviceModel device) {
        if (thing == null || device == null) {
            throw new IllegalArgumentException("thing or device null, cannot perform update");
        }
        Channel channelOnline = thing.getChannel(CHANNEL_ONLINE);
        if (channelOnline != null) {
            this.updateState(channelOnline.getUID(), (device.getPresent() == 1) ? OnOffType.ON : OnOffType.OFF);
        } else {
            logger.warn("Channel {} in thing {} does not exist, please recreate the thing", CHANNEL_ONLINE,
                    thing.getUID());
        }
        if (device.getPresent() == 1) {
            thing.setStatusInfo(new ThingStatusInfo(ThingStatus.ONLINE, ThingStatusDetail.NONE, null));
            logger.debug("about to update thing {} from device {}", thing.getUID(), device.toString());
            if (device.isTempSensor() && device.getTemperature() != null) {
                Channel channelTemp = thing.getChannel(CHANNEL_TEMP);
                this.updateState(channelTemp.getUID(), new DecimalType(device.getTemperature().getCelsius()));
            }
            if (device.isPowermeter() && device.getPowermeter() != null) {
                Channel channelEnergy = thing.getChannel(CHANNEL_ENERGY);
                this.updateState(channelEnergy.getUID(), new DecimalType(device.getPowermeter().getEnergy()));
                Channel channelPower = thing.getChannel(CHANNEL_POWER);
                this.updateState(channelPower.getUID(), new DecimalType(device.getPowermeter().getPower()));
            }
            if (device.isSwitchableOutlet() && device.getSwitch() != null) {
                Channel channelSwitch = thing.getChannel(CHANNEL_SWITCH);
                if (device.getSwitch().getState() == null) {
                    this.updateState(channelSwitch.getUID(), UnDefType.UNDEF);
                } else if (device.getSwitch().getState().equals(SwitchModel.ON)) {
                    this.updateState(channelSwitch.getUID(), OnOffType.ON);
                } else if (device.getSwitch().getState().equals(SwitchModel.OFF)) {
                    this.updateState(channelSwitch.getUID(), OnOffType.OFF);
                } else {
                    logger.warn("Received unknown value {} for channel {}", device.getSwitch().getState(),
                            channelSwitch.getUID());
                }
                Channel channelMode = thing.getChannel(CHANNEL_OUTLET_MODE);
                if (channelMode != null) {
                    this.updateState(channelMode.getUID(), new StringType(device.getSwitch().getMode()));
                } else {
                    logger.warn("Channel {} in thing {} does not exist, please recreate the thing", CHANNEL_OUTLET_MODE,
                            thing.getUID());
                }
                Channel channelLocked = thing.getChannel(CHANNEL_LOCKED);
                if (channelLocked != null) {
                    if (device.getSwitch().getLock() == null) {
                        this.updateState(channelLocked.getUID(), UnDefType.UNDEF);
                    } else if (device.getSwitch().getLock().equals(SwitchModel.ON)) {
                        this.updateState(channelLocked.getUID(), OnOffType.ON);
                    } else if (device.getSwitch().getLock().equals(SwitchModel.OFF)) {
                        this.updateState(channelLocked.getUID(), OnOffType.OFF);
                    } else {
                        logger.warn("Unknown state {} for channel {}", device.getSwitch().getLock(),
                                channelLocked.getUID());
                    }
                } else {
                    logger.warn("Channel {} in thing {} does not exist, please recreate the thing", CHANNEL_LOCKED,
                            thing.getUID());
                }
                Channel channelDeviceLocked = thing.getChannel(CHANNEL_DEVICE_LOCKED);
                if (channelDeviceLocked != null) {
                    if (device.getSwitch().getDevicelock() == null) {
                        this.updateState(channelDeviceLocked.getUID(), UnDefType.UNDEF);
                    } else if (device.getSwitch().getDevicelock().equals(SwitchModel.ON)) {
                        this.updateState(channelDeviceLocked.getUID(), OnOffType.ON);
                    } else if (device.getSwitch().getDevicelock().equals(SwitchModel.OFF)) {
                        this.updateState(channelDeviceLocked.getUID(), OnOffType.OFF);
                    } else {
                        logger.warn("Unknown state {} for channel {}", device.getSwitch().getDevicelock(),
                                channelDeviceLocked.getUID());
                    }
                } else {
                    logger.warn("Channel {} in thing {} does not exist, please recreate the thing",
                            CHANNEL_DEVICE_LOCKED, thing.getUID());
                }
            }
            if (device.isHeatingThermostat() && device.getHkr() != null) {
                Channel channelActualTemp = thing.getChannel(CHANNEL_ACTUALTEMP);

                this.updateState(channelActualTemp.getUID(), new DecimalType(device.getHkr().getTist()));
                Channel channelSetTemp = thing.getChannel(CHANNEL_SETTEMP);
                Channel channelSetMode = thing.getChannel(CHANNEL_SET_MODE);
                BigDecimal tSoll = device.getHkr().getTsoll();

                if (tSoll.equals(HeatingModel.TEMP_OFF)) {
                    this.updateState(channelSetTemp.getUID(), new DecimalType(HeatingModel.TEMP_MIN_CELSIUS));
                    this.updateState(channelSetMode.getUID(), new StringType(CHANNEL_SET_MODE_OFF));
                    if (thing.getConfiguration().get(THING_TSOLL) == null) {
                        thing.getConfiguration().put(THING_TSOLL,
                                HeatingModel.CelsiusToHKRVal(HeatingModel.TEMP_MIN_CELSIUS));
                    }
                } else if (tSoll.equals(HeatingModel.TEMP_ON)) {
                    this.updateState(channelSetTemp.getUID(), new DecimalType(HeatingModel.TEMP_MAX_CELSIUS));
                    this.updateState(channelSetMode.getUID(), new StringType(CHANNEL_SET_MODE_BOOST));
                    if (thing.getConfiguration().get(THING_TSOLL) == null) {
                        thing.getConfiguration().put(THING_TSOLL,
                                HeatingModel.CelsiusToHKRVal(HeatingModel.TEMP_MAX_CELSIUS));
                    }
                } else {
                    this.updateState(channelSetTemp.getUID(), new DecimalType(HeatingModel.HKRValToCelsius(tSoll)));
                    this.updateState(channelSetMode.getUID(), new StringType(CHANNEL_SET_MODE_ON));
                    if (thing.getConfiguration().get(THING_TSOLL) == null) {
                        thing.getConfiguration().put(THING_TSOLL, tSoll);
                    }
                }

                Channel channelEcoTemp = thing.getChannel(CHANNEL_ECOTEMP);
                this.updateState(channelEcoTemp.getUID(),
                        new DecimalType(HeatingModel.HKRValToCelsius(device.getHkr().getAbsenk())));
                Channel channelComfortTemp = thing.getChannel(CHANNEL_COMFORTTEMP);
                this.updateState(channelComfortTemp.getUID(),
                        new DecimalType(HeatingModel.HKRValToCelsius(device.getHkr().getKomfort())));
                if (device.getHkr().getNextchange() != null) {
                    Channel channelNextChange = thing.getChannel(CHANNEL_NEXTCHANGE);
                    if (device.getHkr().getNextchange().getEndperiod() == 0) {
                        this.updateState(channelNextChange.getUID(), UnDefType.UNDEF);
                    } else {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(new Date(device.getHkr().getNextchange().getEndperiod() * 1000L));
                        this.updateState(channelNextChange.getUID(), new DateTimeType(calendar));
                    }
                    Channel channelNextTemp = thing.getChannel(CHANNEL_NEXTTEMP);
                    this.updateState(channelNextTemp.getUID(), new DecimalType(
                            HeatingModel.HKRValToCelsius(device.getHkr().getNextchange().getTchange())));
                }
                Channel channelBattery = thing.getChannel(CHANNEL_BATTERY);
                if (device.getHkr().getBatterylow() == null) {
                    this.updateState(channelBattery.getUID(), UnDefType.UNDEF);
                } else if (device.getHkr().getBatterylow().equals(HeatingModel.BATTERY_ON)) {
                    this.updateState(channelBattery.getUID(), OnOffType.ON);
                } else if (device.getHkr().getBatterylow().equals(HeatingModel.BATTERY_OFF)) {
                    this.updateState(channelBattery.getUID(), OnOffType.OFF);
                } else {
                    logger.warn("Received unknown value {} for channel {}", device.getHkr().getBatterylow(),
                            channelBattery.getUID());
                }
            }
        } else {
            thing.setStatusInfo(new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.NONE, null));
        }
    }

    /**
     * Builds a {@link ThingUID} from a device model. The UID is build from the
     * {@link BindingConstants#BINDING_ID} and value of
     * {@link DeviceModel#getProductName()} in which all characters NOT matching
     * the regex [^a-zA-Z0-9_] are replaced by "_".
     *
     * @param device
     *            Discovered device model
     * @return ThingUID without illegal characters.
     */
    public ThingUID getThingUID(DeviceModel device) {
        ThingUID bridgeUID = this.getThing().getUID();
        ThingTypeUID thingTypeUID = new ThingTypeUID(BINDING_ID,
                device.getProductName().replaceAll("[^a-zA-Z0-9_]", "_"));

        if (BindingConstants.SUPPORTED_DEVICE_THING_TYPES_UIDS.contains(thingTypeUID)) {
            String thingName = device.getIdentifier().replaceAll("[^a-zA-Z0-9_]", "_");
            ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, thingName);
            return thingUID;
        } else {
            return null;
        }
    }

    /**
     * Start the polling.
     */
    private synchronized void onUpdate() {
        if (this.getThing() != null) {
            if (pollingJob == null || pollingJob.isCancelled()) {
                logger.debug("start polling job at intervall " + refreshInterval);
                pollingJob = scheduler.scheduleWithFixedDelay(pollingRunnable, 1, refreshInterval, TimeUnit.SECONDS);
            } else {
                logger.debug("pollingJob active");
            }
        } else {
            logger.warn("bridge is null");
        }
    }

    /**
     * Just logging - nothing to do.
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("command for " + channelUID.getAsString() + ": " + command.toString());
        if (command instanceof RefreshType) {
            // TODO
            return;
        }
    }

    /**
     * Called from {@link FritzahaWebInterface#authenticate()} to update the
     * bridge status because updateStatus is protected.
     *
     * @param status
     *            Bridge status
     * @param statusDetail
     *            Bridge status detail
     * @param description
     *            Bridge status description
     */
    @Override
    public void setStatusInfo(ThingStatus status, ThingStatusDetail statusDetail, String description) {
        super.updateStatus(status, statusDetail, description);
    }
}
