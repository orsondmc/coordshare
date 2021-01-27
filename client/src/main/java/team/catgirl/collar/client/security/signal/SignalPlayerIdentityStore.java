package team.catgirl.collar.client.security.signal;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import team.catgirl.collar.client.HomeDirectory;
import team.catgirl.collar.client.security.PlayerIdentityStore;
import team.catgirl.collar.security.PlayerIdentity;
import team.catgirl.collar.security.KeyPair;
import team.catgirl.collar.security.ServerIdentity;
import team.catgirl.collar.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class SignalPlayerIdentityStore implements PlayerIdentityStore {

    private final UUID player;
    private final SignalProtocolStore store;
    private final State state;

    public SignalPlayerIdentityStore(UUID player, SignalProtocolStore store, State state) {
        this.player = player;
        this.store = store;
        this.state = state;
    }

    @Override
    public PlayerIdentity currentIdentity() {
        try {
            IdentityKeyPair identityKeyPair = new IdentityKeyPair(state.identityKeyPair);
            return new PlayerIdentity(player, new KeyPair.PublicKey(identityKeyPair.getPublicKey().getFingerprint(), identityKeyPair.getPublicKey().serialize()), state.registrationId);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("keypair was invalid", e);
        }
    }

    @Override
    public boolean isTrustedIdentity(ServerIdentity identity) {
        return store.isTrustedIdentity(signalProtocolAddressFrom(identity), identityKeyFrom(identity));
    }

    @Override
    public void trustIdentity(ServerIdentity identity) {
        SignalProtocolAddress address = signalProtocolAddressFrom(identity);
        IdentityKey identityKey = identityKeyFrom(identity);
        if (!isTrustedIdentity(identity)) {
            store.saveIdentity(address, identityKey);
        }
    }

    private SignalProtocolAddress signalProtocolAddressFrom(ServerIdentity serverIdentity) {
        return new SignalProtocolAddress(serverIdentity.serverId.toString(), serverIdentity.registrationId);
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
        public final byte[] identityKeyPair;
        public final Integer registrationId;

        public State(byte[] identityKeyPair, Integer registrationId) {
            this.identityKeyPair = identityKeyPair;
            this.registrationId = registrationId;
        }
    }

    public static PlayerIdentityStore from(UUID player, HomeDirectory homeDirectory, BiConsumer<SignedPreKeyRecord, List<PreKeyRecord>> onInstall) throws IOException {
        SignalProtocolStore store = ClientSignalProtocolStore.from(homeDirectory);
        File file = new File(homeDirectory.security(), "identity.json");
        State state;
        if (file.exists()) {
            state = Utils.createObjectMapper().readValue(file, State.class);
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
            state = new State(identityKeyPair.serialize(), registrationId);
            // Save the identity state
            writeState(file, state);
            // fire the on install consumer
            onInstall.accept(signedPreKey, preKeys);
        }
        return new SignalPlayerIdentityStore(player, store, state);
    }

    private static void writeState(File file, State state) throws IOException {
        Utils.createObjectMapper().writeValue(file, state);
    }
}
