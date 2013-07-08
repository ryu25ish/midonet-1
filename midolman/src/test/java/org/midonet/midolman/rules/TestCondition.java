/*
 * Copyright 2011 Midokura KK
 */

package org.midonet.midolman.rules;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.midonet.cache.Cache;
import org.midonet.midolman.vrn.ForwardInfo;
import org.midonet.packets.IPv4;
import org.midonet.packets.IPv4Addr;
import org.midonet.packets.IPv4Subnet;
import org.midonet.packets.Transport;
import org.midonet.sdn.flows.WildcardMatch;
import org.midonet.util.Range;
import org.midonet.util.functors.Callback1;


public class TestCondition {

    private WildcardMatch pktMatch;
    static Random rand;
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static JsonFactory jsonFactory = new JsonFactory(objectMapper);
    private ForwardInfo fwdInfo;
    private DummyCache connCache;

    static {
        objectMapper.setVisibilityChecker(objectMapper.getVisibilityChecker()
                                              .withFieldVisibility(
                                                  Visibility.ANY));
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    }

    @Before
    public void setUp() {
        pktMatch = new WildcardMatch();
        pktMatch.setInputPort((short) 5);
        pktMatch.setDataLayerSource("02:11:33:00:11:01");
        pktMatch.setDataLayerDestination("02:11:aa:ee:22:05");
        pktMatch.setDataLayerType(IPv4.ETHERTYPE);
        pktMatch.setNetworkSource(IPv4Addr.fromInt(0x0a001406));
        pktMatch.setNetworkDestination(IPv4Addr.fromInt(0x0a000b22));
        pktMatch.setNetworkProtocol((byte) 6);
        pktMatch.setNetworkTypeOfService((byte) 34);
        pktMatch.setTransportSource(4321);
        pktMatch.setTransportDestination(1234);
        rand = new Random();

        connCache = new DummyCache();
        fwdInfo = new ForwardInfo(false, connCache, UUID.randomUUID());
        fwdInfo.flowMatch = pktMatch;
    }

    @Test
    public void testConjunctionInv() {
        Condition cond = new Condition();
        UUID inPort = new UUID(rand.nextLong(), rand.nextLong());
        // This condition should match all packets.
        fwdInfo.inPortId = inPort;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.conjunctionInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testInPortIds() {
        Condition cond = new Condition();
        UUID inPort = new UUID(rand.nextLong(), rand.nextLong());
        cond.inPortIds = new HashSet<UUID>();
        cond.inPortIds.add(new UUID(rand.nextLong(), rand.nextLong()));
        cond.inPortIds.add(new UUID(rand.nextLong(), rand.nextLong()));
        // The condition should not match the packet.
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        fwdInfo.inPortId = inPort;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        // Verify that inPortInv causes a match.
        cond.inPortInv = true;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        fwdInfo.inPortId = null;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        // Now add inPort to the condition - it stops matching due to invert.
        cond.inPortIds.add(inPort);
        fwdInfo.inPortId = inPort;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.inPortInv = false;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testOutPortIds() {
        Condition cond = new Condition();
        UUID inPort = new UUID(rand.nextLong(), rand.nextLong());
        UUID outPort = new UUID(rand.nextLong(), rand.nextLong());
        fwdInfo.inPortId = inPort;
        cond.outPortIds = new HashSet<UUID>();
        cond.outPortIds.add(new UUID(rand.nextLong(), rand.nextLong()));
        cond.outPortIds.add(new UUID(rand.nextLong(), rand.nextLong()));
        // The condition should not match the packet.
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, true));
        fwdInfo.outPortId = outPort;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, true));
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        // Verify that outPortInv causes a match.
        cond.outPortInv = true;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, true));
        fwdInfo.outPortId = null;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, true));
        // Now add outPort to the condition - it stops matching due to invert
        // on forwarding elements, but stays the same on port filters.
        cond.outPortIds.add(outPort);
        fwdInfo.outPortId = outPort;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, true));
        cond.outPortInv = false;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, true));
    }

    @Test
    public void testNwTos() {
        Condition cond = new Condition();
        UUID inPort = new UUID(rand.nextLong(), rand.nextLong());
        fwdInfo.inPortId = inPort;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwTos = 5;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.nwTosInv = true;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwTosInv = false;
        cond.nwTos = 34;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwTosInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testNwProto() {
        Condition cond = new Condition();
        fwdInfo.inPortId = UUID.randomUUID();
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwProto = 5;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.nwProtoInv = true;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwProtoInv = false;
        cond.nwProto = 6;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwProtoInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testNwSrc() {
        Condition cond = new Condition();
        fwdInfo.inPortId = UUID.randomUUID();
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        // Inverting nwSrc has no effect when it's wild-carded.
        cond.nwSrcInv = true;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwSrcInv = false;
        // Set the nwSrcIp to something different than the packet's 0x0a001406.
        cond.nwSrcIp = new IPv4Subnet(IPv4Addr.fromString("10.0.20.3"), 0);
        // Since nwSrcLength is still 0, the condition still matches the packet.
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwSrcIp = new IPv4Subnet(IPv4Addr.fromString("10.0.20.3"), 32);
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        // Now try shorter prefixes:
        cond.nwSrcIp = new IPv4Subnet(IPv4Addr.fromString("10.0.20.3"), 24);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwSrcIp = new IPv4Subnet(IPv4Addr.fromString("10.0.20.3"), 16);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        // Now try length 0 with an ip that differs in the left-most bit.
        cond.nwSrcIp = new IPv4Subnet(IPv4Addr.fromString("250.1.1.4"), 1);
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.nwSrcIp = new IPv4Subnet(IPv4Addr.fromString("250.1.1.4"), 0);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwSrcInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        // Now increase the maskLength. The packet doesn't match the
        // condition's srcIp, but the nwSrcInv is true.
        cond.nwSrcIp = new IPv4Subnet(IPv4Addr.fromString("250.1.1.4"), 32);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        // Remove the invert, set the nwSrcIp to the packet's
        cond.nwSrcInv = false;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.nwSrcIp = new IPv4Subnet(IPv4Addr.fromString("10.0.20.6"), 32);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwSrcInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testNwDst() {
        Condition cond = new Condition();
        // Empty condition matches the packet.
        fwdInfo.inPortId = UUID.randomUUID();
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        // Inverting is ignored if the field is null.
        cond.nwDstInv = true;
        // Condition still matches the packet.
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwDstInv = false;
        // Set the nwDstIp to something different than the packet's 0x0a000b22.
        cond.nwDstIp = new IPv4Subnet(IPv4Addr.fromString("10.0.11.35"), 0);
        // Since nwDstLength is 0, the condition still matches the packet.
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        // Now try inverting the result
        cond.nwDstInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.nwDstInv = false;
        cond.nwDstIp = new IPv4Subnet(IPv4Addr.fromString("10.0.11.35"), 32);
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        // Now try shorter prefixes:
        cond.nwDstIp = new IPv4Subnet(IPv4Addr.fromString("10.0.11.35"), 31);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwDstIp = new IPv4Subnet(IPv4Addr.fromString("10.0.11.35"), 24);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwDstIp = new IPv4Subnet(IPv4Addr.fromString("10.0.11.35"), 16);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        // Now try inverting
        cond.nwDstInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.nwDstIp = new IPv4Subnet(IPv4Addr.fromString("10.0.11.35"), 32);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        // Remove the invert, set the nwDstIp to the packet's
        cond.nwDstInv = false;
        cond.nwDstIp = new IPv4Subnet(IPv4Addr.fromString("10.0.11.34"), 32);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.nwDstInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testTpSrc() {
        // Note tpSrc is set to 4321
        Condition cond = new Condition();
        fwdInfo.inPortId = UUID.randomUUID();
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcRange = new Range<Integer>(30, Transport.MAX_PORT_NO);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = false;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcRange = new Range<Integer>(4322, Transport.MAX_PORT_NO);
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = true;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = false;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcRange = new Range<Integer>(4321, 4321) ;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = false;
        cond.tpSrcRange = new Range<Integer>(0, 4322);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testTpSrc_upperPorts() {
        pktMatch.setTransportSource(40000);

        Condition cond = new Condition();
        fwdInfo.inPortId = UUID.randomUUID();
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcRange = new Range<Integer>(30000, Transport.MAX_PORT_NO);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = false;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcRange = new Range<Integer>(45000, Transport.MAX_PORT_NO);
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = true;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = false;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcRange = new Range<Integer>(35000, 45000);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpSrcInv = false;
        cond.tpSrcRange = new Range<Integer>(0, Transport.MAX_PORT_NO);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testTpDst() {
        // tpDst is set to 1234
        Condition cond = new Condition();
        fwdInfo.inPortId = UUID.randomUUID();
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstRange = new Range<Integer>(1235, Transport.MAX_PORT_NO);
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = true;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = false;
        cond.tpDstRange = new Range<Integer>(1233, Transport.MAX_PORT_NO);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = false;
        cond.tpDstRange = new Range<Integer>(1233, 1233);
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstRange = new Range<Integer>(1233, 1234);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = false;
        cond.tpDstRange = new Range<Integer>(0, 1234);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testTpDst_upperPorts() {
        pktMatch.setTransportDestination(50000);

        Condition cond = new Condition();
        fwdInfo.inPortId = UUID.randomUUID();
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstRange = new Range<Integer>(40000, Transport.MAX_PORT_NO);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = false;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstRange = new Range<Integer>(55000, Transport.MAX_PORT_NO);
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = true;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = false;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstRange = new Range<Integer>(45000, 55000);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.tpDstInv = false;
        cond.tpDstRange = new Range<Integer>(0, Transport.MAX_PORT_NO);
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testFwdFlow() {
        Condition cond = new Condition();
        fwdInfo.inPortId = UUID.randomUUID();
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        Assert.assertFalse(fwdInfo.isConnTracked());
        cond.matchForwardFlow = true;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        Assert.assertTrue(fwdInfo.isConnTracked());
        // Still matches forward flow.
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        cond.matchReturnFlow = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.matchForwardFlow = false;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testReturnFlow() {
        Condition cond = new Condition();
        fwdInfo.inPortId = UUID.randomUUID();
        connCache.setStoredValue("r");
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
        Assert.assertFalse(fwdInfo.isConnTracked());
        cond.matchForwardFlow = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        Assert.assertTrue(fwdInfo.isConnTracked());
        cond.matchReturnFlow = true;
        Assert.assertFalse(cond.matches(fwdInfo, pktMatch, false));
        cond.matchForwardFlow = false;
        Assert.assertTrue(cond.matches(fwdInfo, pktMatch, false));
    }

    @Test
    public void testIpv6() {
        Condition cond = new Condition();
        cond.dlType = 0x86DD;
        fwdInfo.inPortId = UUID.randomUUID();

        WildcardMatch pktMatch6 = new WildcardMatch();
        pktMatch6.setDataLayerType((short)0x86DD);

        Assert.assertTrue(cond.matches(fwdInfo, pktMatch6, false));
    }

    @Test
    public void testSerialization() throws IOException, ClassNotFoundException {
        Condition cond = new Condition();
        Set<UUID> ids = new HashSet<UUID>();
        ids.add(UUID.randomUUID());
        ids.add(UUID.randomUUID());
        cond.inPortIds = ids;
        cond.portGroup = UUID.randomUUID();
        cond.tpSrcRange = new Range<Integer>(40000, 41000);
        cond.tpSrcInv = false;
        cond.tpDstRange = new Range<Integer>(42000, 43000);
        cond.tpDstInv = true;
        cond.dlType = 0x86DD;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream out = new BufferedOutputStream(bos);
        JsonGenerator jsonGenerator =
            jsonFactory.createJsonGenerator(new OutputStreamWriter(out));
        jsonGenerator.writeObject(cond);
        out.close();
        byte[] data = bos.toByteArray();
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        InputStream in = new BufferedInputStream(bis);
        JsonParser jsonParser =
            jsonFactory.createJsonParser(new InputStreamReader(in));
        Condition c = jsonParser.readValueAs(Condition.class);
        in.close();
        Assert.assertTrue(cond.equals(c));
    }

    static class DummyCache implements Cache {
        public void set(String key, String value) { }
        public String get(String key) { return storedValue; }
        public Map<String, String> dump(int maxEntries) {return null;}
        public void delete(String key) {}
        public void getAsync(String key, Callback1<String> cb) {
            throw new UnsupportedOperationException();
        }
        public String getAndTouch(String key) { return storedValue; }
        public int getExpirationSeconds() { return 0; }
        public void setStoredValue(String value) { storedValue = value; }

        private String storedValue = null;
    }
}
