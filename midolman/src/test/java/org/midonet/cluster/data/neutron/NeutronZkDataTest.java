/*
 * Copyright (c) 2014 Midokura SARL, All Rights Reserved.
 */
package org.midonet.cluster.data.neutron;

import java.util.HashMap;
import java.util.Map;

import org.apache.curator.test.TestingServer;
import org.junit.Before;
import org.junit.Test;

import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.state.DirectoryVerifier;
import org.midonet.midolman.state.PathBuilder;
import org.midonet.midolman.state.StateAccessException;

public final class NeutronZkDataTest extends NeutronPluginTest {

    private DirectoryVerifier dirVerifier;
    private PathBuilder pathBuilder;

    @Before
    public void setUp() throws Exception {

        super.setUp();

        pathBuilder = getPathBuilder();
        dirVerifier = new DirectoryVerifier(getDirectory());
    }

    private void verifyFipDnatRule(int expectedMatchCnt) {

        String rulesPath = pathBuilder.getRulesPath();

        String floatingIpAddr = floatingIp.floatingIpAddress;
        String fixedIpAddr = floatingIp.fixedIpAddress;

        Map<String, Object> matches = new HashMap<>();
        matches.put("type", "ForwardNat");
        matches.put("condition.nwDstIp.address", floatingIpAddr);
        matches.put("natTargets[0].nwStart", fixedIpAddr);
        matches.put("natTargets[0].nwEnd", fixedIpAddr);

        dirVerifier.assertChildrenFieldsMatch(rulesPath, matches,
                                              expectedMatchCnt);
    }

    private void verifyFipSnatRule(int expectedMatchCnt) {

        String rulesPath = pathBuilder.getRulesPath();

        String floatingIpAddr = floatingIp.floatingIpAddress;
        String fixedIpAddr = floatingIp.fixedIpAddress;

        Map<String, Object> matches = new HashMap<>();
        matches.put("type", "ForwardNat");
        matches.put("condition.nwSrcIp.address", fixedIpAddr);
        matches.put("natTargets[0].nwStart", floatingIpAddr);
        matches.put("natTargets[0].nwEnd", floatingIpAddr);

        dirVerifier.assertChildrenFieldsMatch(rulesPath, matches,
                                              expectedMatchCnt);
    }

    private void verifyFloatingIpRules() {

        verifyFipSnatRule(1);
        verifyFipDnatRule(1);

    }

    public void verifyNoFloatingIpRules() {

        verifyFipSnatRule(0);
        verifyFipDnatRule(0);
    }

    @Test
    public void testBasicScenario()
        throws SerializationException, StateAccessException {

        verifyFloatingIpRules();

        plugin.deleteNetwork(extNetwork.id);

        verifyNoFloatingIpRules();
    }
}
