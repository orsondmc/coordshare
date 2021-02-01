package team.catgirl.collar.client.security.signal;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import team.catgirl.collar.client.HomeDirectory;
import team.catgirl.collar.client.security.ClientIdentityStore;
import team.catgirl.collar.client.security.IdentityState;
import team.catgirl.collar.protocol.signal.SendPreKeysRequest;
import team.catgirl.collar.protocol.signal.SendPreKeysResponse;
import team.catgirl.collar.security.ClientIdentity;
import team.catgirl.collar.security.Cypher;
import team.catgirl.collar.security.KeyPair;
import team.catgirl.collar.security.ServerIdentity;
import team.catgirl.collar.security.signal.PreKeys;
import team.catgirl.collar.security.signal.SignalCypher;
import team.catgirl.collar.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public final class SignalClientIdentityStore implements ClientIdentityStore {

    private final UUID owner;
    private final ClientSignalProtocolStore store;
    private final State state;
    private final File file;
    private final ReentrantReadWriteLock lock;

    public SignalClientIdentityStore(UUID owner, ClientSignalProtocolStore store, State state, File file) {
        this.owner = owner;
        this.store = store;
        this.state = state;
        this.file = file;
        this.lock = new ReentrantReadWriteLock();
    }

    @Override
    public ClientIdentity currentIdentity() {
        IdentityKeyPair identityKeyPair = this.store.getIdentityKeyPair();
        return new ClientIdentity(owner, new KeyPair.PublicKey(identityKeyPair.getPublicKey().getFingerprint(), identityKeyPair.getPublicKey().serialize()));
    }

    @Override
    public boolean isTrustedIdentity(ServerIdentity identity) {
        return store.isTrustedIdentity(signalProtocolAddressFrom(identity), identityKeyFrom(identity));
    }

    @Override
    public void trustIdentity(SendPreKeysResponse resp) {
        PreKeyBundle bundle;
        try {
            bundle = PreKeys.preKeyBundleFromBytes(resp.preKeyBundle);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SignalProtocolAddress address = signalProtocolAddressFrom(resp.identity);
        store.saveIdentity(address, bundle.getIdentityKey());
        SessionBuilder sessionBuilder = new SessionBuilder(store, address);
        try {
            sessionBuilder.process(bundle);
        } catch (InvalidKeyException | UntrustedIdentityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Cypher createCypher() {
        return new SignalCypher(store);
    }

    @Override
    public void setDeviceId(int deviceId) {
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        try {
            writeLock.lockInterruptibly();
            if (state.deviceId != null) {
                throw new IllegalStateException("deviceId has already been set");
            }
            state.deviceId = deviceId;
            writeState(file, state);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new IllegalStateException("could not save state", e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public SendPreKeysRequest createSendPreKeysRequest() {
        if (state.deviceId == null || state.deviceId < 1) {
            throw new IllegalStateException("deviceId has not been negotated");
        }
        int deviceId;
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        try {
            readLock.lockInterruptibly();
            deviceId = state.deviceId;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            readLock.unlock();
        }
        PreKeyBundle bundle = PreKeys.generate(store, deviceId);
        try {
            return new SendPreKeysRequest(currentIdentity(), PreKeys.preKeyBundleToBytes(bundle));
        } catch (IOException e) {
            throw new IllegalStateException("could not generate PreKeyBundle");
        }
    }

    public void delete() throws IOException {
        store.delete();
    }

    private SignalProtocolAddress signalProtocolAddressFrom(ServerIdentity serverIdentity) {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        try {
            readLock.lockInterruptibly();
            return new SignalProtocolAddress(serverIdentity.id().toString(), state.deviceId);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            readLock.unlock();
        }
    }

    private static IdentityKey identityKeyFrom(ServerIdentity identity) {
        IdentityKey identityKey;
        try {
            identityKey = new IdentityKey(identity.publicKey.key, 0);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("bad key");
        }
        return identityKey;
    }

    private static final class State {
        @JsonProperty("identityKeyPair")
        public final byte[] identityKeyPair;
        @JsonProperty("registrationId")
        public final Integer registrationId;
        @JsonProperty("deviceId")
        public Integer deviceId;

        public State(@JsonProperty("identityKeyPair") byte[] identityKeyPair,
                     @JsonProperty("registrationId") Integer registrationId,
                     @JsonProperty("deviceId") Integer deviceId) {
            this.identityKeyPair = identityKeyPair;
            this.registrationId = registrationId;
            this.deviceId = deviceId;
        }
    }

    @Override
    public void reset() throws IOException {
        store.delete();
    }

    public static boolean hasIdentityStore(HomeDirectory homeDirectory) {
        return IdentityState.exists(homeDirectory);
    }

    public static SignalClientIdentityStore from(UUID profileId, HomeDirectory homeDirectory, Consumer<SignalProtocolStore> onInstall, Consumer<SignalProtocolStore> onReady) {
        ClientSignalProtocolStore store;
        try {
            store = ClientSignalProtocolStore.from(homeDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create identity store", e);
        }
        File file;
        try {
            file = new File(homeDirectory.security(), "identityStore.json");
        } catch (IOException e) {
            throw new IllegalStateException("Could not create identity store", e);
        }
        State state;
        SignalClientIdentityStore clientIdentityStore;
        if (file.exists()) {
            try {
                state = Utils.createObjectMapper().readValue(file, State.class);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read profile store", e);
            }
            clientIdentityStore = new SignalClientIdentityStore(profileId, store, state, file);
            onReady.accept(store);
        } else {
            // Generate the new identity, its prekeys, etc
            IdentityKeyPair identityKeyPair = KeyHelper.generateIdentityKeyPair();
            int registrationId  = KeyHelper.generateRegistrationId(false);
            List<PreKeyRecord> preKeys = KeyHelper.generatePreKeys(0, 500);
            SignedPreKeyRecord signedPreKey;
            try {
                signedPreKey = KeyHelper.generateSignedPreKey(identityKeyPair, 0);
            } catch (InvalidKeyException e) {
                throw new IllegalStateException("problem generating signed preKey", e);
            }
            preKeys.forEach(preKeyRecord -> store.storePreKey(preKeyRecord.getId(), preKeyRecord));
            store.storeSignedPreKey(signedPreKey.getId(), signedPreKey);
            state = new State(identityKeyPair.serialize(), registrationId, null);
            // Save the identity state
            try {
                writeState(file, state);
            } catch (IOException e) {
                throw new IllegalStateException("Could not create new profile store", e);
            }
            // fire the on install consumer
            onInstall.accept(store);
            clientIdentityStore = new SignalClientIdentityStore(profileId, store, state, file);
        }
        return clientIdentityStore;
    }

    private static void writeState(File file, State state) throws IOException {
        Utils.createObjectMapper().writeValue(file, state);
    }
}
