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
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
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
 * The {@link DeviceHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Robert Bausdorf - Initial contribution
 * @author Christoph Weitkamp
 *
 */
public class DeviceHandler extends BaseThingHandler implements IFritzHandler {
    /**
     * Logger
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Ip of PL546E in standalone mode
     */
    private String soloIp;
    /**
     * the refresh interval which is used to poll values from the fritzaha.
     * server (optional, defaults to 15 s)
     */
    protected long refreshInterval = 15;
    /**
     * Interface object for querying the FRITZ!Box web interface
     */
    protected FritzahaWebInterface connection;
    /**
     * Job which will do the FRITZ!Box polling
     */
    private DeviceListPolling pollingRunnable;
    /**
     * Schedule for polling
     */
    private ScheduledFuture<?> pollingJob;

    public DeviceHandler(Thing thing) {
        super(thing);
        this.pollingRunnable = new DeviceListPolling(this);
    }

    /**
     * Initializes the bridge.
     */
    @Override
    public void initialize() {
        if (this.getThing().getThingTypeUID().equals(PL546E_STANDALONE_THING_TYPE)) {
            logger.debug("About to initialize thing " + BindingConstants.DEVICE_PL546E_STANDALONE);
            Thing thing = this.getThing();
            AvmFritzConfiguration config = this.getConfigAs(AvmFritzConfiguration.class);
            this.soloIp = config.getIpAddress();

            logger.debug("discovered PL546E initialized: " + config.toString());

            this.refreshInterval = config.getPollingInterval();
            this.connection = new FritzahaWebInterface(config, this);
            if (config.getPassword() != null) {
                this.onUpdate();
            } else {
                thing.setStatusInfo(new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "no password set"));
            }
        }
    }

    /**
     * Disposes the thing.
     */
    @Override
    public void dispose() {
        if (this.getThing().getThingTypeUID().equals(PL546E_STANDALONE_THING_TYPE)) {
            logger.debug("Handler disposed.");
            if (pollingJob != null && !pollingJob.isCancelled()) {
                pollingJob.cancel(true);
                pollingJob = null;
            }
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
            logger.warn("thing is null");
        }
    }

    /**
     * Handle the commands for switchable outlets or heating thermostats. TODO:
     * test switch behaviour on PL546E standalone
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Handle command {} for channel {}", channelUID.getIdWithoutGroup(), command.toString());
        FritzahaWebInterface fritzBox = null;
        if (!this.getThing().getThingTypeUID().equals(PL546E_STANDALONE_THING_TYPE)) {
            Bridge bridge = this.getBridge();
            if (bridge != null && bridge.getHandler() instanceof BoxHandler) {
                fritzBox = ((BoxHandler) bridge.getHandler()).getWebInterface();
            }
        } else {
            fritzBox = this.getWebInterface();
        }
        String ain = this.getThing().getConfiguration().get(THING_AIN).toString();
        switch (channelUID.getIdWithoutGroup()) {
            case CHANNEL_ONLINE:
                if (command instanceof RefreshType) {
                    fritzBox.getPresent(ain);
                }
                break;
            case CHANNEL_TEMP:
                if (command instanceof RefreshType) {
                    fritzBox.getTemperature(ain);
                }
                break;
            case CHANNEL_ENERGY:
                if (command instanceof RefreshType) {
                    fritzBox.getEnergy(ain);
                }
                break;
            case CHANNEL_POWER:
                if (command instanceof RefreshType) {
                    fritzBox.getPower(ain);
                }
                break;
            case CHANNEL_SWITCH:
                if (command instanceof OnOffType) {
                    fritzBox.setSwitch(ain, command.equals(OnOffType.ON) ? true : false);
                } else if (command instanceof RefreshType) {
                    fritzBox.getSwitch(ain);
                }
                break;
            case CHANNEL_OUTLET_MODE:
            case CHANNEL_LOCKED:
            case CHANNEL_DEVICE_LOCKED:
            case CHANNEL_ACTUALTEMP:
                if (command instanceof RefreshType) {
                    // not supported
                }
                break;
            case CHANNEL_SETTEMP:
                if (command instanceof DecimalType) {
                    BigDecimal temperature = HeatingModel.CelsiusToHKRVal(new BigDecimal(command.toString()));
                    this.updateSetModeChannel(temperature);
                    this.getThing().getConfiguration().put(THING_TSOLL, temperature);
                    fritzBox.setSetTemp(ain, temperature);
                } else if (command instanceof OnOffType) {
                    BigDecimal temperature = command.equals(OnOffType.ON) ? HeatingModel.TEMP_ON
                            : HeatingModel.TEMP_OFF;
                    fritzBox.setSetTemp(ain, temperature);
                } else if (command instanceof RefreshType) {
                    fritzBox.getSetTemp(ain);
                }
                break;
            case CHANNEL_ECOTEMP:
                if (command instanceof RefreshType) {
                    fritzBox.getEcoTemp(ain);
                }
                break;
            case CHANNEL_COMFORTTEMP:
                if (command instanceof RefreshType) {
                    fritzBox.getComfortTemp(ain);
                }
                break;
            case CHANNEL_NEXTCHANGE:
                if (command instanceof RefreshType) {
                    // not supported
                }
                break;
            case CHANNEL_NEXTTEMP:
                if (command instanceof RefreshType) {
                    // not supported
                }
                break;
            case CHANNEL_BATTERY:
                if (command instanceof RefreshType) {
                    // not supported
                }
            case CHANNEL_SET_MODE:
                if (command instanceof StringType) {
                    // not supported
                    logger.debug("XXReceived  option " + ((StringType) command).toString() + " on channel {}",
                            channelUID.getIdWithoutGroup());
                    if (((StringType) command).equals(CHANNEL_SET_MODE_OFF)) {
                        // Set_Mode "OFF"
                        this.updateSetTempChannel(HeatingModel.TEMP_OFF);
                        fritzBox.setSetTemp(ain, HeatingModel.TEMP_OFF);
                    } else if (((StringType) command).equals(CHANNEL_SET_MODE_ON)) {
                        // Set_Mode "ON"
                        // Thermostat function on, reset stored to tSoll value;
                        BigDecimal tSoll = (BigDecimal) this.getThing().getConfiguration().get(THING_TSOLL);
                        if (tSoll != null) {
                            this.updateSetTempChannel(tSoll);
                            fritzBox.setSetTemp(ain, tSoll);
                        }
                    } else if (((StringType) command).equals(CHANNEL_SET_MODE_BOOST)) {
                        // Set_Mode "BOOST"
                        fritzBox.setSetTemp(ain, HeatingModel.TEMP_ON);
                        this.updateSetTempChannel(HeatingModel.TEMP_ON);
                    } else {
                        logger.debug("Received unknown option on channel {}", channelUID.getIdWithoutGroup());
                    }
                }
                break;
            default:
                logger.debug("Received unknown channel {}", channelUID.getIdWithoutGroup());
                break;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStatusInfo(ThingStatus status, ThingStatusDetail statusDetail, String description) {
        super.updateStatus(status, statusDetail, description);
    }

    @Override
    public FritzahaWebInterface getWebInterface() {
        return this.connection;
    }

    @Override
    public void addDeviceList(DeviceModel device) {
        try {
            logger.debug("set device model: {}", device.toString());
            ThingUID thingUID = this.getThingUID(device);
            if (this.getThing() != null) {
                logger.debug("update thing {} with device model: {}", thingUID, device.toString());
                Channel channelOnline = thing.getChannel(INPUT_PRESENT);
                if (channelOnline != null) {
                    this.updateState(CHANNEL_ONLINE, (device.getPresent() == 1) ? OnOffType.ON : OnOffType.OFF);
                } else {
                    logger.warn("Channel {} in thing {} does not exist, please recreate the thing", CHANNEL_ONLINE,
                            thing.getUID());
                }
                if (device.isTempSensor() && device.getTemperature() != null) {
                    this.updateTemperatureChannel(device.getTemperature().getCelsius());
                }
                if (device.isPowermeter() && device.getPowermeter() != null) {
                    this.updateEnergyChannel(device.getPowermeter().getEnergy());
                    this.updatePowerChannel(device.getPowermeter().getPower());
                }
                if (device.isSwitchableOutlet() && device.getSwitch() != null) {
                    if (device.getSwitch().getState() == null) {
                        this.updateState(CHANNEL_SWITCH, UnDefType.UNDEF);
                    } else if (device.getSwitch().getState().equals(SwitchModel.ON)) {
                        this.updateSwitchChannel(OnOffType.ON);
                    } else if (device.getSwitch().getState().equals(SwitchModel.OFF)) {
                        this.updateSwitchChannel(OnOffType.OFF);
                    } else {
                        logger.warn("Received unknown value {} for channel {}", device.getSwitch().getState(),
                                CHANNEL_SWITCH);
                    }
                    this.updateState(CHANNEL_OUTLET_MODE, new StringType(device.getSwitch().getMode()));
                    if (device.getSwitch().getLock() == null) {
                        this.updateState(CHANNEL_LOCKED, UnDefType.UNDEF);
                    } else if (device.getSwitch().getLock().equals(SwitchModel.ON)) {
                        this.updateState(CHANNEL_LOCKED, OnOffType.ON);
                    } else if (device.getSwitch().getLock().equals(SwitchModel.OFF)) {
                        this.updateState(CHANNEL_LOCKED, OnOffType.OFF);
                    } else {
                        logger.warn("Unknown state {} for channel {}", device.getSwitch().getLock(), CHANNEL_LOCKED);
                    }
                    if (device.getSwitch().getDevicelock() == null) {
                        this.updateState(CHANNEL_DEVICE_LOCKED, UnDefType.UNDEF);
                    } else if (device.getSwitch().getDevicelock().equals(SwitchModel.ON)) {
                        this.updateState(CHANNEL_DEVICE_LOCKED, OnOffType.ON);
                    } else if (device.getSwitch().getDevicelock().equals(SwitchModel.OFF)) {
                        this.updateState(CHANNEL_DEVICE_LOCKED, OnOffType.OFF);
                    } else {
                        logger.warn("Unknown state {} for channel {}", device.getSwitch().getDevicelock(),
                                CHANNEL_DEVICE_LOCKED);
                    }
                }
                if (device.isHeatingThermostat() && device.getHkr() != null) {
                    this.updateActualTempChannel(device.getHkr().getTist());
                    this.updateSetTempChannel(device.getHkr().getTsoll());
                    this.updateSetModeChannel(device.getHkr().getTsoll());
                    this.updateEcoTempChannel(HeatingModel.HKRValToCelsius(device.getHkr().getAbsenk()));
                    this.updateComfortTempChannel(HeatingModel.HKRValToCelsius(device.getHkr().getKomfort()));
                    if (device.getHkr().getNextchange() != null) {
                        if (device.getHkr().getNextchange().getEndperiod() == 0) {
                            this.updateState(CHANNEL_NEXTCHANGE, UnDefType.UNDEF);
                        } else {
                            this.updateNextChangeChannel(device.getHkr().getNextchange().getEndperiod());
                        }
                        this.updateNextTempChannel(
                                HeatingModel.HKRValToCelsius(device.getHkr().getNextchange().getTchange()));
                    }
                    if (device.getHkr().getBatterylow() == null) {
                        this.updateState(CHANNEL_BATTERY, UnDefType.UNDEF);
                    } else if (device.getHkr().getBatterylow().equals(HeatingModel.BATTERY_ON)) {
                        this.updateBatteryChannel(OnOffType.ON);
                    } else if (device.getHkr().getBatterylow().equals(HeatingModel.BATTERY_OFF)) {
                        this.updateBatteryChannel(OnOffType.OFF);
                    } else {
                        logger.warn("Received unknown value {} for channel {}", device.getHkr().getBatterylow(),
                                INPUT_BATTERY);
                    }
                }
                // save AIN to config for PL546E standalone
                if (this.getThing().getConfiguration().get(THING_AIN) == null) {
                    this.getThing().getConfiguration().put(THING_AIN, device.getIdentifier());
                }
            }
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
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
        } else if (thingTypeUID.equals(PL546E_STANDALONE_THING_TYPE)) {
            String thingName = this.soloIp.replaceAll("[^a-zA-Z0-9_]", "_");
            ThingUID thingUID = new ThingUID(thingTypeUID, thingName);
            return thingUID;
        } else {
            return null;
        }
    }

    public void updateTemperatureChannel(BigDecimal temperature) {
        this.updateState(CHANNEL_TEMP, new DecimalType(temperature));
    }

    public void updateSetModeChannel(StringType mode) {
        this.updateState(CHANNEL_SET_MODE, mode);
    }

    public void updateSetModeChannel(BigDecimal hkrValue) {
        if (hkrValue.equals(HeatingModel.TEMP_OFF)) {
            this.updateState(CHANNEL_SET_MODE, new StringType(CHANNEL_SET_MODE_OFF));
        } else if (hkrValue.equals(HeatingModel.TEMP_ON)) {
            this.updateState(CHANNEL_SET_MODE, new StringType(CHANNEL_SET_MODE_BOOST));
        } else {
            this.updateState(CHANNEL_SET_MODE, new StringType(CHANNEL_SET_MODE_ON));
        }

    }

    public void updateEnergyChannel(BigDecimal energy) {
        this.updateState(CHANNEL_ENERGY, new DecimalType(energy));
    }

    public void updatePowerChannel(BigDecimal power) {
        this.updateState(CHANNEL_POWER, new DecimalType(power));
    }

    public void updateSwitchChannel(OnOffType state) {
        this.updateState(CHANNEL_SWITCH, state);
    }

    public void updateActualTempChannel(BigDecimal temperature) {
        this.updateState(CHANNEL_ACTUALTEMP, new DecimalType(temperature));
    }

    public void updateSetTempChannel(BigDecimal hkrValue) {
        if (hkrValue.equals(HeatingModel.TEMP_OFF)) {
            this.updateState(CHANNEL_SETTEMP, new DecimalType(HeatingModel.TEMP_MIN_CELSIUS));
        } else if (hkrValue.equals(HeatingModel.TEMP_ON)) {
            this.updateState(CHANNEL_SETTEMP, new DecimalType(HeatingModel.TEMP_MAX_CELSIUS));
        } else {
            this.updateState(CHANNEL_SETTEMP, new DecimalType(HeatingModel.HKRValToCelsius(hkrValue)));
            this.getThing().getConfiguration().put(BindingConstants.THING_TSOLL, hkrValue);
        }
    }

    public void updateEcoTempChannel(BigDecimal temperature) {
        this.updateState(CHANNEL_ECOTEMP, new DecimalType(temperature));
    }

    public void updateComfortTempChannel(BigDecimal temperature) {
        this.updateState(CHANNEL_COMFORTTEMP, new DecimalType(temperature));
    }

    public void updateNextChangeChannel(int timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(timestamp * 1000L));
        this.updateState(CHANNEL_NEXTCHANGE, new DateTimeType(calendar));
    }

    public void updateNextTempChannel(BigDecimal temperature) {
        this.updateState(CHANNEL_NEXTTEMP, new DecimalType(temperature));
    }

    public void updateBatteryChannel(OnOffType state) {
        this.updateState(CHANNEL_BATTERY, state);
    }
}
