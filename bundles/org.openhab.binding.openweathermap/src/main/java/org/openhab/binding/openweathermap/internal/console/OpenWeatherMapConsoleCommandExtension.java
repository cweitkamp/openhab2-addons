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
package org.openhab.binding.openweathermap.internal.console;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.PointType;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerService;
import org.eclipse.smarthome.io.console.Console;
import org.eclipse.smarthome.io.console.extensions.AbstractConsoleCommandExtension;
import org.eclipse.smarthome.io.console.extensions.ConsoleCommandExtension;
import org.openhab.binding.openweathermap.internal.connection.OpenWeatherMapConfigurationException;
import org.openhab.binding.openweathermap.internal.dto.stations.OpenWeatherMapJsonStationData;
import org.openhab.binding.openweathermap.internal.handler.OpenWeatherMapAPIHandler;

/**
 * The {@link OpenWeatherMapConsoleCommandExtension} is responsible for handling console commands
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@NonNullByDefault
public class OpenWeatherMapConsoleCommandExtension extends AbstractConsoleCommandExtension
        implements ConsoleCommandExtension, ThingHandlerService {

    private @NonNullByDefault({}) OpenWeatherMapAPIHandler bridgeHandler;

    public OpenWeatherMapConsoleCommandExtension() {
        super("owm", "Interact with the OpenWeatherMap Binding.");
    }

    @Override
    public void execute(String[] args, Console console) {
        if (args.length != 0) {
            switch (args[0]) {
                case "pws:list":
                    List<OpenWeatherMapJsonStationData> stations = bridgeHandler.getStations();
                    if (stations != null && !stations.isEmpty()) {
                        console.printf("%s",
                                stations.stream().map(s -> String.format("%s [stationId=%s]", s.name, s.id))
                                        .collect(Collectors.joining("\n")));
                    } else {
                        console.println("No personal weather stations found.");
                    }
                    break;
                case "pws:add":
                    if (args.length < 2) {
                        console.println(
                                "Specify a 'name' for your personal weather station: owm pws:add <NAME> [<LOCATION>] [<EXTERNAL_ID>].");
                    } else {
                        String name = args[1];
                        try {
                            PointType location = args.length > 2 ? PointType.valueOf(args[2]) : null;
                            String externalId = args.length > 3 ? args[3] : null;
                            OpenWeatherMapJsonStationData station = bridgeHandler.registerStation(name, externalId,
                                    location);
                            if (station != null) {
                                console.printf("%s [stationId=%s]", station.name, station.id);
                            } else {
                                console.printf("Could not add personal weather station '%s'.\n", name);
                            }
                        } catch (IllegalArgumentException e) {
                            console.printf("Could not add personal weather station '%s': %s.\n", name, e.getMessage());
                        }
                    }
                    break;
                case "pws:delete":
                    if (args.length < 2) {
                        console.println(
                                "Specify a 'stationId' to delete a personal weather station: owm pws:delete <STATION_ID>.");
                    } else {
                        String stationId = args[1];
                        try {
                            bridgeHandler.deleteStation(stationId);
                            console.printf("Personal weather station '%s' successfully deleted.\n", stationId);
                        } catch (OpenWeatherMapConfigurationException e) {
                            console.printf("Could not delete personal weather station '%s': %s.\n", stationId,
                                    e.getMessage());
                        }
                    }
                    break;
                case "pws:send":
                    bridgeHandler.sendMeasurements();
                    console.println("Measurements sent.");
                    break;
                case "pws:clear":
                    bridgeHandler.clearMeasurements();
                    console.println("Measurement caches cleared.");
                    break;
                case "pws:history":
                    if (args.length < 2) {
                        console.println(
                                "Specify a 'stationId' to print the measurement history of the personal weather station: owm pws:history <STATION_ID>.");
                    } else {
                        String stationId = args[1];
                        // TODO
                        bridgeHandler.getMeasurements(stationId);
                    }
                    break;
                default:
                    console.printf("'%s' is not a valid OpenWeatherMap console command.\n\n", args[0]);
                    printUsage(console);
                    break;
            }
        } else {
            printUsage(console);
        }
    }

    @Override
    public List<String> getUsages() {
        //@formatter:off
        return Arrays.asList(
                buildCommandUsage("pws:list", "lists all personal weather stations"),
                buildCommandUsage("pws:add <NAME> [<LOCATION>] [<EXTERNAL_ID>]", "adds a new personal weather station with the given name, location (if given) and external id (if given)"),
                buildCommandUsage("pws:delete <STATION_ID>", "deletes the personal weather station with the given id"),
                buildCommandUsage("pws:send", "sends the measurements of all personal weather stations"),
                buildCommandUsage("pws:clear", "clears the measurement caches of all personal weather stations"),
                buildCommandUsage("pws:history <STATION_ID>", "prints the measurement history of the personal weather stations with the given id")
        );
        //@formatter:off
    }

    @Override
    public void setThingHandler(@NonNullByDefault({}) ThingHandler handler) {
        if (handler instanceof OpenWeatherMapAPIHandler) {
            bridgeHandler = (OpenWeatherMapAPIHandler) handler;
        }
    }

    @Override
    public ThingHandler getThingHandler() {
        return bridgeHandler;
    }
}
