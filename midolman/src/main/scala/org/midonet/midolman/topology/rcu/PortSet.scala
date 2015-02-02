/*
* Copyright 2012 Midokura Europe SARL
*/
package org.midonet.midolman.topology.rcu

import collection.immutable
import java.util.UUID


case class PortSet(id: UUID, hosts: immutable.Set[UUID],
                   localPorts: immutable.Set[UUID])