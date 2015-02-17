/*
 * Copyright (c) 2011 Midokura Europe SARL, All Rights Reserved.
 */

package org.midonet.midolman.rules;

import org.midonet.packets.IPv4Addr;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

public class NatTarget {
    public final IPv4Addr nwStart;
    public final IPv4Addr nwEnd;
    public final int tpStart;
    public final int tpEnd;

    @JsonCreator
    public NatTarget(@JsonProperty("nwStart") IPv4Addr nwStart,
                     @JsonProperty("nwEnd") IPv4Addr nwEnd,
                     @JsonProperty("tpStart") int tpStart,
                     @JsonProperty("tpEnd") int tpEnd) {
        this.nwStart = nwStart;
        this.nwEnd = nwEnd;
        this.tpStart = tpStart & 0xffff;
        this.tpEnd = tpEnd & 0xffff;
    }

    public NatTarget(int nwStart, int nwEnd, int tpStart, int tpEnd) {
        this(new IPv4Addr(nwStart), new IPv4Addr(nwEnd), tpStart, tpEnd);
    }

    public NatTarget(IPv4Addr nwStart, IPv4Addr nwEnd) {
        this(nwStart, nwEnd, 1, 0xffff);
    }

    public NatTarget(IPv4Addr ipAddr) {
        this(ipAddr, ipAddr);
    }

    public NatTarget(IPv4Addr ipAddr, int tpStart, int tpEnd) {
        this(ipAddr, ipAddr, tpStart, tpEnd);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof NatTarget))
            return false;

        NatTarget nt = (NatTarget) other;
        return nwStart.equals(nt.nwStart) && nwEnd.equals(nt.nwEnd) &&
               tpStart == nt.tpStart && tpEnd == nt.tpEnd;
    }

    @Override
    public int hashCode() {
        int hash = nwStart.hashCode();
        hash = 13 * hash + nwEnd.hashCode();
        hash = 17 * hash + tpStart;
        return 23 * hash + tpEnd;
    }

    @Override
    public String toString() {
        return "NatTarget [" + "nwStart=" + nwStart.toString() + ", nwEnd="
               + nwEnd.toString() + ", tpStart=" + tpStart + ", tpEnd=" + tpEnd
               + "]";
    }
}
