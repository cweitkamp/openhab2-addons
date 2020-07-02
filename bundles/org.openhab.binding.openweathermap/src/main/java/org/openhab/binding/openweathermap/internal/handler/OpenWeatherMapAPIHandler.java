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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.i18n.LocaleProvider;
import org.eclipse.smarthome.core.i18n.LocationProvider;
import org.eclipse.smarthome.core.library.types.PointType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerService;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.openweathermap.internal.actions.OpenWeatherMapThingActions;
import org.openhab.binding.openweathermap.internal.config.OpenWeatherMapAPIConfiguration;
import org.openhab.binding.openweathermap.internal.connection.OpenWeatherMapCommunicationException;
import org.openhab.binding.openweathermap.internal.connection.OpenWeatherMapConfigurationException;
import org.openhab.binding.openweathermap.internal.connection.OpenWeatherMapConnection;
import org.openhab.binding.openweathermap.internal.console.OpenWeatherMapConsoleCommandExtension;
import org.openhab.binding.openweathermap.internal.discovery.OpenWeatherMapPWSDiscoveryService;
import org.openhab.binding.openweathermap.internal.dto.stations.OpenWeatherMapJsonMeasurementsData;
import org.openhab.binding.openweathermap.internal.dto.stations.OpenWeatherMapJsonStationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWeatherMapAPIHandler} is responsible for accessing the OpenWeatherMap API.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@NonNullByDefault
public class OpenWeatherMapAPIHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(OpenWeatherMapAPIHandler.class);

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.singleton(THING_TYPE_WEATHER_API);

    private static final long INITIAL_DELAY_IN_SECONDS = 15;

    private @Nullable ScheduledFuture<?> refreshJob;

    private final HttpClient httpClient;
    private final LocationProvider locationProvider;
    private final LocaleProvider localeProvider;
    private @NonNullByDefault({}) OpenWeatherMapConnection connection;
    private @Nullable OpenWeatherMapPWSDiscoveryService discoveryService;

    // keeps track of the parsed config
    private @NonNullByDefault({}) OpenWeatherMapAPIConfiguration config;

    public OpenWeatherMapAPIHandler(Bridge bridge, HttpClient httpClient, LocationProvider locationProvider,
            LocaleProvider localeProvider) {
        super(bridge);
        this.httpClient = httpClient;
        this.locationProvider = locationProvider;
        this.localeProvider = localeProvider;
    }

    @Override
    public void initialize() {
        logger.debug("Initialize OpenWeatherMap API handler '{}'.", getThing().getUID());
        config = getConfigAs(OpenWeatherMapAPIConfiguration.class);

        boolean configValid = true;
        String apikey = config.apikey;
        if (apikey == null || apikey.trim().isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error-missing-apikey");
            configValid = false;
        }
        int refreshInterval = config.refreshInterval;
        if (refreshInterval < 10) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error-not-supported-refreshInterval");
            configValid = false;
        }
        String language = config.language;
        if (language != null && !(language = language.trim()).isEmpty()) {
            if (!OpenWeatherMapAPIConfiguration.SUPPORTED_LANGUAGES.contains(language.toLowerCase())) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "@text/offline.conf-error-not-supported-language");
                configValid = false;
            }
        } else {
            language = localeProvider.getLocale().getLanguage();
            if (OpenWeatherMapAPIConfiguration.SUPPORTED_LANGUAGES.contains(language)) {
                logger.debug("Language set to '{}'.", language);
                Configuration editConfig = editConfiguration();
                editConfig.put(CONFIG_LANGUAGE, language);
                updateConfiguration(editConfig);
            }
        }

        if (configValid) {
            connection = new OpenWeatherMapConnection(this, httpClient);

            updateStatus(ThingStatus.UNKNOWN);

            ScheduledFuture<?> localRefreshJob = refreshJob;
            if (localRefreshJob == null || localRefreshJob.isCancelled()) {
                logger.debug("Start refresh job at interval {} min.", refreshInterval);
                refreshJob = scheduler.scheduleWithFixedDelay(this::updateThings, INITIAL_DELAY_IN_SECONDS,
                        TimeUnit.MINUTES.toSeconds(refreshInterval), TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void dispose() {
        logger.debug("Dispose OpenWeatherMap API handler '{}'.", getThing().getUID());
        ScheduledFuture<?> localRefreshJob = refreshJob;
        if (localRefreshJob != null && !localRefreshJob.isCancelled()) {
            logger.debug("Stop refresh job.");
            if (localRefreshJob.cancel(true)) {
                refreshJob = null;
            }
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            scheduler.schedule(this::updateThings, INITIAL_DELAY_IN_SECONDS, TimeUnit.SECONDS);
        } else {
            logger.debug("The OpenWeatherMap binding is a read-only binding and cannot handle command '{}'.", command);
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        scheduler.schedule(() -> {
            updateThing((AbstractOpenWeatherMapHandler) childHandler, childThing);
            determineBridgeStatus();
        }, INITIAL_DELAY_IN_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        determineBridgeStatus();
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.unmodifiableList(Arrays.asList(OpenWeatherMapPWSDiscoveryService.class,
                OpenWeatherMapConsoleCommandExtension.class, OpenWeatherMapThingActions.class));
    }

    private void determineBridgeStatus() {
        ThingStatus status = ThingStatus.OFFLINE;
        for (Thing thing : getThing().getThings()) {
            if (ThingStatus.ONLINE.equals(thing.getStatus())) {
                status = ThingStatus.ONLINE;
                break;
            }
        }
        updateStatus(status);
    }

    private void updateThings() {
        ThingStatus status = ThingStatus.OFFLINE;
        for (Thing thing : getThing().getThings()) {
            if (ThingStatus.ONLINE.equals(updateThing((AbstractOpenWeatherMapHandler) thing.getHandler(), thing))) {
                status = ThingStatus.ONLINE;
            }
        }
        updateStatus(status);
    }

    private ThingStatus updateThing(@Nullable AbstractOpenWeatherMapHandler handler, Thing thing) {
        if (handler != null) {
            handler.updateData(connection);
            return thing.getStatus();
        } else {
            logger.debug("Cannot update weather data of thing '{}' as handler is null.", thing.getUID());
            return ThingStatus.OFFLINE;
        }
    }

    public OpenWeatherMapAPIConfiguration getOpenWeatherMapAPIConfig() {
        return config;
    }

    public void setPWSDiscoveryService(OpenWeatherMapPWSDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * Requests all personal weather stations from OpenWeatherMap API.
     *
     * @return all personal weather stations
     */
    public @Nullable List<OpenWeatherMapJsonStationData> getStations() {
        try {
            return connection.getStations();
        } catch (OpenWeatherMapCommunicationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getLocalizedMessage());
        } catch (OpenWeatherMapConfigurationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getLocalizedMessage());
        }
        return null;
    }

    /**
     * Registers a personal weather station via OpenWeatherMap API and triggers discovery.
     *
     * @param name name of the PWS
     * @param location location location of the PWS represented as {@link PointType}
     * @return the weather station
     */
    public @Nullable OpenWeatherMapJsonStationData registerStation(final String name, final @Nullable String externalId,
            final @Nullable PointType location) {
        try {
            OpenWeatherMapJsonStationData station = connection.registerStation(name,
                    externalId == null ? thing.getUID().getAsString() : externalId,
                    location == null ? locationProvider.getLocation() : location);
            if (discoveryService != null && station != null) {
                discoveryService.onStationAdded(station);
            }
            return station;
        } catch (OpenWeatherMapCommunicationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getLocalizedMessage());
            return null;
        }
    }

    /**
     * Deletes a personal weather station via OpenWeatherMap API and cleans-up discovery.
     *
     * @param stationId the id of the PWS
     * @return true, if the PWS is successfully deleted
     */
    public boolean deleteStation(final String stationId) {
        boolean deleted = false;
        try {
            deleted = connection.deleteStation(stationId);
            if (discoveryService != null && deleted) {
                discoveryService.onStationDeleted(stationId);
            }
        } catch (OpenWeatherMapCommunicationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getLocalizedMessage());
        }
        return deleted;
    }

    /**
     * Sends the measurements of all personal weather stations.
     */
    public void sendMeasurements() {
        ThingStatus status = ThingStatus.OFFLINE;
        for (Thing thing : getThing().getThings()) {
            ThingHandler thingHandler = thing.getHandler();
            if (thingHandler instanceof OpenWeatherMapPWSHandler) {
                if (ThingStatus.ONLINE.equals(sendMeasurements((OpenWeatherMapPWSHandler) thingHandler, thing))) {
                    status = ThingStatus.ONLINE;
                }
            }
        }
        updateStatus(status);
    }

    private ThingStatus sendMeasurements(final @Nullable OpenWeatherMapPWSHandler handler, final Thing thing) {
        if (handler != null) {
            handler.sendMeasurements(connection);
            return thing.getStatus();
        } else {
            logger.debug("Cannot send measurements of thing '{}' as handler is null.", thing.getUID());
            return ThingStatus.OFFLINE;
        }
    }

    /**
     * Clears the measurement caches of all personal weather stations.
     */
    public void clearMeasurements() {
        for (Thing thing : getThing().getThings()) {
            ThingHandler thingHandler = thing.getHandler();
            if (thingHandler instanceof OpenWeatherMapPWSHandler) {
                ((OpenWeatherMapPWSHandler) thingHandler).clearMeasurements();
            }
        }
    }

    /**
     * Gets the measurement history of a personal weather station from OpenWeatherMap API.
     *
     * @param stationId the id of the PWS
     * @return the measurement history
     */
    public @Nullable List<OpenWeatherMapJsonMeasurementsData> getMeasurements(final String stationId) {
        try {
            return connection.getMeasurements(stationId);
        } catch (OpenWeatherMapCommunicationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getLocalizedMessage());
        } catch (OpenWeatherMapConfigurationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getLocalizedMessage());
        }
        return null;
    }
}
