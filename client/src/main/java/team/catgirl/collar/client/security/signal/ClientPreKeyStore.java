package team.catgirl.collar.client.security.signal;

import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import team.catgirl.collar.client.HomeDirectory;
import team.catgirl.collar.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ClientPreKeyStore implements PreKeyStore {

    private final File file;
    private final ReentrantReadWriteLock lock;
    private final State state;

    public ClientPreKeyStore(File file, State state) {
        this.file = file;
        this.state = state;
        this.lock = new ReentrantReadWriteLock();
    }

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        try {
            readLock.lockInterruptibly();
            byte[] bytes = state.preKeyRecords.get(preKeyId);
            if (bytes == null || bytes.length == 0) {
                throw new InvalidKeyIdException("no key with id " + preKeyId );
            }
            return new PreKeyRecord(bytes);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new IllegalStateException("could not deserialize key " + preKeyId, e);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        try {
            writeLock.lockInterruptibly();
            state.preKeyRecords.put(preKeyId, record.serialize());
            saveState(file, state);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new IllegalStateException("could not save state", e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        try {
            readLock.lockInterruptibly();
            byte[] bytes = state.preKeyRecords.get(preKeyId);
            return bytes != null && bytes.length != 0;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void removePreKey(int preKeyId) {
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        try {
            writeLock.lockInterruptibly();
            state.preKeyRecords.remove(preKeyId);
            saveState(file, state);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new IllegalStateException("could not save state", e);
        } finally {
            writeLock.unlock();
        }
    }

    public static ClientPreKeyStore from(HomeDirectory home) throws IOException {
        File file = new File(home.security(), "clientPreKeyStore.json");
        State state;
        if (file.exists()) {
            state = Utils.createObjectMapper().readValue(file, State.class);
        } else {
            state = new State(new HashMap<>());
            saveState(file, state);
        }
        return new ClientPreKeyStore(file, state);
    }

    private static void saveState(File file, State state) throws IOException {
        Utils.createObjectMapper().writeValue(file, state);
    }

    private static class State {
        public final Map<Integer, byte[]> preKeyRecords;

        public State(Map<Integer, byte[]> preKeyRecords) {
            this.preKeyRecords = preKeyRecords;
        }
    }
}
