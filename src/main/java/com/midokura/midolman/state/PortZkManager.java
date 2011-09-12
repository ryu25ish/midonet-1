/*
 * @(#)PortZkManager        1.6 11/09/08
 *
 * Copyright 2011 Midokura KK
 */
package com.midokura.midolman.state;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;

import com.midokura.midolman.layer3.Route;
import com.midokura.midolman.state.PortDirectory.BridgePortConfig;
import com.midokura.midolman.state.PortDirectory.PortConfig;
import com.midokura.midolman.state.PortDirectory.RouterPortConfig;

/**
 * This class was created to handle multiple ops feature in Zookeeper.
 * 
 * @version 1.6 08 Sept 2011
 * @author Ryu Ishimoto
 */
public class PortZkManager extends ZkManager {

    /**
     * PortZkManager constructor.
     * 
     * @param zk
     *            Zookeeper object.
     * @param basePath
     *            Directory to set as the base.
     */
    public PortZkManager(ZooKeeper zk, String basePath) {
        super(zk, basePath);
    }

    public List<Op> preparePortCreate(ZkNodeEntry<UUID, PortConfig> portNode)
            throws ZkStateSerializationException {

        List<Op> ops = new ArrayList<Op>();
        try {
            ops.add(Op.create(pathManager.getPortPath(portNode.key),
                    serialize(portNode.value), Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT));
        } catch (IOException e) {
            throw new ZkStateSerializationException(
                    "Could not serialize PortConfig", e, PortConfig.class);
        }

        if (portNode.value instanceof RouterPortConfig) {
            ops.add(Op.create(pathManager.getRouterPortPath(
                    portNode.value.device_id, portNode.key), null,
                    Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));
            ops.add(Op.create(pathManager.getPortRoutesPath(portNode.key),
                    null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));
        } else if (portNode.value instanceof BridgePortConfig) {
            ops.add(Op.create(pathManager.getBridgePortPath(
                    portNode.value.device_id, portNode.key), null,
                    Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));
        } else {
            throw new IllegalArgumentException("Unrecognized port type.");
        }

        return ops;
    }

    public UUID create(PortConfig port) throws IOException, KeeperException,
            InterruptedException, ZkStateSerializationException {
        UUID id = UUID.randomUUID();
        ZkNodeEntry<UUID, PortConfig> portNode = new ZkNodeEntry<UUID, PortConfig>(
                id, port);
        zk.multi(preparePortCreate(portNode));
        return id;
    }

    public List<Op> getRouterPortDeleteOps(UUID id, UUID routerId)
            throws KeeperException, InterruptedException, IOException {
        List<Op> ops = new ArrayList<Op>();
        // Get delete ops for port routes.
        RouteZkManager routeZk = new RouteZkManager(zk, basePath);
        HashMap<UUID, Route> routes = routeZk.listPortRoutes(id);
        for (Map.Entry<UUID, Route> entry : routes.entrySet()) {
            ops.addAll(routeZk.getPortRouteDeleteOps(entry.getKey(), id));
        }
        ops.add(Op.delete(pathManager.getRouterPortPath(routerId, id), -1));
        ops.add(Op.delete(pathManager.getPortPath(id), -1));
        return ops;
    }

    public List<Op> getBridgePortDeleteOps(UUID id, UUID bridgeId)
            throws KeeperException, InterruptedException, IOException {
        List<Op> ops = new ArrayList<Op>();
        ops.add(Op.delete(pathManager.getBridgePortPath(bridgeId, id), -1));
        ops.add(Op.delete(pathManager.getPortPath(id), -1));
        return ops;
    }

    public void delete(UUID id) throws InterruptedException, KeeperException,
            IOException, ClassNotFoundException {
        PortConfig port = get(id);
        // Check if this is a bridge port.
        if (port instanceof BridgePortConfig) {
            this.zk.multi(getBridgePortDeleteOps(id, port.device_id));
        } else {
            this.zk.multi(getRouterPortDeleteOps(id, port.device_id));
        }
    }

    /**
     * Get a PortConfig object.
     * 
     * @param id
     *            Router UUID,
     * @return A PortConfigs
     * @throws KeeperException
     *             Zookeeper exception.
     * @throws InterruptedException
     *             Paused thread interrupted.
     * @throws ClassNotFoundException
     *             Unknown class.
     * @throws IOException
     *             Serialization error.
     */
    public PortConfig get(UUID id) throws KeeperException,
            InterruptedException, IOException, ClassNotFoundException {
        byte[] data = zk.getData(pathManager.getPortPath(id), null, null);
        return deserialize(data, PortConfig.class);
    }

    /**
     * Update a port data.
     * 
     * @param id
     *            Port UUID
     * @param port
     *            PortConfig object.
     * @throws IOException
     *             Serialization error.
     * @throws InterruptedException
     * @throws KeeperException
     */
    public void update(UUID id, PortConfig port) throws IOException,
            KeeperException, InterruptedException {
        // Update any version for now.
        zk.setData(pathManager.getPortPath(id), serialize(port), -1);
    }

    public HashMap<UUID, PortConfig> listPorts(String path)
            throws KeeperException, InterruptedException, IOException,
            ClassNotFoundException {
        HashMap<UUID, PortConfig> configs = new HashMap<UUID, PortConfig>();
        List<String> portIds = zk.getChildren(path, null);
        for (String portId : portIds) {
            // For now get each one.
            UUID id = UUID.fromString(portId);
            configs.put(id, get(id));
        }
        return configs;
    }

    public HashMap<UUID, PortConfig> listRouterPorts(UUID routerId)
            throws KeeperException, InterruptedException, IOException,
            ClassNotFoundException {
        return listPorts(pathManager.getRouterPortsPath(routerId));
    }

    public HashMap<UUID, PortConfig> listBridgePorts(UUID bridgeId)
            throws KeeperException, InterruptedException, IOException,
            ClassNotFoundException {
        return listPorts(pathManager.getBridgePortsPath(bridgeId));
    }
}
