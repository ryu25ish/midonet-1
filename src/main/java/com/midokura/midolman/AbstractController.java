/*
 * Copyright 2011 Midokura KK
 */

package com.midokura.midolman;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.zookeeper.KeeperException;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.OFFlowRemoved.OFFlowRemovedReason;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.midokura.midolman.openflow.Controller;
import com.midokura.midolman.openflow.ControllerStub;
import com.midokura.midolman.openflow.MidoMatch;
import com.midokura.midolman.openvswitch.OpenvSwitchDatabaseConnection;
import com.midokura.midolman.packets.Ethernet;
import com.midokura.midolman.packets.IPv4;
import com.midokura.midolman.packets.ICMP;
import com.midokura.midolman.packets.TCP;
import com.midokura.midolman.packets.UDP;
import com.midokura.midolman.state.PortToIntNwAddrMap;
import com.midokura.midolman.state.ReplicatedMap.Watcher;
import com.midokura.midolman.util.Net;

public abstract class AbstractController implements Controller, AbstractControllerMXBean {

    private final static Logger log = LoggerFactory.getLogger(AbstractController.class);

    protected long datapathId;

    protected ControllerStub controllerStub;

    protected HashMap<UUID, Integer> portUuidToNumberMap;
    protected HashMap<Integer, UUID> portNumToUuid;
    protected PortToIntNwAddrMap portLocMap;

    // Tunnel management data structures
    protected HashMap<Integer, Integer> tunnelPortNumToPeerIp;
    protected HashMap<Integer, Integer> peerIpToTunnelPortNum;

    protected PortToIntNwAddrMap.Watcher listener;

    private OpenvSwitchDatabaseConnection ovsdb;

    protected int greKey;
    protected int publicIp;
    protected String externalIdKey;

    public final short nonePort = OFPort.OFPP_NONE.getValue();

    class PortLocMapListener implements Watcher<UUID, Integer> {
        public AbstractController controller;

        PortLocMapListener(AbstractController controller) {
            this.controller = controller;
        }

        public void processChange(UUID key, Integer oldAddr, Integer newAddr) {
            controller.portLocationUpdate(key, oldAddr, newAddr);
        }
    }

    public AbstractController(
            long datapathId,
            UUID switchUuid,
            int greKey,
            OpenvSwitchDatabaseConnection ovsdb,
            PortToIntNwAddrMap portLocMap,
            long flowExpireMinMillis,
            long flowExpireMaxMillis,
            long idleFlowExpireMillis,
            InetAddress internalIp,
            String externalIdKey) {
        this.datapathId = datapathId;
        this.ovsdb = ovsdb;
        this.greKey = greKey;
        this.portLocMap = portLocMap;
        this.externalIdKey = externalIdKey;
        publicIp = internalIp != null ? Net.convertInetAddressToInt(internalIp)
                                      : 0;
        portUuidToNumberMap = new HashMap<UUID, Integer>();
        portNumToUuid = new HashMap<Integer, UUID>();
        tunnelPortNumToPeerIp = new HashMap<Integer, Integer>();
        peerIpToTunnelPortNum = new HashMap<Integer, Integer>();
        listener = new PortLocMapListener(this);
        if (portLocMap != null)
            portLocMap.addWatcher(listener);
    }

    @Override
    public void setControllerStub(ControllerStub controllerStub) {
        this.controllerStub = controllerStub;
    }

    @Override
    public void onConnectionMade() {
        log.info("onConnectionMade");

        // TODO: Maybe find and record the datapath_id?
        //       The python implementation did, but here we get the dp_id
        //       in the constructor.

        // Delete all currently installed flows.
        OFMatch match = new OFMatch();
        controllerStub.sendFlowModDelete(match, false, (short)0, nonePort);

        // Add all the ports.
        for (OFPhysicalPort portDesc : controllerStub.getFeatures().getPorts())
            callAddPort(portDesc, portDesc.getPortNumber());
                
        portLocMap.start();
    }

    @Override
    public void onConnectionLost() {
        log.info("onConnectionLost");

        portLocMap.stop();

        portNumToUuid.clear();
        portUuidToNumberMap.clear();
        tunnelPortNumToPeerIp.clear();
        peerIpToTunnelPortNum.clear();
    }

    @Override
    public abstract void onPacketIn(int bufferId, int totalLen, short inPort,
                                    byte[] data);

    private void callAddPort(OFPhysicalPort portDesc, short portNum) {
        UUID uuid = getPortUuidFromOvsdb(datapathId, portNum);
        if (uuid != null) {
            portNumToUuid.put(new Integer(portNum), uuid);
            portUuidToNumberMap.put(uuid, new Integer(portNum));
            try {
                portLocMap.put(uuid, publicIp);
            } catch (KeeperException e) {
                log.warn("callAddPort", e);
            } catch (InterruptedException e) {
                log.warn("callAddPort", e);
            }
        }
        // TODO(pino, jlm): should this be an else-if?
        if (isGREPortOfKey(portDesc.getName())) {
            Integer peerIp = peerIpOfGrePortName(portDesc.getName());
            // TODO: Error out if already tunneled to this peer.
            tunnelPortNumToPeerIp.put(new Integer(portNum), peerIp);
            peerIpToTunnelPortNum.put(peerIp, new Integer(portNum));
            log.debug("Recording tunnel {} <=> {}", portNum,
                      Net.convertIntAddressToString(peerIp.intValue()));
        }

        addPort(portDesc, portNum);
    }

    @Override
    public final void onPortStatus(OFPhysicalPort portDesc,
                                   OFPortReason reason) {
        if (reason == OFPortReason.OFPPR_ADD) {
            short portNum = portDesc.getPortNumber();
            callAddPort(portDesc, portNum);
        } else if (reason == OFPortReason.OFPPR_DELETE) {
            deletePort(portDesc);
            Integer portNum = new Integer(portDesc.getPortNumber());
            portNumToUuid.remove(portNum);
            portUuidToNumberMap.remove(
                getPortUuidFromOvsdb(datapathId, portNum.shortValue()));
            Integer peerIp = tunnelPortNumToPeerIp.remove(portNum);
            peerIpToTunnelPortNum.remove(peerIp);
        } else {
            modifyPort(portDesc);
            UUID uuid = getPortUuidFromOvsdb(datapathId, 
                                             portDesc.getPortNumber());
            Integer portNum = new Integer(portDesc.getPortNumber());
            if (uuid != null) {
                portNumToUuid.put(portNum, uuid);
                portUuidToNumberMap.put(uuid, portNum);
            } else
                portNumToUuid.remove(portNum);

            if (isGREPortOfKey(portDesc.getName())) {
                Integer peerIp = peerIpOfGrePortName(portDesc.getName());
                tunnelPortNumToPeerIp.put(portNum, peerIp);
                peerIpToTunnelPortNum.put(peerIp, portNum);
            } else {
                Integer peerIp = tunnelPortNumToPeerIp.remove(portNum);
                peerIpToTunnelPortNum.remove(peerIp);
            }
        }
    }

    protected abstract void addPort(OFPhysicalPort portDesc, short portNum);
    protected abstract void deletePort(OFPhysicalPort portDesc);
    protected abstract void modifyPort(OFPhysicalPort portDesc);

    @Override
    public abstract void onFlowRemoved(OFMatch match, long cookie,
            short priority, OFFlowRemovedReason reason, int durationSeconds,
            int durationNanoseconds, short idleTimeout, long packetCount,
            long byteCount);

    @Override
    public void onMessage(OFMessage m) {
        log.debug("onMessage: {}", m);
        // Don't do anything else.
    }

    /* Maps a port UUID to its number on the local datapath. */
    public int portUuidToNumber(UUID port_uuid) {
        return portUuidToNumberMap.get(port_uuid);
    }

    /* Maps a remote port UUID to the number of the tunnel port where it
     * can be reached, if any. */
    public Integer portUuidToTunnelPortNumber(UUID port_uuid) {
        Integer intAddress = portLocMap.get(port_uuid);
        if (intAddress == null)
            return null;
        return peerIpToTunnelPortNum.get(intAddress);
    }

    protected Integer peerOfTunnelPortNum(int portNum) {
        return tunnelPortNumToPeerIp.get(portNum);
    }

    protected boolean isTunnelPortNum(int portNum) {
        return tunnelPortNumToPeerIp.containsKey(new Integer(portNum));
    }

    private boolean isGREPortOfKey(String portName) {
        if (portName == null || portName.length() != 15)
            return false;
        String greString = String.format("tn%05x", greKey);
        return portName.startsWith(greString);
    }

    protected Integer peerIpOfGrePortName(String portName) {
        String hexAddress = portName.substring(7, 15);
        return (new BigInteger(hexAddress, 16)).intValue();
    }

    public String makeGREPortName(int address) {
        return String.format("tn%05x%08x", greKey, address);
    }

    private boolean portLocMapContainsPeer(int peerAddress) {
        return portLocMap.containsValue(peerAddress);
    }

    protected UUID getPortUuidFromOvsdb(long datapathId, short portNum) {
        String extId = ovsdb.getPortExternalId(datapathId, portNum,
                                               externalIdKey);
        if (extId == null)
            return null;
        return UUID.fromString(extId);
    }

    private void portLocationUpdate(UUID portUuid, Integer oldAddr,
                                    Integer newAddr) {
        /* oldAddr: Former address of the port as an 
         *          integer (32-bit, big-endian); or null if a new port mapping.
         * newAddr: Current address of the port as an
         *          integer (32-bit, big-endian); or null if port mapping 
         *          was deleted.
         */

        log.info("PortLocationUpdate: {} moved from {} to {}",
            new Object[] { 
                portUuid, 
                oldAddr == null ? "null" 
                                : Net.convertIntAddressToString(oldAddr),
                newAddr == null ? "null"
                                : Net.convertIntAddressToString(newAddr)});
        if (newAddr != null && newAddr != publicIp) {
            // Try opening the tunnel even if we already have one in order to
            // cancel any in-progress tunnel deletion requests.
            String grePortName = makeGREPortName(newAddr);
            String newAddrStr = Net.convertIntAddressToString(newAddr);
            log.debug("Requesting tunnel from " + 
                      Net.convertIntAddressToString(publicIp) + " to " + 
                      newAddrStr + " with name " + grePortName);
            ovsdb.addGrePort(datapathId, grePortName, newAddrStr);
        }    

        if (oldAddr != null && oldAddr != publicIp) {
            // Peer might still be in portLocMap under a different portUuid.
            if (portLocMapContainsPeer(oldAddr))
                return;

            // Tear down the GRE tunnel.
            String grePortName = makeGREPortName(oldAddr);
            log.info("Tearing down tunnel " + grePortName);
            ovsdb.delPort(grePortName);
        }

        portMoved(portUuid, oldAddr, newAddr);
    }

    abstract protected void portMoved(UUID portUuid, Integer oldAddr,
                                      Integer newAddr);

    protected OFMatch createMatchFromPacket(Ethernet data, short inPort) {
        MidoMatch match = new MidoMatch();
        if (inPort != -1)
            match.setInputPort(inPort);
        match.setDataLayerDestination(data.getDestinationMACAddress());
        match.setDataLayerSource(data.getSourceMACAddress());
        match.setDataLayerType(data.getEtherType());
        match.setDataLayerVirtualLan(data.getVlanID());
        match.setDataLayerVirtualLanPriorityCodePoint(data.getPriorityCode());
        if (data.getEtherType() == IPv4.ETHERTYPE) {
            IPv4 packet = (IPv4) data.getPayload();
            match.setNetworkTypeOfService(packet.getDiffServ());
            match.setNetworkProtocol(packet.getProtocol());
            match.setNetworkSource(packet.getSourceAddress());
            match.setNetworkDestination(packet.getDestinationAddress());

            if (packet.getProtocol() == ICMP.PROTOCOL_NUMBER) {
                ICMP dgram = (ICMP) packet.getPayload();
                match.setTransportSource((short) dgram.getType());
                match.setTransportDestination((short) dgram.getCode());
            } else if (packet.getProtocol() == TCP.PROTOCOL_NUMBER) {
                TCP dgram = (TCP) packet.getPayload();
                match.setTransportSource(dgram.getSourcePort());
                match.setTransportDestination(dgram.getDestinationPort());
            } else if (packet.getProtocol() == UDP.PROTOCOL_NUMBER) {
                UDP dgram = (UDP) packet.getPayload();
                match.setTransportSource(dgram.getSourcePort());
                match.setTransportDestination(dgram.getDestinationPort());
            }
        }

        return match;
    }

    protected void addFlowAndPacketOut(OFMatch match, long cookie, 
                short idleTimeout, short hardTimeout, short priority,
                int bufferId, boolean sendFlowRemoval, boolean checkOverlap,
                boolean emergency, OFAction[] actions, short inPort,
                byte[] data) {
        List<OFAction> actionList = Arrays.asList(actions);
        controllerStub.sendFlowModAdd(match, cookie, idleTimeout, hardTimeout,
                                      priority, bufferId, sendFlowRemoval,
                                      checkOverlap, emergency, actionList);
        if (bufferId == 0xffffffff)
            controllerStub.sendPacketOut(bufferId, inPort, actionList, data);
    }
    
    public Map<Integer, Integer> getTunnelPortNumToPeerIp() {
        return tunnelPortNumToPeerIp;
    }

}
