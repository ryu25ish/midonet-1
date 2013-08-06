/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package org.midonet.midolman.state;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.midonet.midolman.config.MidolmanConfig;
import org.midonet.midolman.guice.zookeeper.ZKConnectionProvider;
import org.midonet.util.eventloop.Reactor;

public class ZookeeperConnectionWatcher implements ZkConnectionAwareWatcher {

    static final Logger log = LoggerFactory.getLogger(ZookeeperConnectionWatcher.class);

    private ScheduledFuture<?> disconnectHandle;
    private ZkConnection conn = null;
    private long sessionId = 0;
    private List<Runnable> reconnectCallbacks  = new LinkedList<Runnable>();
    private List<Runnable> disconnectCallbacks = new LinkedList<Runnable>();

    @Inject
    @Named(ZKConnectionProvider.DIRECTORY_REACTOR_TAG)
    Reactor reactorLoop;

    @Inject
    MidolmanConfig config;

    @Override
    public void setZkConnection(ZkConnection conn) {
        this.conn = conn;
        this.reconnectCallbacks = new LinkedList<Runnable>();
        this.disconnectCallbacks = new LinkedList<Runnable>();
    }

    @Override
    public synchronized void process(WatchedEvent event) {
        if (event.getState() == Watcher.Event.KeeperState.Disconnected) {
            log.warn("KeeperState is Disconnected, will shutdown in {} " +
                "milliseconds if the connection is not restored.",
                config.getZooKeeperGraceTime());

            disconnectHandle = reactorLoop.schedule(new Runnable() {
                @Override
                public void run() {
                    log.error("have been disconnected for {} milliseconds, " +
                              "so exiting", config.getZooKeeperGraceTime());
                    System.exit(7453);
                }
            }, config.getZooKeeperGraceTime(), TimeUnit.MILLISECONDS);
            submitDisconnectCallbacks();
        }

        if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
            if (conn != null) {
                if (sessionId == 0) {
                    this.sessionId = conn.getZooKeeper().getSessionId();
                } else if (sessionId != conn.getZooKeeper().getSessionId()) {
                    log.warn("Zookeeper connection restored to a new session " +
                            "id (old={} new={}), shutting down",
                            sessionId, conn.getZooKeeper().getSessionId());
                    System.exit(-1);
                }

                log.info("KeeperState is SyncConnected, SessionId={}",
                        conn.getZooKeeper().getSessionId());
            } else {
                log.error("Got ZK connection event but ZkConnection "+
                          "has not been supplied, cannot track sessions");
            }

            submitReconnectCallbacks();

            if (disconnectHandle != null) {
                log.info("canceling shutdown");
                disconnectHandle.cancel(true);
                disconnectHandle = null;
            }
        }

        if (event.getState() == Watcher.Event.KeeperState.Expired) {
            log.warn("KeeperState is Expired, shutdown now");
            System.exit(-1);
        }

        //TODO(abel) should this class process other Zookeeper events?
    }

    private void submitReconnectCallbacks() {
        if (reconnectCallbacks.size() > 0) {
            List<Runnable> callbacks = this.reconnectCallbacks;
            this.reconnectCallbacks = new LinkedList<Runnable>();
            log.info("ZK connection restored, re-issuing {} requests",
                    callbacks.size());
            for (Runnable r: callbacks)
                reactorLoop.submit(r);
        }
    }

    private void submitDisconnectCallbacks() {
        if (disconnectCallbacks.size() > 0) {
            List<Runnable> callbacks = this.disconnectCallbacks;
            this.disconnectCallbacks = new LinkedList<Runnable>();
            log.info("ZK connection lost, firing {} disconnect callbacks",
                     callbacks.size());
            for (Runnable r: callbacks)
                reactorLoop.submit(r);
        }
    }

    @Override
    public void scheduleOnReconnect(Runnable runnable) {
        log.info("scheduling callback on zookeeper reconnection");
        reconnectCallbacks.add(runnable);
    }

    @Override
    public void scheduleOnDisconnect(Runnable runnable) {
        log.info("scheduling callback on zookeeper disconnection");
        disconnectCallbacks.add(runnable);
    }

    /**
     * Handles an error thrown by a cluster operation. There are three possible
     * outcomes:
     *
     *     - The operation is retried. This is done for operations that were
     *       interrupted or timed out.
     *     - The operation is queued in the ZookeeperConnectionWatcher for
     *       resubmission when the Zookeeper connection is restored. This is
     *       done for disconnection errors.
     *     - All other errors are ignored and logged. Their operations will
     *       never be retried and their watchers/callbacks never invoked.
     *
     * @param operationDesc Human-readable string describing the failed operation
     * @param retry A runnable that will retry the operation inside the
     *              the directory reactor.
     * @param e The error thrown by the failed operation.
     */
    @Override
    public void handleError(String operationDesc, Runnable retry,
                            KeeperException e) {
        if (e instanceof KeeperException.ConnectionLossException)
            handleDisconnect(retry);
        else if (e instanceof KeeperException.OperationTimeoutException)
            handleTimeout(retry);
        else if (e instanceof KeeperException.NoNodeException)
            log.warn("Expected a ZK node for {} but not found: {}",
                     operationDesc, e);
        else if (e instanceof KeeperException.SessionExpiredException ||
                 e instanceof KeeperException.SessionMovedException) {
            log.error("Exiting: Non-recoverable error on ZK operation for " +
                      "{} - {}", operationDesc, e);
            System.exit(7453);
        } else {
            log.warn("ZK operation for {} failed: {}", operationDesc, e);
        }
    }

    // TODO(guillermo) There is a bit of an abstraction leak in the ZK exception
    // business. Some classes map them to StateAccessException subclasses, some
    // let KeeperExceptions to pass through their api. We handle both cases here
    // but should eventually choose one or the other.
    @Override
    public void handleError(String operationDesc, Runnable retry,
                            StateAccessException e) {
        Throwable cause = e.getCause();
        if (cause instanceof KeeperException)
            handleError(operationDesc, retry, (KeeperException) cause);
        else if (cause instanceof InterruptedException)
            handleTimeout(retry);
        else
            log.error("Failed state access operation for {} - {}",
                      operationDesc, e);
    }

    @Override
    public void handleTimeout(Runnable runnable) {
        log.info("Resubmitting timed-out zookeeper request");
        reactorLoop.submit(runnable);
    }

    @Override
    public void handleDisconnect(Runnable runnable) {
        scheduleOnReconnect(runnable);
    }
}
