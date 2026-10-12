package dev.playtime.engine.power;

import dev.playtime.engine.graph.Connection;
import dev.playtime.engine.graph.ConnectionGraph;
import dev.playtime.engine.graph.ConnectionType;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceType;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class PowerNetworkTest {
    private ConnectionGraph g;
    private PowerNetwork net;
    private int x;

    @BeforeEach
    void setUp() {
        g = new ConnectionGraph(new HashMap<>());
        net = new PowerNetwork(g);
    }

    private Device add(String id, DeviceType type) {
        Device d = new Device(Id.of(id), type, BlockRef.of(x++, 0, 0));
        g.addDevice(d);
        return d;
    }

    private void wire(String a, String b) {
        Connection c = new Connection();
        c.from = Id.of(a);
        c.to = Id.of(b);
        c.type = ConnectionType.POWER;
        assertFalse(g.connect(c).hasErrors());
    }

    @Test
    void generatorPowersThroughClosedSwitchOnly() {
        add("gen", DeviceType.GENERATOR);
        add("sw", DeviceType.POWER_SWITCH);
        add("door", DeviceType.DOOR_CONTROLLER);
        wire("gen", "sw");
        wire("sw", "door");
        g.state(Id.of("gen")).active = true;
        net.recompute(0);
        assertFalse(net.isPowered(Id.of("door")));
        assertTrue(net.isPowered(Id.of("sw")), "switch input is live");
        g.state(Id.of("sw")).active = true;
        PowerResult r = net.recompute(1);
        assertTrue(net.isPowered(Id.of("door")));
        assertTrue(r.nowPowered().contains(Id.of("door")));
    }

    @Test
    void fuseBoxNeedsEveryFuse() {
        add("gen", DeviceType.GENERATOR);
        Device box = add("box", DeviceType.FUSE_BOX);
        box.settings.put("sockets", "a=playtime:fuse,b=playtime:fuse");
        add("rx", DeviceType.POWER_RECEIVER);
        wire("gen", "box");
        wire("box", "rx");
        g.state(Id.of("gen")).active = true;
        g.state(Id.of("box")).sockets.put("a", Id.of("fuse"));
        net.recompute(0);
        assertFalse(net.isPowered(Id.of("rx")));
        g.state(Id.of("box")).sockets.put("b", Id.of("fuse"));
        net.recompute(1);
        assertTrue(net.isPowered(Id.of("rx")));
    }

    @Test
    void limitedCapacityServesPriorityFirstAndCanTrip() {
        Device gen = add("gen", DeviceType.GENERATOR);
        gen.settings.put("capacity", "2");
        Device lights = add("lights", DeviceType.LIGHT);
        lights.settings.put("demand", "2");
        Device door = add("door", DeviceType.DOOR_CONTROLLER);
        door.settings.put("demand", "2");
        door.settings.put("priority", "5");
        wire("gen", "lights");
        wire("gen", "door");
        g.state(gen.id).active = true;
        net.recompute(0);
        assertTrue(net.isPowered(door.id));
        assertFalse(net.isPowered(lights.id));

        gen.settings.put("trip_on_overload", "true");
        PowerResult r = net.recompute(1);
        assertTrue(r.tripped().contains(gen.id));
        assertFalse(g.state(gen.id).active);
        assertFalse(net.isPowered(door.id));
    }

    @Test
    void nodesThatNeedActivationBlockUntilActivated() {
        add("gen", DeviceType.GENERATOR);
        Device node = add("node", DeviceType.POWER_NODE);
        node.settings.put("needs_activation", "true");
        add("rx", DeviceType.POWER_RECEIVER);
        wire("gen", "node");
        wire("node", "rx");
        g.state(Id.of("gen")).active = true;
        net.recompute(0);
        assertFalse(net.isPowered(Id.of("rx")));
        g.state(node.id).active = true;
        net.recompute(1);
        assertTrue(net.isPowered(Id.of("rx")));
    }

    @Test
    void temporaryChargeExpiresWithoutScanning() {
        add("rx", DeviceType.POWER_RECEIVER);
        add("lamp", DeviceType.LIGHT);
        wire("rx", "lamp");
        net.recompute(0);
        net.chargeTemporarily(Id.of("rx"), 50);
        net.update(1);
        assertTrue(net.isPowered(Id.of("lamp")));
        assertTrue(net.update(20).isEmpty(), "nothing to do before expiry");
        PowerResult r = net.update(51);
        assertTrue(r.nowUnpowered().contains(Id.of("lamp")));
    }

    @Test
    void unpoweredNetworkIsStable() {
        add("rx", DeviceType.POWER_RECEIVER);
        assertTrue(net.update(0).isEmpty());
        assertTrue(net.update(1).isEmpty());
    }
}
