package com.github.satori87.gdx.webrtc.server.turn;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StunConstantsTest {

    @Test
    void magicCookie() {
        assertEquals(0x2112A442, StunConstants.MAGIC_COOKIE);
    }

    @Test
    void headerSize() {
        assertEquals(20, StunConstants.HEADER_SIZE);
    }

    @Test
    void getMethodFromBindingRequest() {
        assertEquals(StunConstants.METHOD_BINDING, StunConstants.getMethod(StunConstants.BINDING_REQUEST));
    }

    @Test
    void getMethodFromAllocateRequest() {
        assertEquals(StunConstants.METHOD_ALLOCATE, StunConstants.getMethod(StunConstants.ALLOCATE_REQUEST));
    }

    @Test
    void getMethodFromAllocateSuccess() {
        assertEquals(StunConstants.METHOD_ALLOCATE, StunConstants.getMethod(StunConstants.ALLOCATE_SUCCESS));
    }

    @Test
    void getMethodFromAllocateError() {
        assertEquals(StunConstants.METHOD_ALLOCATE, StunConstants.getMethod(StunConstants.ALLOCATE_ERROR));
    }

    @Test
    void getMethodFromRefresh() {
        assertEquals(StunConstants.METHOD_REFRESH, StunConstants.getMethod(StunConstants.REFRESH_REQUEST));
    }

    @Test
    void getMethodFromSendIndication() {
        assertEquals(StunConstants.METHOD_SEND, StunConstants.getMethod(StunConstants.SEND_INDICATION));
    }

    @Test
    void getMethodFromDataIndication() {
        assertEquals(StunConstants.METHOD_DATA, StunConstants.getMethod(StunConstants.DATA_INDICATION));
    }

    @Test
    void getMethodFromCreatePermission() {
        assertEquals(StunConstants.METHOD_CREATE_PERMISSION, StunConstants.getMethod(StunConstants.CREATE_PERMISSION_REQUEST));
    }

    @Test
    void getMethodFromChannelBind() {
        assertEquals(StunConstants.METHOD_CHANNEL_BIND, StunConstants.getMethod(StunConstants.CHANNEL_BIND_REQUEST));
    }

    @Test
    void getClassRequest() {
        assertEquals(0, StunConstants.getClass(StunConstants.BINDING_REQUEST));
    }

    @Test
    void getClassIndication() {
        assertEquals(1, StunConstants.getClass(StunConstants.SEND_INDICATION));
    }

    @Test
    void getClassSuccess() {
        assertEquals(2, StunConstants.getClass(StunConstants.BINDING_SUCCESS));
    }

    @Test
    void getClassError() {
        assertEquals(3, StunConstants.getClass(StunConstants.BINDING_ERROR));
    }

    @Test
    void buildMessageTypeBindingRequest() {
        int type = StunConstants.buildMessageType(StunConstants.METHOD_BINDING, 0);
        assertEquals(StunConstants.BINDING_REQUEST, type);
    }

    @Test
    void buildMessageTypeBindingSuccess() {
        int type = StunConstants.buildMessageType(StunConstants.METHOD_BINDING, 2);
        assertEquals(StunConstants.BINDING_SUCCESS, type);
    }

    @Test
    void buildMessageTypeBindingError() {
        int type = StunConstants.buildMessageType(StunConstants.METHOD_BINDING, 3);
        assertEquals(StunConstants.BINDING_ERROR, type);
    }

    @Test
    void buildMessageTypeAllocateRequest() {
        int type = StunConstants.buildMessageType(StunConstants.METHOD_ALLOCATE, 0);
        assertEquals(StunConstants.ALLOCATE_REQUEST, type);
    }

    @Test
    void buildMessageTypeAllocateSuccess() {
        int type = StunConstants.buildMessageType(StunConstants.METHOD_ALLOCATE, 2);
        assertEquals(StunConstants.ALLOCATE_SUCCESS, type);
    }

    @Test
    void buildMessageTypeRoundTrip() {
        for (int method : new int[]{
                StunConstants.METHOD_BINDING,
                StunConstants.METHOD_ALLOCATE,
                StunConstants.METHOD_REFRESH,
                StunConstants.METHOD_SEND,
                StunConstants.METHOD_DATA,
                StunConstants.METHOD_CREATE_PERMISSION,
                StunConstants.METHOD_CHANNEL_BIND}) {
            for (int clazz = 0; clazz < 4; clazz++) {
                int type = StunConstants.buildMessageType(method, clazz);
                assertEquals(method, StunConstants.getMethod(type),
                        "method roundtrip failed for method=0x" + Integer.toHexString(method) + " class=" + clazz);
                assertEquals(clazz, StunConstants.getClass(type),
                        "class roundtrip failed for method=0x" + Integer.toHexString(method) + " class=" + clazz);
            }
        }
    }

    @Test
    void channelNumberRange() {
        assertEquals(0x4000, StunConstants.CHANNEL_MIN);
        assertEquals(0x7FFF, StunConstants.CHANNEL_MAX);
        assertTrue(StunConstants.CHANNEL_MIN < StunConstants.CHANNEL_MAX);
    }

    @Test
    void lifetimeDefaults() {
        assertEquals(600, StunConstants.DEFAULT_LIFETIME);
        assertEquals(3600, StunConstants.MAX_LIFETIME);
        assertTrue(StunConstants.DEFAULT_LIFETIME < StunConstants.MAX_LIFETIME);
    }

    @Test
    void transportUdp() {
        assertEquals(17, StunConstants.TRANSPORT_UDP);
    }

    @Test
    void errorCodeValues() {
        assertEquals(400, StunConstants.ERR_BAD_REQUEST);
        assertEquals(401, StunConstants.ERR_UNAUTHORIZED);
        assertEquals(403, StunConstants.ERR_FORBIDDEN);
        assertEquals(420, StunConstants.ERR_UNKNOWN_ATTRIBUTE);
        assertEquals(437, StunConstants.ERR_ALLOCATION_MISMATCH);
        assertEquals(438, StunConstants.ERR_STALE_NONCE);
        assertEquals(508, StunConstants.ERR_INSUFFICIENT_CAPACITY);
    }

    @Test
    void attributeTypeValues() {
        assertEquals(0x0001, StunConstants.ATTR_MAPPED_ADDRESS);
        assertEquals(0x0006, StunConstants.ATTR_USERNAME);
        assertEquals(0x0008, StunConstants.ATTR_MESSAGE_INTEGRITY);
        assertEquals(0x0009, StunConstants.ATTR_ERROR_CODE);
        assertEquals(0x0014, StunConstants.ATTR_REALM);
        assertEquals(0x0015, StunConstants.ATTR_NONCE);
        assertEquals(0x0020, StunConstants.ATTR_XOR_MAPPED_ADDRESS);
        assertEquals(0x000C, StunConstants.ATTR_CHANNEL_NUMBER);
        assertEquals(0x000D, StunConstants.ATTR_LIFETIME);
        assertEquals(0x0012, StunConstants.ATTR_XOR_PEER_ADDRESS);
        assertEquals(0x0013, StunConstants.ATTR_DATA);
        assertEquals(0x0016, StunConstants.ATTR_XOR_RELAYED_ADDRESS);
        assertEquals(0x0019, StunConstants.ATTR_REQUESTED_TRANSPORT);
    }
}
