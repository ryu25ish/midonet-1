/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.cluster.data.midonet;

import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonTypeInfo;

import org.midonet.packets.*;

public class PortDirectory {
    public static Random rand = new Random(System.currentTimeMillis());

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
        property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = BridgePortConfig.class,
            name = "BridgePort"),
        @JsonSubTypes.Type(value = RouterPortConfig.class,
            name = "RouterPort"),
        @JsonSubTypes.Type(value = ExteriorBridgePortConfig.class,
            name = "ExteriorBridgePort"),
        @JsonSubTypes.Type(value = ExteriorRouterPortConfig.class,
            name = "ExteriorRouterPort"),
        @JsonSubTypes.Type(value = InteriorBridgePortConfig.class,
            name = "InteriorBridgePort"),
        @JsonSubTypes.Type(value = InteriorRouterPortConfig.class,
            name = "InteriorRouterPort"),
        @JsonSubTypes.Type(value = VxLanPortConfig.class,
            name = "VxLanPort")})
    public abstract static class PortConfig extends ConfigWithProperties {

        PortConfig(UUID device_id) {
            this(device_id, false);
        }

        PortConfig(UUID device_id, boolean adminStateUp) {
            super();
            this.device_id = device_id;
            this.adminStateUp = adminStateUp;
        }

        // Default constructor for the Jackson deserialization.
        PortConfig() { super(); }
        public UUID device_id;
        public boolean adminStateUp;
        public UUID inboundFilter;
        public UUID outboundFilter;
        public Set<UUID> portGroupIDs;
        public int tunnelKey;

        public UUID hostId;
        public String interfaceName;
        public UUID peerId;
        public String v1ApiType;

        public String getV1ApiType() { return v1ApiType; }
        public void setV1ApiType(String type) { v1ApiType=type; }

        // Custom accessors for Jackson serialization
        public UUID getHostId() { return hostId; }
        public void setHostId(UUID hostId) {
            this.hostId = hostId;
        }
        public String getInterfaceName() { return interfaceName; }
        public void setInterfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
        }
        public UUID getPeerId() { return peerId; }
        public void setPeerId(UUID pId) { peerId = pId; }

        @JsonIgnore
        public boolean isInterior() { return peerId != null; }
        @JsonIgnore
        public boolean isExterior() { return hostId != null; }
        @JsonIgnore
        public boolean isUnplugged() { return !isInterior() && !isExterior(); }
        @JsonIgnore
        public boolean hasSubnet(IPv4Subnet sub) { return false; };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PortConfig that = (PortConfig) o;

            if (tunnelKey != that.tunnelKey) return false;
            if (device_id != null
                ? !device_id.equals(that.device_id) : that.device_id != null)
                return false;
            if (inboundFilter != null
                ? !inboundFilter.equals(that.inboundFilter)
                : that.inboundFilter != null)
                return false;
            if (outboundFilter != null
                ? !outboundFilter.equals(that.outboundFilter)
                : that.outboundFilter != null)
                return false;
            if (portGroupIDs != null ? !portGroupIDs.equals(that.portGroupIDs)
                                     : that.portGroupIDs != null)
                return false;
            if (v1ApiType != null ? v1ApiType.equals(that.v1ApiType)
                                  : that.v1ApiType != null)
                return false;
            return
                hostId == null ? that.hostId == null : hostId.equals(that.hostId)
                                                       && interfaceName == null ?
                                                       that.interfaceName == null :
                                                       interfaceName.equals(that.interfaceName)
                                                       && peerId == null? that.peerId == null :
                                                       peerId.equals(that.peerId)
                                                       && adminStateUp == that.adminStateUp;
        }

        @Override
        public int hashCode() {
            int result = device_id != null ? device_id.hashCode() : 0;
            result = 31 * result +
                     (inboundFilter != null ? inboundFilter.hashCode() : 0);
            result = 31 * result +
                     (outboundFilter != null ? outboundFilter.hashCode() : 0);
            result = 31 * result +
                     (portGroupIDs != null ? portGroupIDs.hashCode() : 0);
            result = 31 * result + tunnelKey;
            result = 31 * result + (peerId != null ? peerId.hashCode() : 0);
            result = 31 * result + (hostId != null ? hostId.hashCode() : 0);
            result = 31 * result +
                     (interfaceName != null ? interfaceName.hashCode() : 0);
            result = 31 * result +
                     (v1ApiType != null ? v1ApiType.hashCode() : 0);
            result = 31 * result + Boolean.valueOf(adminStateUp).hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "PortConfig{ device_id=," + device_id +
                   ", hostId=" + hostId +
                   ", interfaceName=" + interfaceName +
                   ", peerId=" + peerId +
                   ", adminStateUp=" + adminStateUp + '}';
        }
    }


    public static class BridgePortConfig extends PortConfig {
        public Short vlanId;

        public BridgePortConfig(UUID device_id) {
            super(device_id);
        }

        public BridgePortConfig(UUID device_id, boolean adminStateUp) {
            super(device_id, adminStateUp);
        }

        public BridgePortConfig(UUID deviceId, UUID peerId, Short vlanId) {
            this.device_id = deviceId;
            this.peerId = peerId;
            this.vlanId = vlanId;
        }

        // Default constructor for the Jackson deserialization.
        public BridgePortConfig() { super(); }

        public Short getVlanId() { return vlanId; }
        public void setVlanId(Short vlanId) {
            this.vlanId = vlanId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            BridgePortConfig that = (BridgePortConfig) o;
            if (vlanId != null ? !vlanId.equals(that.vlanId) :
                    that.vlanId != null)
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (vlanId != null ? vlanId.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "BridgePortConfig{peer_uuid=" + peerId +
                    ", vlanId = " + vlanId + "}";
        }
    }

    // NOTE(tfukushima): Jackson only supports one-to-one mappings between
    //   type names and `JsonSubType`s. So we have to create duplicated
    //   classes with the different names.
    //
    //   See `org.midonet.midolman.state.PortConfig` for details.
    final public static class InteriorBridgePortConfig extends BridgePortConfig {}
    final public static class ExteriorBridgePortConfig extends BridgePortConfig {}

    public static class RouterPortConfig extends PortConfig {
        // TODO(pino): use IntIPv4 for Babuza!
        public int nwAddr;
        public int nwLength;
        public int portAddr;
        public MAC hwAddr;
        public transient Set<BGP> bgps;

        // Routes are stored in a ZK sub-directory. Don't serialize them.
        public transient Set<Route> routes;

        public RouterPortConfig(UUID routerId, IPv4Subnet network,
                                IPv4Addr portAddr, boolean adminStateUp) {
            this(routerId, network.getIntAddress(), network.getPrefixLen(),
                    portAddr.addr(), adminStateUp);
        }

        public RouterPortConfig(UUID routerId, int networkAddr,
                                int networkLength, int portAddr,
                                boolean adminStateUp) {
            this(routerId, networkAddr, networkLength, portAddr, null,
                    MAC.random());
            this.adminStateUp = adminStateUp;
        }

        public RouterPortConfig(UUID device_id, int networkAddr,
                int networkLength, int portAddr, Set<Route> routes,
                MAC mac) {
            super(device_id);
            this.nwAddr = networkAddr;
            this.nwLength = networkLength;
            this.portAddr = portAddr;
            this.routes = routes;
            this.hwAddr = (mac == null) ? MAC.random() : mac;
        }

        public RouterPortConfig(UUID device_id,
                                int networkAddr,
                                int networkLength,
                                int portAddr, MAC mac,
                                Set<Route> routes,
                                Set<BGP> bgps) {
            this(device_id, networkAddr, networkLength, portAddr, routes, mac);
            setBgps(bgps);
        }

        // Default constructor for the Jackson deserialization.
        public RouterPortConfig() {
            super();
            this.hwAddr = MAC.random();
        }

        // Custom accessors for Jackson serialization

        public String getNwAddr() {
            return IPv4Addr.intToString(this.nwAddr);
        }

        public void setNwAddr(String addr) {
            this.nwAddr = IPv4Addr.stringToInt(addr);
        }

        public String getPortAddr() {
            return IPv4Addr.intToString(this.portAddr);
        }

        public void setPortAddr(String addr) {
            this.portAddr = IPv4Addr.stringToInt(addr);
        }

        public MAC getHwAddr() { return hwAddr; }

        public void setHwAddr(MAC hwAddr) { this.hwAddr = hwAddr; }

        public Set<Route> getRoutes() { return routes; }
        public void setRoutes(Set<Route> routes) { this.routes = routes; }

        public Set<BGP> getBgps() { return bgps; }
        public void setBgps(Set<BGP> bgps) { this.bgps = bgps; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("RouterPort [");
            sb.append("nwAddr=").append(IPv4Addr.intToString(nwAddr));
            sb.append(", nwLength=").append(nwLength);
            sb.append(", portAddr=").append(IPv4Addr.intToString(portAddr));
            sb.append(", bgps={");
            if (null != bgps) {
                for (BGP b : bgps)
                    sb.append(b.toString());
            }
            sb.append("}]");
            return sb.toString();
        }

        @Override
        public boolean equals(Object other) {
            if (other == null)
                return false;
            if (other == this)
                return true;
            if (!(other instanceof RouterPortConfig))
                return false;
            RouterPortConfig that = (RouterPortConfig) other;
            return device_id.equals(that.device_id)
                    && nwAddr == that.nwAddr
                    && nwLength == that.nwLength
                    && portAddr == that.portAddr
                    && hwAddr == null ? that.getHwAddr() == null :
                       hwAddr.equals(that.getHwAddr())
                    && getRoutes() == null ? that.getRoutes() == null :
                       getRoutes().equals(that.getRoutes())
                    && getBgps() == null ? that.getBgps() == null :
                       getBgps().equals(that.getBgps());
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + nwAddr;
            result = 31 * result + nwLength;
            result = 31 * result + portAddr;
            result = 31 * result + (hwAddr != null ? hwAddr.hashCode() : 0);
            result = 31 * result + (bgps != null ? bgps.hashCode() : 0);
            return result;
        }

        @JsonIgnore
        @Override
        public boolean hasSubnet(IPv4Subnet sub) {
            return sub.getIntAddress() == nwAddr
                    && sub.getPrefixLen() == nwLength;
        }

        @JsonIgnore
        public boolean portAddressEquals(IPAddr addr) {
            if (addr == null) {
                throw new IllegalArgumentException("addr is null");
            }

            if (addr instanceof IPv6Addr) {
                throw new UnsupportedOperationException("IPv6 not supported");
            }

            return Objects.equals(addr, IPv4Addr.fromInt(this.portAddr));
        }
    }

    // NOTE(tfukushima): Jackson only supports one-to-one mappings between
    //   type names and `JsonSubType`s. So we have to create duplicated
    //   classes with the different names.
    //
    //   See `org.midonet.midolman.state.PortConfig` for details.
    final public static class InteriorRouterPortConfig extends RouterPortConfig {}
    final public static class ExteriorRouterPortConfig extends RouterPortConfig {}

    public static class VxLanPortConfig extends PortConfig {

        public String mgmtIpAddr;
        public int mgmtPort;
        public int vni;
        public String tunIpAddr;
        public UUID tunnelZoneId;

        public String getMgmtIpAddr() {
            return mgmtIpAddr;
        }

        public void setMgmtIpAddr(String mgmtIpAddr) {
            this.mgmtIpAddr = mgmtIpAddr;
        }

        public int getMgmtPort() {
            return mgmtPort;
        }

        public void setMgmtPort(int mgmtPort) {
            this.mgmtPort = mgmtPort;
        }

        public int getVni() {
            return vni;
        }

        public void setVni(int vni) {
            this.vni = vni;
        }

        public String getTunIpAddr() {
            return tunIpAddr;
        }

        public void setTunIpAddr(String tunIpAddr) {
            this.tunIpAddr = tunIpAddr;
        }

        public UUID getTunnelZoneId() {
            return tunnelZoneId;
        }

        public void setTunnelZoneId(UUID tunnelZoneId) {
            this.tunnelZoneId = tunnelZoneId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            VxLanPortConfig that = (VxLanPortConfig) o;

            return mgmtPort == that.mgmtPort &&
                   vni == that.vni &&
                   Objects.equals(tunIpAddr , that.tunIpAddr) &&
                   Objects.equals(tunnelZoneId , that.tunnelZoneId) &&
                   Objects.equals(mgmtIpAddr, that.mgmtIpAddr);
        }

        @Override
        public int hashCode() {
            return vni;
        }

        @Override
        public String toString() {
            return "VxLanPortConfig{mgmtIpAddr=" + mgmtIpAddr +
                    ", mgmtPort=" + mgmtPort + ", vni=" + vni +
                    ", tunnelIp=" + tunIpAddr + ", tunnelZoneId=" +
                    tunnelZoneId + "}";
        }
    }
}
