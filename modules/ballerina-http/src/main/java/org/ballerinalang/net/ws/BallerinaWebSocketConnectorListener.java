/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.net.ws;

import org.ballerinalang.connector.api.BallerinaConnectorException;
import org.ballerinalang.connector.api.ConnectorFuture;
import org.ballerinalang.connector.api.ConnectorFutureListener;
import org.ballerinalang.connector.api.Executor;
import org.ballerinalang.connector.api.Resource;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.services.ErrorHandlerUtils;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketBinaryMessage;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketCloseMessage;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketConnectorListener;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketControlMessage;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketInitMessage;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketMessage;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketTextMessage;

import java.net.ProtocolException;
import javax.websocket.Session;

/**
 * Ballerina Connector listener for WebSocket.
 *
 * @since 0.93
 */
public class BallerinaWebSocketConnectorListener implements WebSocketConnectorListener {

    @Override
    public void onMessage(WebSocketInitMessage webSocketInitMessage) {
        WebSocketService wsService = WebSocketDispatcher.findService(webSocketInitMessage);
        Resource onHandshakeResource = wsService.getResourceByName(Constants.RESOURCE_NAME_ON_HANDSHAKE);
        if (onHandshakeResource != null) {
            BValue[] bValues = new BValue[0];
            ConnectorFuture future = Executor.submit(onHandshakeResource, null, bValues);
            future.setConnectorFutureListener(new ConnectorFutureListener() {
                @Override
                public void notifySuccess() {
                    //TODO need to find a way to execute this after resource invocation.
                    handleHandshake(webSocketInitMessage, wsService);
                }

                @Override
                public void notifyReply(BValue response) {
                    //Nothing to do
                }

                @Override
                public void notifyFailure(BallerinaConnectorException ex) {
                }
            });

        } else {
            handleHandshake(webSocketInitMessage, wsService);
        }
    }

    @Override
    public void onMessage(WebSocketTextMessage webSocketTextMessage) {
        WebSocketService wsService = WebSocketDispatcher.findService(webSocketTextMessage);
        Resource onTextMessageResource =
                WebSocketDispatcher.getResource(wsService, Constants.RESOURCE_NAME_ON_TEXT_MESSAGE);
        if (onTextMessageResource == null) {
            return;
        }
        BStruct wsConnection = getWSConnection(webSocketTextMessage);

        BStruct wsTextFrame = wsService.createTextFrameStruct();
        wsTextFrame.setStringField(0, webSocketTextMessage.getText());
        if (webSocketTextMessage.isFinalFragment()) {
            wsTextFrame.setBooleanField(0, 1);
        } else {
            wsTextFrame.setBooleanField(0, 0);
        }
        BValue[] bValues = {wsConnection, wsTextFrame};
        ConnectorFuture future = Executor.submit(onTextMessageResource, null, bValues);
        future.setConnectorFutureListener(new WebSocketEmptyConnFutureListener());
    }

    @Override
    public void onMessage(WebSocketBinaryMessage webSocketBinaryMessage) {
        WebSocketService wsService = WebSocketDispatcher.findService(webSocketBinaryMessage);
        Resource onBinaryMessageResource =
                WebSocketDispatcher.getResource(wsService, Constants.RESOURCE_NAME_ON_BINARY_MESSAGE);
        if (onBinaryMessageResource == null) {
            return;
        }
        BStruct wsConnection = getWSConnection(webSocketBinaryMessage);
        BStruct wsBinaryFrame = wsService.createBinaryFrameStruct();
        wsBinaryFrame.setBlobField(0, webSocketBinaryMessage.getByteArray());
        if (webSocketBinaryMessage.isFinalFragment()) {
            wsBinaryFrame.setBooleanField(0, 1);
        } else {
            wsBinaryFrame.setBooleanField(0, 0);
        }
        BValue[] bValues = {wsConnection, wsBinaryFrame};
        ConnectorFuture future = Executor.submit(onBinaryMessageResource, null, bValues);
        future.setConnectorFutureListener(new WebSocketEmptyConnFutureListener());
    }

    @Override
    public void onMessage(WebSocketControlMessage webSocketControlMessage) {
        throw new BallerinaConnectorException("Pong messages are not supported!");
    }

    @Override
    public void onMessage(WebSocketCloseMessage webSocketCloseMessage) {
        WebSocketService wsService = WebSocketDispatcher.findService(webSocketCloseMessage);
        Resource onCloseResource = WebSocketDispatcher.getResource(wsService, Constants.RESOURCE_NAME_ON_CLOSE);
        if (onCloseResource == null) {
            return;
        }
        BStruct wsConnection = getWSConnection(webSocketCloseMessage);
        BStruct wsCloseFrame = wsService.createCloseFrameStruct();
        wsCloseFrame.setIntField(0, webSocketCloseMessage.getCloseCode());
        wsCloseFrame.setStringField(0, webSocketCloseMessage.getCloseReason());

        BValue[] bValues = {wsConnection, wsCloseFrame};
        ConnectorFuture future = Executor.submit(onCloseResource, null, bValues);
        future.setConnectorFutureListener(new WebSocketEmptyConnFutureListener());
    }

    @Override
    public void onError(Throwable throwable) {
        throw new BallerinaConnectorException("Unexpected error occurred in WebSocket transport", throwable);
    }

    @Override
    public void onIdleTimeout(WebSocketControlMessage controlMessage) {
        WebSocketService wsService = WebSocketDispatcher.findService(controlMessage);
        Resource onIdleTimeoutResource =
                WebSocketDispatcher.getResource(wsService, Constants.RESOURCE_NAME_ON_IDLE_TIMEOUT);
        if (onIdleTimeoutResource == null) {
            return;
        }
        BStruct wsConnection = getWSConnection(controlMessage);
        BValue[] bValues = {wsConnection};
        ConnectorFuture future = Executor.submit(onIdleTimeoutResource, null, bValues);
        future.setConnectorFutureListener(new WebSocketEmptyConnFutureListener());
    }


    private void handleHandshake(WebSocketInitMessage initMessage, WebSocketService wsService) {
        try {
            String[] subProtocols = wsService.getNegotiableSubProtocols();
            int idleTImeoutInSeconds = wsService.getIdleTimeoutInSeconds();
            Session session = initMessage.handshake(subProtocols, true, idleTImeoutInSeconds * 1000);
            BStruct wsConnection = wsService.createConnectionStruct();
            wsConnection.addNativeData(Constants.NATIVE_DATA_WEBSOCKET_SESSION, session);
            wsConnection.addNativeData(Constants.WEBSOCKET_MESSAGE, initMessage);

            WebSocketConnectionManager.getInstance().addConnection(session.getId(), wsConnection);

            Resource onOpenResource = wsService.getResourceByName(Constants.RESOURCE_NAME_ON_OPEN);
            BValue[] bValues = {wsConnection};
            if (onOpenResource == null) {
                return;
            }
            ConnectorFuture future = Executor.submit(onOpenResource, null, bValues);
            future.setConnectorFutureListener(new WebSocketEmptyConnFutureListener());
        } catch (ProtocolException e) {
            ErrorHandlerUtils.printError(e);
        }
    }

    private BStruct getWSConnection(WebSocketMessage webSocketMessage) {
        return WebSocketConnectionManager.getInstance().getConnection(webSocketMessage.getChannelSession().getId());
    }

}
