/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.openweathermap.internal.handler;

import static org.openhab.binding.openweathermap.internal.OpenWeatherMapBindingConstants.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.measure.quantity.Angle;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Length;
import javax.measure.quantity.Pressure;
import javax.measure.quantity.Speed;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.i18n.TimeZoneProvider;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.openweathermap.internal.config.OpenWeatherMapPWSConfiguration;
import org.openhab.binding.openweathermap.internal.connection.OpenWeatherMapCommunicationException;
import org.openhab.binding.openweathermap.internal.connection.OpenWeatherMapConfigurationException;
import org.openhab.binding.openweathermap.internal.connection.OpenWeatherMapConnection;
import org.openhab.binding.openweathermap.internal.dto.stations.OpenWeatherMapJsonMeasurementsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import tec.uom.se.unit.MetricPrefix;

/**
 * The {@link OpenWeatherMapPWSHandler} is responsible for handling commands, which are sent to one of the channels.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@NonNullByDefault
public class OpenWeatherMapPWSHandler extends AbstractOpenWeatherMapHandler {

    private final Logger logger = LoggerFactory.getLogger(OpenWeatherMapPWSHandler.class);

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.singleton(THING_TYPE_PWS);

    // keeps track of the data until exposed
    private final Map<Long, OpenWeatherMapJsonMeasurementsData> cache = new HashMap<>();

    // keeps track of the station id
    private String stationId = "";

    public OpenWeatherMapPWSHandler(final Thing thing, final TimeZoneProvider timeZoneProvider) {
        super(thing, timeZoneProvider);
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("Initialize OpenWeatherMapPWSHandler handler '{}'.", getThing().getUID());
        OpenWeatherMapPWSConfiguration config = getConfigAs(OpenWeatherMapPWSConfiguration.class);

        boolean configValid = true;
        if (config.stationId == null || config.stationId.trim().isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error-missing-station-id");
            configValid = false;
        }

        if (configValid) {
            stationId = config.stationId;
        }
    }

    @Override
    public void handleCommand(final ChannelUID channelUID, final Command command) {
        if (command instanceof RefreshType) {
            updateChannel(channelUID);
            return;
        }

        String channelId = channelUID.getIdWithoutGroup();
        logger.debug("Received new measurement for channel '{}': '{}'.", channelId, command);
        switch (channelId) {
            case CHANNEL_TIME_STAMP:
                if (command instanceof DateTimeType) {
                    ZonedDateTime timestamp = ((DateTimeType) command).getZonedDateTime();
                    getCachedData(timestamp).timestamp = timestamp.toEpochSecond();
                } else {
                    logger.warn("Channel '{}' requires DateTimeType but was: '{}'.", CHANNEL_TIME_STAMP,
                            command.getClass());
                }
                break;
            case CHANNEL_TEMPERATURE:
                if (command instanceof QuantityType
                        && ((QuantityType<?>) command).getUnit().isCompatible(SIUnits.CELSIUS)) {
                    @SuppressWarnings("unchecked")
                    QuantityType<Temperature> temperature = ((QuantityType<Temperature>) command)
                            .toUnit(SIUnits.CELSIUS);
                    if (temperature != null) {
                        getCachedData().temperature = temperature.doubleValue();
                    }
                } else if (command instanceof DecimalType) {
                    getCachedData().temperature = ((DecimalType) command).doubleValue();
                } else {
                    logger.warn("Channel '{}' requires QuantityType<Temperature> but was: '{}'.", CHANNEL_TIME_STAMP,
                            command.getClass());
                }
                break;
            case CHANNEL_WIND_SPEED:
                if (command instanceof QuantityType
                        && ((QuantityType<?>) command).getUnit().isCompatible(SmartHomeUnits.METRE_PER_SECOND)) {
                    @SuppressWarnings("unchecked")
                    QuantityType<Speed> speed = ((QuantityType<Speed>) command).toUnit(SmartHomeUnits.METRE_PER_SECOND);
                    if (speed != null) {
                        getCachedData().windSpeed = speed.doubleValue();
                    }
                } else if (command instanceof DecimalType) {
                    getCachedData().windSpeed = ((DecimalType) command).doubleValue();
                } else {
                    logger.warn("Channel '{}' requires QuantityType<Speed> but was: '{}'.", CHANNEL_WIND_SPEED,
                            command.getClass());
                }
                break;
            case CHANNEL_GUST_SPEED:
                if (command instanceof QuantityType
                        && ((QuantityType<?>) command).getUnit().isCompatible(SmartHomeUnits.METRE_PER_SECOND)) {
                    @SuppressWarnings("unchecked")
                    QuantityType<Speed> speed = ((QuantityType<Speed>) command).toUnit(SmartHomeUnits.METRE_PER_SECOND);
                    if (speed != null) {
                        getCachedData().gustSpeed = speed.doubleValue();
                    }
                } else if (command instanceof DecimalType) {
                    getCachedData().gustSpeed = ((DecimalType) command).doubleValue();
                } else {
                    logger.warn("Channel '{}' requires QuantityType<Speed> but was: '{}'.", CHANNEL_GUST_SPEED,
                            command.getClass());
                }
                break;
            case CHANNEL_WIND_DIRECTION:
                if (command instanceof QuantityType
                        && ((QuantityType<?>) command).getUnit().isCompatible(SmartHomeUnits.DEGREE_ANGLE)) {
                    @SuppressWarnings("unchecked")
                    QuantityType<Angle> angle = ((QuantityType<Angle>) command).toUnit(SmartHomeUnits.DEGREE_ANGLE);
                    if (angle != null) {
                        getCachedData().windDegree = angle.intValue();
                    }
                } else if (command instanceof DecimalType) {
                    getCachedData().windDegree = ((DecimalType) command).intValue();
                } else {
                    logger.warn("Channel '{}' requires QuantityType<Angle> but was: '{}'.", CHANNEL_WIND_DIRECTION,
                            command.getClass());
                }
                break;
            case CHANNEL_PRESSURE:
                if (command instanceof QuantityType
                        && ((QuantityType<?>) command).getUnit().isCompatible(SIUnits.PASCAL)) {
                    @SuppressWarnings("unchecked")
                    QuantityType<Pressure> pressure = ((QuantityType<Pressure>) command)
                            .toUnit(MetricPrefix.HECTO(SIUnits.PASCAL));
                    if (pressure != null) {
                        getCachedData().pressure = pressure.doubleValue();
                    }
                } else if (command instanceof DecimalType) {
                    getCachedData().pressure = ((DecimalType) command).doubleValue();
                } else {
                    logger.warn("Channel '{}' requires QuantityType<Pressure> but was: '{}'.", CHANNEL_PRESSURE,
                            command.getClass());
                }
                break;
            case CHANNEL_HUMIDITY:
                if (command instanceof QuantityType
                        && ((QuantityType<?>) command).getUnit().isCompatible(SmartHomeUnits.PERCENT)) {
                    @SuppressWarnings("unchecked")
                    QuantityType<Dimensionless> humidity = ((QuantityType<Dimensionless>) command)
                            .toUnit(SmartHomeUnits.PERCENT);
                    if (humidity != null) {
                        getCachedData().humidity = humidity.intValue();
                    }
                } else if (command instanceof DecimalType) {
                    getCachedData().humidity = ((DecimalType) command).intValue();
                } else {
                    logger.warn("Channel '{}' requires QuantityType<Dimensionless> but was: '{}'.", CHANNEL_HUMIDITY,
                            command.getClass());
                }
                break;
            case CHANNEL_RAIN:
                if (command instanceof QuantityType
                        && ((QuantityType<?>) command).getUnit().isCompatible(SIUnits.METRE)) {
                    @SuppressWarnings("unchecked")
                    QuantityType<Length> rain = ((QuantityType<Length>) command)
                            .toUnit(MetricPrefix.MILLI(SIUnits.METRE));
                    if (rain != null) {
                        getCachedData().rain = rain.doubleValue();
                    }
                } else if (command instanceof DecimalType) {
                    getCachedData().rain = ((DecimalType) command).doubleValue();
                } else {
                    logger.warn("Channel '{}' requires QuantityType<Length> but was: '{}'.", CHANNEL_RAIN,
                            command.getClass());
                }
                break;
            case CHANNEL_VISIBILITY:
                if (command instanceof QuantityType
                        && ((QuantityType<?>) command).getUnit().isCompatible(SIUnits.METRE)) {
                    @SuppressWarnings("unchecked")
                    QuantityType<Length> visibility = ((QuantityType<Length>) command)
                            .toUnit(MetricPrefix.KILO(SIUnits.METRE));
                    if (visibility != null) {
                        getCachedData().visibility = visibility.intValue();
                    }
                } else if (command instanceof DecimalType) {
                    getCachedData().visibility = ((DecimalType) command).intValue();
                } else {
                    logger.warn("Channel '{}' requires QuantityType<Length> but was: '{}'.", CHANNEL_VISIBILITY,
                            command.getClass());
                }
                break;
            default:
                logger.debug("Received command for unknown channel '{}'.", channelId);
                break;
        }
    }

    private OpenWeatherMapJsonMeasurementsData getCachedData() {
        return getCachedData(null);
    }

    private OpenWeatherMapJsonMeasurementsData getCachedData(final @Nullable ZonedDateTime timestamp) {
        ZonedDateTime key = timestamp == null ? ZonedDateTime.now() : timestamp;
        return cache.computeIfAbsent(Long.valueOf(key.truncatedTo(ChronoUnit.MINUTES).toEpochSecond()),
                v -> new OpenWeatherMapJsonMeasurementsData(stationId));
    }

    /**
     * Sends the measurements of the personal weather station and clears the cache.
     *
     * @param connection {@link OpenWeatherMapConnection} instance
     * @return <code>true</code>, if measurements of the PWS are successfully send
     */
    public boolean sendMeasurements(final OpenWeatherMapConnection connection) {
        if (cache.isEmpty()) {
            logger.debug("Cannot send measurements of thing '{}' as cache is empty.", getThing().getUID());
            return false;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Sending measurements of thing '{}': {}", getThing().getUID(),
                    new Gson().toJson(cache.values()));
        }

        boolean success = false;
        try {
            success = connection.sendMeasurements(cache.values());
            if (success) {
                clearMeasurements();
            }
        } catch (OpenWeatherMapCommunicationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getLocalizedMessage());
        } catch (OpenWeatherMapConfigurationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getLocalizedMessage());
        }
        return success;
    }

    /**
     * Clears the measurement cache of the personal weather station.
     *
     * @return <code>true</code>
     */
    public boolean clearMeasurements() {
        logger.debug("Clearing cache of thing '{}'.", getThing().getUID());
        cache.clear();
        return true;
    }

    @Override
    protected boolean requestData(final OpenWeatherMapConnection connection)
            throws OpenWeatherMapCommunicationException, OpenWeatherMapConfigurationException {
        logger.debug("Update PWS data of thing '{}'.", getThing().getUID());
        // TODO
        return true;
    }

    @Override
    protected void updateChannel(final ChannelUID channelUID) {
        // TODO
        updateState(channelUID, UnDefType.UNDEF);
    }
}
