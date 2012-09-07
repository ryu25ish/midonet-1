/*
 * Copyright 2011 Midokura KK
 */

package com.midokura.midolman.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ReplicatedMap<K, V> {

    private final static Logger log =
         LoggerFactory.getLogger(ReplicatedMap.class);

    /*
     * TODO(pino): don't allow deletes to be lost.
     *
     * Problem with current code is that calling remove on a key only removes
     * the value with the highest sequence number. If the owner of the previous
     * sequence number never saw the most recent sequence number than it won't
     * have removed its value and hence the 'delete' will result in that old
     * value coming back.
     *
     * Typical fix is to implement remove by writing a tombstone (e.g. empty
     * string for value) with a higher sequence number.
     *
     * Unresolved issue: who cleans up the tombstones.
     */

    public interface Watcher<K1, V1> {
        void processChange(K1 key, V1 oldValue, V1 newValue);
    }

    private static class Notification<K1, V1> {
        K1 key;
        V1 oldValue, newValue;
        Notification(K1 k, V1 v1, V1 v2) {
            key = k;
            oldValue = v1;
            newValue = v2;
        }
    }

    private class DirectoryWatcher implements Runnable {
        public void run() {
            if (!running) {
                return;
            }
            Set<String> curPaths = null;
            try {
                curPaths = dir.getChildren("/", this);
            } catch (KeeperException e) {
                log.error("DirectoryWatcher.run", e);
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                log.error("DirectoryWatcher.run", e);
                Thread.currentThread().interrupt();
            }
            List<String> cleanupPaths = new LinkedList<String>();
            Set<K> curKeys = new HashSet<K>();
            Set<Notification<K,V>> notifications =
                    new HashSet<Notification<K,V>>();
            synchronized(ReplicatedMap.this) {
                for (String path : curPaths) {
                    Path p = decodePath(path);
                    curKeys.add(p.key);
                    MapValue mv = localMap.get(p.key);
                    /*
                     * TODO(pino): if (null == mv || mv.version < p.version):
                     * This way of determining the winning value is flawed: if
                     * the controller that writes the most recent value
                     * fails some maps may never see it. They will be
                     * inconsistent with maps that saw the most recent
                     * value. The fix is to choose the winning value
                     * based on the highest version in ZK. Only use the
                     * most recent version this map remembers in order to
                     * decide whether to notify our watchers.
                     */
                    if (null == mv || mv.version < p.version) {
                        localMap.put(p.key,
                                     new MapValue(p.value, p.version, false));
                        if (null == mv) {
                            notifications.add(
                                new Notification<K,V>(p.key, null, p.value));
                        } else {
                            // Remember my obsolete paths and clean them up
                            // later.
                            if (mv.owner)
                                cleanupPaths.add(encodePath(p.key, mv.value,
                                    mv.version));
                            notifications.add(new Notification<K,V>(
                                        p.key, mv.value, p.value));
                        }
                    }
                }
                Set<K> allKeys = new HashSet<K>(localMap.keySet());
                allKeys.removeAll(curKeys);
                // The remaining keys must have been deleted by someone else.
                for (K key : allKeys) {
                    MapValue mv = localMap.remove(key);
                    if (null != mv.value)
                        notifications.add(
                                new Notification<K,V>(key, mv.value, null));
                }
            }
            for (Notification<K,V> notice : notifications)
                notifyWatchers(notice.key, notice.oldValue, notice.newValue);
            // Now clean up any of my paths that have been obsoleted.
            for (String path : cleanupPaths)
                try {
                    dir.delete(path);
                } catch (KeeperException e) {
                    log.error("DirectoryWatcher.run", e);
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    log.error("DirectoryWatcher.run", e);
                    Thread.currentThread().interrupt();
                }
        }
    }

    private Directory dir;
    private boolean running;
    private Map<K, MapValue> localMap;
    private Set<Watcher<K, V>> watchers;
    private DirectoryWatcher myWatcher;

    public ReplicatedMap(Directory dir) {
        this.dir = dir;
        this.running = false;
        this.localMap = new HashMap<K, MapValue>();
        this.watchers = new HashSet<Watcher<K, V>>();
        this.myWatcher = new DirectoryWatcher();
    }

    public void addWatcher(Watcher<K, V> watcher) {
        watchers.add(watcher);
    }

    public void removeWatcher(Watcher<K, V> watcher) {
        watchers.remove(watcher);
    }

    public void start() {
        if (!this.running) {
            this.running = true;
            myWatcher.run();
        }
    }

    public synchronized void stop() {
        this.running = false;
        this.localMap.clear();
    }

    public synchronized V get(K key) {
        MapValue mv = localMap.get(key);
        if (null == mv)
            return null;
        return mv.value;
    }

    public synchronized boolean containsKey(K key) {
        return localMap.containsKey(key);
    }

    public synchronized Map<K, V> getMap() {
        Map<K, V> result = new HashMap<K, V>();
        for (Map.Entry<K, MapValue> entry : localMap.entrySet())
            result.put(entry.getKey(), entry.getValue().value);
        return result;
    }

    public synchronized List<K> getByValue(V value) {
        ArrayList<K> keyList = new ArrayList<K>();
        for (Map.Entry<K, MapValue> entry : localMap.entrySet())
            if (entry.getValue().value.equals(value))
                keyList.add(entry.getKey());
        return keyList;
    }

    private class PutCallback implements DirectoryCallback.Add {
        private K key;
        private V value;
 
        PutCallback(K k, V v) {
            key = k;
            value = v;
        }

        public void onSuccess(Result<String> result) {
            // Get the sequence number added by ZooKeeper.
            Path p = decodePath(result.getData());
            synchronized(ReplicatedMap.this) {
                localMap.put(key, new MapValue(value, p.version, true));
            }
        }

        public void onError(KeeperException ex) {
            log.error("ReplicatedMap Put {} => {} failed: {}",
                      new Object[] { key, value, ex });
        }

        public void onTimeout() {
            log.error("ReplicatedMap Put {} => {} timed out.", key, value);
        }
    }

    public void put(final K key, final V value) {
        dir.asyncAdd(new Path(key, value, 0).encode(false), null,
                     CreateMode.EPHEMERAL_SEQUENTIAL,
                     new PutCallback(key, value));

        // Our notifies for this change are called from the update
        // notification to the DirectoryWatcher after ZK has accepted it.
    }

    public synchronized boolean isKeyOwner(K key) {
        MapValue mv = localMap.get(key);
        if (null == mv)
            return false;
        return mv.owner;
    }

    public V removeIfOwner(K key) throws KeeperException, InterruptedException {
        MapValue mv;
        synchronized(this) {
            mv = localMap.get(key);
            if (null == mv)
                return null;
            if (!mv.owner)
                return null;
            localMap.remove(key);
        }
        notifyWatchers(key, mv.value, null);
        dir.delete(encodePath(key, mv.value, mv.version));
        return mv.value;
    }

    private void notifyWatchers(K key, V oldValue, V newValue) {
        for (Watcher<K, V> watcher : watchers) {
            watcher.processChange(key, oldValue, newValue);
        }
    }

    private class MapValue {
        V value;
        int version;
        boolean owner;

        MapValue(V value, int version, boolean owner) {
            this.value = value;
            this.version = version;
            this.owner = owner;
        }
    }

    private class Path {
        K key;
        V value;
        int version;

        Path(K key, V value, int version) {
            this.key = key;
            this.value = value;
            this.version = version;
        }

        String encode(boolean withVersion) {
            if (withVersion)
                return encodePath(key, value, version);
            else
                return encodePath(key, value);
        }
    }

    private String encodePath(K key, V value, int version) {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append(encodePath(key, value)).append(
                String.format("%010d", version));
        return strBuilder.toString();
    }

    private String encodePath(K key, V value) {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append('/');
        strBuilder.append(encodeKey(key)).append(',');
        strBuilder.append(encodeValue(value)).append(',');
        return strBuilder.toString();
    }

    private Path decodePath(String str) {
        // Need to skip the '/' at the beginning of the path
        if (str.startsWith("/"))
            str = str.substring(1);
        String[] parts = str.split(",");
        Path result = new Path(null, null, 0);
        result.key = decodeKey(parts[0]);
        result.value = decodeValue(parts[1]);
        result.version = Integer.parseInt(parts[2]);
        return result;
    }

    public synchronized boolean containsValue(V address) {
        for (Map.Entry<K, MapValue> entry : localMap.entrySet())
            if (entry.getValue().value.equals(address))
                return true;

        return false;
    }

    // TODO(pino): document that the encoding may not contain ','.
    protected abstract String encodeKey(K key);

    protected abstract K decodeKey(String str);

    protected abstract String encodeValue(V value);

    protected abstract V decodeValue(String str);
}
