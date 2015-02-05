#!/usr/bin/env bash

# Copyright 2015 Midokura SARL
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script creates the fake uplink assuming that midonet is running
# correctly.  It takes the IP address CIDR as its argument which gets
# routed into the MidoNet provider router.
# Example:
#      ./create_fake_uplink.sh 200.0.0.0/24

if [[ -n "$1" ]]; then
    CIDR=$1
else
    echo "Usage: create_fake_uplink.sh <CIDR>"
    exit -1
fi

# Save the top directory and source the functions and midorc
TOP_DIR=$(cd $(dirname "$0") && pwd)
source $TOP_DIR/midorc
source $TOP_DIR/functions

set -e

# Grab the provider router
PROVIDER_ROUTER_NAME='MidoNet Provider Router'
PROVIDER_ROUTER_ID=$(midonet-cli -e router list | \
    grep "$PROVIDER_ROUTER_NAME" | \
    awk '{ print $2 }')
die_if_not_set $LINENO PROVIDER_ROUTER_ID "FAILED to find a provider router"
echo "Provider Router: ${PROVIDER_ROUTER_ID}"

# Get the host id of the devstack machine
HOST_ID=$(midonet-cli -e host list | awk '{ print $2 }')
die_if_not_set $LINENO HOST_ID "FAILED to obtain host id"
echo "Host: ${HOST_ID}"

# Check if the default tunnel zone exists
TZ_NAME='default_tz'
TZ_ID=$(midonet-cli -e list tunnel-zone name $TZ_NAME | awk '{ print $2 }')
if [[ -z "$TZ_ID" ]]; then
    TZ_ID=$(midonet-cli -e create tunnel-zone name default_tz type gre)
    die_if_not_set $LINENO TZ_ID "FAILED to create tunnel zone"
fi
echo "Tunnel Zone: ${TZ_ID}"

# add our host as a member to the tunnel zone
TZ_MEMBER=$(midonet-cli -e tunnel-zone $TUNNEL_ZONE_ID add member \
    host $HOST_ID address 172.19.0.2)
die_if_not_set $LINENO TZ_MEMBER "FAILED to create tunnel zone member"
echo "Added member ${TZ_MEMBER} to the tunnel zone"

# Add a port in the MidoNet Provider Router that will be part of a /30 network
PORT_ID=$(midonet-cli -e router $PROVIDER_ROUTER_ID add \
    port address 172.19.0.2 net 172.19.0.0/30)
die_if_not_set $LINENO PORT_ID "FAILED to create port on provider router"
echo "Created router port ${PORT_ID}"

# Create a route to push all the packets from this end of the /30 network to
# the other end
ROUTE=$(midonet-cli -e router $PROVIDER_ROUTER_ID add route \
    src 0.0.0.0/0 dst 0.0.0.0/0 type normal port router $PROVIDER_ROUTER_ID \
    port $PORT_ID gw 172.19.0.1)
die_if_not_set $LINENO ROUTE "FAILED to create route on provider router"
echo "Created route ${ROUTE}"

# Bind the virtual port to the veth interface
BINDING=$(midonet-cli -e host $HOST_ID add binding \
    port router $PROVIDER_ROUTER_ID port $PORT_ID interface veth1)
die_if_not_set $LINENO BINDING "FAILED to create host binding"
echo "Created binding ${BINDING}"

# Create the veth interfaces
sudo ip link add type veth
sudo ip link set dev veth0 up
sudo ip link set dev veth1 up

# create the linux bridge, give to it an IP address and attach the veth0
# interface
sudo brctl addbr uplinkbridge
sudo brctl addif uplinkbridge veth0
sudo ip addr add 172.19.0.1/30 dev uplinkbridge
sudo ip link set dev uplinkbridge up

# allow ip forwarding
sudo sysctl -w net.ipv4.ip_forward=1

# route packets from physical underlay network to the bridge if the
# destination belongs to the cidr provided
sudo ip route add $CIDR via 172.19.0.2
