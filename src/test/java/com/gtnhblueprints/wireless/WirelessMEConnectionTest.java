package com.gtnhblueprints.wireless;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridConnection;
import appeng.api.exceptions.SecurityConnectionException;
import appeng.util.ReadOnlyCollection;
import org.junit.jupiter.api.Test;

class WirelessMEConnectionTest {
    static final class Node {
        final List<IGridConnection> edges = new ArrayList<>();
        final IGridNode node = (IGridNode) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IGridNode.class },
            (proxy, method, args) -> method.getName().equals("getConnections") ? new ReadOnlyCollection<>(edges) : null);
    }
    static IGridConnection edge(Node a, Node b) {
        IGridConnection[] result = new IGridConnection[1];
        result[0] = (IGridConnection) Proxy.newProxyInstance(WirelessMEConnectionTest.class.getClassLoader(), new Class<?>[] { IGridConnection.class },
            (proxy, method, args) -> {
                if (method.getName().equals("destroy")) {
                    a.edges.removeIf(e -> e == result[0]); b.edges.removeIf(e -> e == result[0]);
                }
                return null;
            });
        a.edges.add(result[0]); b.edges.add(result[0]); return result[0];
    }
    @Test void overAThousandTerminalsConnectDirectlyWithoutAnEightOrThirtyTwoChannelRelay() {
        Node source = new Node();
        List<WirelessMEConnection> links = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            Node terminal = new Node();
            WirelessMEConnection link = new WirelessMEConnection((local, target) -> {
                assertSame(terminal.node, local); assertSame(source.node, target); return edge(terminal, source);
            });
            assertTrue(link.connect(terminal.node, source.node)); assertEquals(1, terminal.edges.size()); links.add(link);
        }
        assertEquals(1024, source.edges.size());
        links.forEach(WirelessMEConnection::detach); assertTrue(source.edges.isEmpty());
    }
    @Test void repeatedUpdatesAreIdempotentAndLostNodesReconnectOnce() {
        Node terminal = new Node(), source = new Node(); int[] calls = { 0 };
        WirelessMEConnection link = new WirelessMEConnection((local, target) -> { calls[0]++; return edge(terminal, source); });
        for (int i = 0; i < 10; i++) assertTrue(link.connect(terminal.node, source.node));
        assertEquals(1, calls[0]); terminal.edges.get(0).destroy();
        assertTrue(link.connect(terminal.node, source.node)); assertEquals(2, calls[0]);
        assertFalse(link.connect(terminal.node, null)); assertTrue(terminal.edges.isEmpty()); assertTrue(source.edges.isEmpty());
        link.detach(); assertTrue(link.connect(terminal.node, source.node)); assertEquals(3, calls[0]);
    }
    @Test void switchingSourceRemovesOldEdgeBeforeJoiningAndSecurityDenialDoesNotKeepIt() {
        Node terminal = new Node(), first = new Node(), second = new Node();
        WirelessMEConnection link = new WirelessMEConnection((local, target) -> {
            if (target == first.node) return edge(terminal, first);
            assertTrue(first.edges.isEmpty()); throw new SecurityConnectionException();
        });
        assertTrue(link.connect(terminal.node, first.node));
        assertFalse(link.connect(terminal.node, second.node));
        assertTrue(terminal.edges.isEmpty()); assertTrue(first.edges.isEmpty());
    }
}
