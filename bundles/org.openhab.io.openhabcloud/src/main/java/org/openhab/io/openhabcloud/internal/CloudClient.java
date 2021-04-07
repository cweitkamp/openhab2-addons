/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.io.openhabcloud.internal;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Request.FailureListener;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Response.ContentListener;
import org.eclipse.jetty.client.api.Response.HeadersListener;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.core.OpenHAB;
import org.openhab.core.common.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This class provides communication between openHAB and the openHAB Cloud service.
 * It also implements async http proxy for serving requests from user to openHAB through the openHAB Cloud.
 * It uses Jetty {@link WebSocketClient} connection to connect to the openHAB Cloud service and Jetty {@link HttpClient}
 * to send local http requests to openHAB.
 *
 * @author Victor Belov - Initial contribution
 * @author Kai Kreuzer - migrated code to new Jetty HttpClient and ESH APIs
 * @author Christoph Weitkamp - migrated to Jetty WebSocketClient
 */
@WebSocket
@NonNullByDefault
public class CloudClient {
    /*
     * Logger for this class
     */
    private final Logger logger = LoggerFactory.getLogger(CloudClient.class);

    /*
     * This variable holds base URL for the openHAB Cloud connections
     */
    private final String baseUrl;

    /*
     * This variable holds openHAB's UUID for authenticating and connecting to the openHAB Cloud
     */
    private final String uuid;

    /*
     * This variable holds openHAB's secret for authenticating and connecting to the openHAB Cloud
     */
    private final String secret;

    /*
     * This variable holds local openHAB's base URL for connecting to the local openHAB instance
     */
    private final String localBaseUrl;

    /*
     * This variable holds instance of Jetty WebSocket client class which provides communication with the openHAB Cloud
     */
    private final WebSocketClient webSocketClient;
    private @Nullable Session session;

    private final Gson mapper = new Gson();

    /*
     * This variable holds instance of Jetty HTTP client to make requests to local openHAB
     */
    private final HttpClient httpClient;

    /*
     * This map holds HTTP requests to local openHAB which are currently running
     */
    private final Map<Integer, Request> runningRequests = new ConcurrentHashMap<>();

    /*
     * This variable indicates if connection to the openHAB Cloud is currently in an established state
     */
    private boolean isConnected;

    /*
     * The protocol of the openHAB-cloud URL.
     */
    private String protocol = "https";

    /*
     * This variable holds instance of CloudClientListener which provides callbacks to communicate
     * certain events from the openHAB Cloud back to openHAB
     */
    private @Nullable CloudClientListener listener;
    private boolean remoteAccessEnabled;
    private final Set<String> exposedItems;

    /**
     * Constructor of CloudClient
     *
     * @param uuid openHAB's UUID to connect to the openHAB Cloud
     * @param secret openHAB's Secret to connect to the openHAB Cloud
     * @param remoteAccessEnabled Allow the openHAB Cloud to be used as a remote proxy
     * @param exposedItems Items that are made available to apps connected to the openHAB Cloud
     */
    public CloudClient(WebSocketClient webSocketClient, HttpClient httpClient, String uuid, String secret,
            String baseUrl, String localBaseUrl, boolean remoteAccessEnabled, Set<String> exposedItems) {
        this.uuid = uuid;
        this.secret = secret;
        this.baseUrl = baseUrl;
        this.localBaseUrl = localBaseUrl;
        this.remoteAccessEnabled = remoteAccessEnabled;
        this.exposedItems = exposedItems;
        this.webSocketClient = webSocketClient;
        this.httpClient = httpClient;
    }

    /**
     * Connect to the openHAB Cloud
     */
    public void connect() {
        URI parsed = URI.create(baseUrl);
        protocol = parsed.getScheme();
        try {
            webSocketClient.start();
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("uuid", uuid);
            request.setHeader("secret", secret);
            String openHABVersion = OpenHAB.getVersion();
            request.setHeader("openhabversion", openHABVersion);
            String clientVersion = CloudService.clientVersion;
            request.setHeader("clientversion", clientVersion == null ? openHABVersion : clientVersion);
            request.setHeader("remoteaccess", Boolean.toString(remoteAccessEnabled));
            webSocketClient.connect(this, parsed, request);
        } catch (Exception e) {
            logger.error("Error while connecting socket: {}", e.getMessage());
        }
    }

    /**
     * Callback method for websocket client which is called when connection is established
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        logger.info("Connected to the openHAB Cloud service (UUID = {}, base URL = {})", uuid, baseUrl);
        this.session = session;
        isConnected = true;
    }

    /**
     * Callback method for websocket client which is called when disconnect occurs
     */
    @OnWebSocketClose
    public void onDisconnect(Session session, int statusCode, String reason) {
        if (!session.equals(this.session)) {
            handleWrongSession(session, "Connection closed: " + statusCode + " / " + reason);
            return;
        }
        logger.info("Disconnected from the openHAB Cloud service (UUID = {}, base URL = {}): {}", uuid, baseUrl,
                reason);
        isConnected = false;
        this.session = null;
        // clean up the list of running requests
        runningRequests.clear();
    }

    private void handleWrongSession(Session session, String message) {
        logger.warn("Received and discarded message for other session {}: {}.", session.hashCode(), message);
        if (session.isOpen()) {
            // Close the session if it is still open. It should already be closed anyway
            session.close();
        }
    }

    /**
     * Callback method for websocket client which is called when an error occurs
     */
    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        Session storedSession = this.session;
        if (session != null && !session.equals(storedSession)) {
            handleWrongSession(session, "Connection error: " + error.getMessage());
            return;
        }
        logger.warn("Connection to the openHAB Cloud service errored (UUID = {}, base URL = {}): {}", uuid, baseUrl,
                error.getMessage());

        if (storedSession != null && storedSession.isOpen()) {
            storedSession.close(-1, "Processing error");
        }
    }

    /**
     * Callback method for websocket client which is called when a message is received
     */
    @OnWebSocketMessage
    public void onMessage(Session session, String text) {
        if (!session.equals(this.session)) {
            handleWrongSession(session, "On Message: " + text);
            return;
        }
        logger.trace("Message from openHAB Cloud service (UUID = {}, base URL = {}): {}", uuid, baseUrl, text);

        // TODO
        // if ("command".equals(event)) {
        // handleCommandEvent(data);
        // return;
        // }
        // if (remoteAccessEnabled) {
        // if ("request".equals(event)) {
        // handleRequestEvent(data);
        // } else if ("cancel".equals(event)) {
        // handleCancelEvent(data);
        // } else {
        // logger.warn("Unsupported event from openHAB Cloud: {}", event);
        // }
        // }
    }

    private void handleRequestEvent(JsonObject data) {
        try {
            // Get unique request Id
            int requestId = data.get("id").getAsInt();
            logger.debug("Got request {}", requestId);
            // Get request path
            String requestPath = data.get("path").getAsString();
            // Get request method
            String requestMethod = data.get("method").getAsString();
            // Get request body
            String requestBody = data.get("body").getAsString();
            // Get JsonObject for request headers
            JsonObject requestHeadersJson = data.get("headers").getAsJsonObject();
            logger.debug("Request headers: {}", requestHeadersJson.toString());
            // Get JsonObject for request query parameters
            JsonObject requestQueryJson = data.get("query").getAsJsonObject();
            logger.debug("Request query: {}", requestQueryJson.toString());
            // Create URI builder with base request URI of openHAB and path from request
            String newPath = URIUtil.addPaths(localBaseUrl, requestPath);
            Iterator<String> queryIterator = requestQueryJson.keySet().iterator();
            // Add query parameters to URI builder, if any
            newPath += "?";
            while (queryIterator.hasNext()) {
                String queryName = queryIterator.next();
                newPath += queryName;
                newPath += "=";
                newPath += URLEncoder.encode(requestQueryJson.get(queryName).getAsString(), StandardCharsets.UTF_8);
                if (queryIterator.hasNext()) {
                    newPath += "&";
                }
            }
            // Finally get the future request URI
            URI requestUri = new URI(newPath);
            // All preparations which are common for different methods are done
            // Now perform the request to openHAB
            // If method is GET
            logger.debug("Request method is {}", requestMethod);
            Request request = httpClient.newRequest(requestUri);
            setRequestHeaders(request, requestHeadersJson);
            JsonElement proto = data.get("protocol");
            request.header("X-Forwarded-Proto", proto == null ? protocol : proto.getAsString());

            switch (requestMethod) {
                case "GET":
                    request.method(HttpMethod.GET);
                    break;
                case "POST":
                    request.method(HttpMethod.POST);
                    request.content(new BytesContentProvider(requestBody.getBytes()));
                    break;
                case "PUT":
                    request.method(HttpMethod.PUT);
                    request.content(new BytesContentProvider(requestBody.getBytes()));
                    break;
                default:
                    // TODO: Reject unsupported methods
                    logger.warn("Unsupported request method {}", requestMethod);
                    return;
            }
            ResponseListener listener = new ResponseListener(requestId);
            request.onResponseHeaders(listener).onResponseContent(listener).onRequestFailure(listener).send(listener);
            // If successfully submitted request to http client, add it to the list of currently
            // running requests to be able to cancel it if needed
            runningRequests.put(requestId, request);
        } catch (URISyntaxException e) {
            logger.debug("{}", e.getMessage());
        }
    }

    private void setRequestHeaders(Request request, JsonObject requestHeadersJson) {
        Iterator<String> headersIterator = requestHeadersJson.keySet().iterator();
        // Convert JSONObject of headers into Header ArrayList
        while (headersIterator.hasNext()) {
            String headerName = headersIterator.next();
            String headerValue = requestHeadersJson.get(headerName).getAsString();
            logger.debug("Jetty set header {} = {}", headerName, headerValue);
            if (!headerName.equalsIgnoreCase("Content-Length")) {
                request.header(headerName, headerValue);
            }
        }
    }

    private void handleCancelEvent(JsonObject data) {
        int requestId = data.get("id").getAsInt();
        logger.debug("Received cancel for request {}", requestId);
        // Find and abort running request
        Request request = runningRequests.get(requestId);
        if (request != null) {
            request.abort(new InterruptedException());
            runningRequests.remove(requestId);
        }
    }

    private void handleCommandEvent(JsonObject data) {
        String itemName = data.get("item").getAsString();
        if (exposedItems.contains(itemName)) {
            String command = data.get("command").getAsString();
            logger.debug("Received command {} for item {}.", command, itemName);
            if (listener != null) {
                listener.sendCommand(itemName, command);
            }
        } else {
            logger.warn("Received command from openHAB Cloud for item '{}', which is not exposed.", itemName);
        }
    }

    /**
     * This method sends notification to the openHAB Cloud
     *
     * @param userId openHAB Cloud user id
     * @param message notification message text
     * @param icon name of the icon for this notification
     * @param severity severity name for this notification
     */
    public void sendNotification(String userId, String message, @Nullable String icon, @Nullable String severity) {
        if (isConnected()) {
            JsonObject notificationMessage = new JsonObject();
            notificationMessage.addProperty("userId", userId);
            notificationMessage.addProperty("message", message);
            notificationMessage.addProperty("icon", icon);
            notificationMessage.addProperty("severity", severity);
            emit("notification", notificationMessage);
        } else {
            logger.debug("No connection, notification is not sent");
        }
    }

    /**
     * This method sends log notification to the openHAB Cloud
     *
     * @param message notification message text
     * @param icon name of the icon for this notification
     * @param severity severity name for this notification
     */
    public void sendLogNotification(String message, @Nullable String icon, @Nullable String severity) {
        if (isConnected()) {
            JsonObject notificationMessage = new JsonObject();
            notificationMessage.addProperty("message", message);
            notificationMessage.addProperty("icon", icon);
            notificationMessage.addProperty("severity", severity);
            emit("lognotification", notificationMessage);
        } else {
            logger.debug("No connection, notification is not sent");
        }
    }

    /**
     * This method sends broadcast notification to the openHAB Cloud
     *
     * @param message notification message text
     * @param icon name of the icon for this notification
     * @param severity severity name for this notification
     */
    public void sendBroadcastNotification(String message, @Nullable String icon, @Nullable String severity) {
        if (isConnected()) {
            JsonObject notificationMessage = new JsonObject();
            notificationMessage.addProperty("message", message);
            notificationMessage.addProperty("icon", icon);
            notificationMessage.addProperty("severity", severity);
            emit("broadcastnotification", notificationMessage);
        } else {
            logger.debug("No connection, notification is not sent");
        }
    }

    /**
     * Send item update to openHAB Cloud
     *
     * @param itemName the name of the item
     * @param itemState updated item state
     *
     */
    public void sendItemUpdate(String itemName, String itemState) {
        if (isConnected()) {
            logger.debug("Sending update '{}' for item '{}'", itemState, itemName);
            JsonObject itemUpdateMessage = new JsonObject();
            itemUpdateMessage.addProperty("itemName", itemName);
            itemUpdateMessage.addProperty("itemStatus", itemState);
            emit("itemupdate", itemUpdateMessage);
        } else {
            logger.debug("No connection, Item update is not sent");
        }
    }

    /**
     * Returns true if openHAB Cloud connection is active
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Disconnect from openHAB Cloud
     */
    public void shutdown() {
        logger.info("Shutting down openHAB Cloud service connection");
        try {
            webSocketClient.stop();
        } catch (Exception e) {
            logger.debug("Error while closing socket connection: ", e);
        }
    }

    public void setListener(CloudClientListener listener) {
        this.listener = listener;
    }

    /*
     * An internal class which forwards response headers and data back to the openHAB Cloud
     */
    private class ResponseListener
            implements Response.CompleteListener, HeadersListener, ContentListener, FailureListener {

        private static final String THREADPOOL_OPENHABCLOUD = "openhabcloud";
        private final int mRequestId;
        private boolean mHeadersSent = false;

        public ResponseListener(int requestId) {
            mRequestId = requestId;
        }

        private JsonObject getJSONHeaders(HttpFields httpFields) {
            JsonObject headersJSON = new JsonObject();
            for (HttpField field : httpFields) {
                headersJSON.addProperty(field.getName(), field.getValue());
            }
            return headersJSON;
        }

        @Override
        public void onComplete(@Nullable Result result) {
            // Remove this request from list of running requests
            runningRequests.remove(mRequestId);
            if ((result != null && result.isFailed())
                    && (result.getResponse() != null && result.getResponse().getStatus() != HttpStatus.OK_200)) {
                if (result.getFailure() != null) {
                    logger.warn("Jetty request {} failed: {}", mRequestId, result.getFailure().getMessage());
                }
                if (result.getRequestFailure() != null) {
                    logger.warn("Request Failure: {}", result.getRequestFailure().getMessage());
                }
                if (result.getResponseFailure() != null) {
                    logger.warn("Response Failure: {}", result.getResponseFailure().getMessage());
                }
            }

            /**
             * What is this? In some cases where latency is very low the myopenhab service
             * can receive responseFinished before the headers or content are received and I
             * cannot find another workaround to prevent it.
             */
            ThreadPoolManager.getScheduledPool(THREADPOOL_OPENHABCLOUD).schedule(() -> {
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("id", mRequestId);
                emit("responseFinished", responseJson);
                logger.debug("Finished responding to request {}", mRequestId);
            }, 1, TimeUnit.MILLISECONDS);
        }

        @Override
        public synchronized void onFailure(@Nullable Request request, @Nullable Throwable failure) {
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("id", mRequestId);
            responseJson.addProperty("responseStatusText", "openHAB connection error: " + failure.getMessage());
            emit("responseError", responseJson);
        }

        @Override
        public void onContent(@Nullable Response response, @Nullable ByteBuffer content) {
            logger.debug("Jetty received response content of size {}", String.valueOf(content.remaining()));
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("id", mRequestId);
            responseJson.addProperty("body", BufferUtil.toString(content));
            emit("responseContentBinary", responseJson);
            logger.debug("Sent content to request {}", mRequestId);
        }

        @Override
        public void onHeaders(@Nullable Response response) {
            if (!mHeadersSent) {
                logger.debug("Jetty finished receiving response header");
                JsonObject responseJson = new JsonObject();
                mHeadersSent = true;
                responseJson.addProperty("id", mRequestId);
                responseJson.add("headers", getJSONHeaders(response.getHeaders()));
                responseJson.addProperty("responseStatusCode", response.getStatus());
                responseJson.addProperty("responseStatusText", "OK");
                emit("responseHeader", responseJson);
                logger.debug("Sent headers to request {}", mRequestId);
                logger.debug("{}", responseJson.toString());
            }
        }
    }

    private boolean emit(String event, JsonObject args) {
        try {
            String message = mapper.toJson(args);
            Session storedSession = session;
            if (storedSession != null) {
                storedSession.getRemote().sendString(message);
            }
            return true;
        } catch (IOException e) {
            logger.debug("Error while sending message: {}", e.getMessage());
        }
        return false;
    }
}
