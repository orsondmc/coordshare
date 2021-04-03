package team.catgirl.collar.sdht.impl;

import team.catgirl.collar.sdht.Content;
import team.catgirl.collar.utils.IO;

import java.io.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Used for persisting {@link DefaultDistributedHashTable} state to disk
 */
public final class DHTNamespaceState {

    private static final int VERSION = 1;
    private static final String DHT_EXT = ".dht";

    private final ReentrantLock lock = new ReentrantLock();
    private final File home;

    public DHTNamespaceState(File home) {
        if (home.exists() && !home.isDirectory()) {
            throw new IllegalStateException("home is not a directory");
        }
        if (!home.exists() && !home.mkdirs()) {
            throw new IllegalStateException("could not create " + home);
        }
        this.home = home;
    }

    /**
     * Read DHT from the file system
     * @return dht contents
     */
    public ConcurrentMap<UUID, ConcurrentMap<UUID, CopyOnWriteArraySet<Content>>> read() {
        try {
            lock.lockInterruptibly();
            ConcurrentMap<UUID, ConcurrentMap<UUID, CopyOnWriteArraySet<Content>>> result = new ConcurrentHashMap<>();
            for (String fileName : Objects.requireNonNull(home.list())) {
                if (!fileName.endsWith(DHT_EXT)) {
                    continue;
                }
                UUID ns = UUID.fromString(fileName.substring(0, fileName.indexOf(DHT_EXT)));
                result.put(ns, read(ns));
            }
            return result;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Write DHT to the file system
     * @param dht contents
     */
    public void write(ConcurrentMap<UUID, ConcurrentMap<UUID, CopyOnWriteArraySet<Content>>> dht) {
        try {
            lock.lockInterruptibly();
            dht.forEach(this::write);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    private ConcurrentMap<UUID, CopyOnWriteArraySet<Content>> read(UUID namespaceId) {
        File namespaceFile = getNamespaceFile(namespaceId);
        if (!namespaceFile.exists()) {
            return new ConcurrentHashMap<>();
        }
        try (DataInputStream dataStream = new DataInputStream(new FileInputStream(namespaceFile))) {
            int version = dataStream.readInt();
            if (version != VERSION) {
                throw new IllegalStateException("DHT version " + version + " is too new");
            }
            ConcurrentMap<UUID, CopyOnWriteArraySet<Content>> namespace = new ConcurrentHashMap<>();
            int mapSize = dataStream.readInt();
            for (int m = 0; m < mapSize; m++) {
                HashSet<Content> contents = new HashSet<>();
                UUID contentId = IO.readUUID(dataStream);
                int contentCount = dataStream.readInt();
                for (int c = 0; c < contentCount; c++) {
                    contents.add(new Content(IO.readBytes(dataStream)));
                }
                namespace.put(contentId, new CopyOnWriteArraySet<>(contents));
            }
            return namespace;
        } catch (IOException e) {
            throw new IllegalStateException("could not read namespace " + namespaceId + " from file", e);
        }
    }

    /**
     * Write namespace to file
     * @param namespace to write to file
     * @param contentsMap of the namespace
     */
    private void write(UUID namespace, ConcurrentMap<UUID, CopyOnWriteArraySet<Content>> contentsMap) {
        try (DataOutputStream dataStream = new DataOutputStream(new FileOutputStream(getNamespaceFile(namespace)))) {
            dataStream.writeInt(VERSION);
            dataStream.writeInt(contentsMap.size());
            for (Map.Entry<UUID, CopyOnWriteArraySet<Content>> entry : contentsMap.entrySet()) {
                UUID uuid = entry.getKey();
                CopyOnWriteArraySet<Content> contents = entry.getValue();
                IO.writeUUID(dataStream, uuid);
                dataStream.writeInt(contents.size());
                for (Content content : contents) {
                    IO.writeBytes(dataStream, content.serialize());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not write namespace " + namespace + " to file", e);
        }
    }

    private File getNamespaceFile(UUID namespace) {
        return new File(home, namespace.toString() + DHT_EXT);
    }
}
