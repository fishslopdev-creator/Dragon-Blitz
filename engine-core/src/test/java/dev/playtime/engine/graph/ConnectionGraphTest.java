package dev.playtime.engine.graph;

import dev.playtime.engine.logic.Facts;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionGraphTest {
    private ConnectionGraph graph;
    private SignalBus bus;
    private final List<String> delivered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        graph = new ConnectionGraph(new HashMap<>());
        bus = new SignalBus(graph, new SignalHandler() {
            @Override public void deliver(Connection c, Device target, Signal signal, UUID player) { delivered.add(c.type + ":" + signal); }
            @Override public void deviceChanged(Device device, boolean active, UUID player) { delivered.add(device.id.path() + "=" + active); }
            @Override public void devicePulsed(Device device, UUID player) { delivered.add(device.id.path() + "!"); }
            @Override public Facts facts(UUID player) { return null; }
        });
        for (int i = 0; i < 5; i++) graph.addDevice(new Device(Id.of("d" + i), DeviceType.POWER_SWITCH, BlockRef.of(i, 0, 0)));
    }

    private Connection link(String from, String to, ConnectionType type, int delay) {
        Connection c = new Connection();
        c.from = Id.of(from);
        c.to = Id.of(to);
        c.type = type;
        c.delayTicks = delay;
        return c;
    }

    @Test
    void rejectsSelfLinksMissingDevicesAndInstantLoops() {
        assertTrue(graph.connect(link("d0", "d0", ConnectionType.ACTIVATE, 0)).hasErrors());
        assertTrue(graph.connect(link("d0", "nope", ConnectionType.ACTIVATE, 0)).hasErrors());
        assertFalse(graph.connect(link("d0", "d1", ConnectionType.ACTIVATE, 0)).hasErrors());
        assertFalse(graph.connect(link("d1", "d2", ConnectionType.ACTIVATE, 0)).hasErrors());
        var loop = graph.connect(link("d2", "d0", ConnectionType.ACTIVATE, 0));
        assertTrue(loop.hasErrors());
        assertTrue(loop.toString().contains("loop"));
        assertFalse(graph.connect(link("d2", "d0", ConnectionType.ACTIVATE, 20)).hasErrors(), "delayed loops are allowed");
        assertTrue(graph.connect(link("d0", "d1", ConnectionType.ACTIVATE, 0)).hasErrors(), "duplicate");
    }

    @Test
    void signalsPropagateAndDelaysWait() {
        graph.connect(link("d0", "d1", ConnectionType.ACTIVATE, 0));
        graph.connect(link("d1", "d2", ConnectionType.ACTIVATE, 10));
        bus.setActive(Id.of("d0"), true, null);
        assertTrue(graph.state(Id.of("d1")).active);
        assertFalse(graph.state(Id.of("d2")).active);
        bus.tick(5);
        assertFalse(graph.state(Id.of("d2")).active);
        bus.tick(10);
        assertTrue(graph.state(Id.of("d2")).active);
    }

    @Test
    void severalInputsAreAnAndGate() {
        graph.device(Id.of("d3")).orElseThrow().inputsRequired = 2;
        graph.connect(link("d0", "d3", ConnectionType.ACTIVATE, 0));
        graph.connect(link("d1", "d3", ConnectionType.ACTIVATE, 0));
        bus.setActive(Id.of("d0"), true, null);
        assertFalse(graph.state(Id.of("d3")).active);
        bus.setActive(Id.of("d1"), true, null);
        assertTrue(graph.state(Id.of("d3")).active);
        bus.setActive(Id.of("d0"), false, null);
        assertFalse(graph.state(Id.of("d3")).active);
    }

    @Test
    void latchKeepsTargetOn() {
        graph.device(Id.of("d1")).orElseThrow().latch = true;
        graph.connect(link("d0", "d1", ConnectionType.ACTIVATE, 0));
        bus.setActive(Id.of("d0"), true, null);
        bus.setActive(Id.of("d0"), false, null);
        assertTrue(graph.state(Id.of("d1")).active);
    }

    @Test
    void toggleInvertDisableAndExternalLinks() {
        graph.connect(link("d0", "d1", ConnectionType.TOGGLE, 0));
        bus.pulse(Id.of("d0"), null);
        assertTrue(graph.state(Id.of("d1")).active);
        bus.pulse(Id.of("d0"), null);
        assertFalse(graph.state(Id.of("d1")).active);

        Connection open = link("d2", "d3", ConnectionType.OPEN_DOOR, 0);
        open.targetRef = Id.of("door");
        graph.connect(open);
        bus.setActive(Id.of("d2"), true, null);
        assertTrue(delivered.contains("OPEN_DOOR:ON"));

        graph.connect(link("d4", "d2", ConnectionType.DISABLE, 0));
        bus.setActive(Id.of("d4"), true, null);
        assertTrue(graph.state(Id.of("d2")).disabled);
    }

    @Test
    void removingADeviceRemovesItsLinks() {
        graph.connect(link("d0", "d1", ConnectionType.ACTIVATE, 0));
        graph.connect(link("d1", "d2", ConnectionType.ACTIVATE, 0));
        assertEquals(2, graph.removeDevice(Id.of("d1")).size());
        assertTrue(graph.connections().isEmpty());
        assertTrue(graph.deviceAt(BlockRef.of(1, 0, 0)).isEmpty());
    }

    @Test
    void delayedLoopIsBudgetedNotInfinite() {
        graph.connect(link("d0", "d1", ConnectionType.TOGGLE, 0));
        graph.connect(link("d1", "d0", ConnectionType.TOGGLE, 1));
        bus.pulse(Id.of("d0"), null);
        for (long t = 1; t < 50; t++) bus.tick(t);
        assertTrue(bus.pendingSnapshot().size() <= 1, "an oscillator schedules at most one pending step");
    }
}
