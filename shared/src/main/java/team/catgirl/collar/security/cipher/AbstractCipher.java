package team.catgirl.collar.security.cipher;

import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.ratchet.BobSignalProtocolParameters;
import org.whispersystems.libsignal.ratchet.RatchetingSession;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import team.catgirl.collar.security.Identity;
import team.catgirl.collar.io.IO;
import team.catgirl.collar.security.cipher.CipherException.InvalidCipherSessionException;
import team.catgirl.collar.security.cipher.CipherException.UnknownCipherException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public abstract class AbstractCipher implements Cipher {

    private final SignalProtocolStore signalProtocolStore;

    public AbstractCipher(SignalProtocolStore signalProtocolStore) {
        this.signalProtocolStore = signalProtocolStore;
    }

    @Override
    public byte[] crypt(Identity recipient, byte[] bytes) throws CipherException {
        SessionCipher sessionCipher = new SessionCipher(signalProtocolStore, signalProtocolAddressFrom(recipient));
        try {
            CiphertextMessage message = sessionCipher.encrypt(bytes);
            int type;
            if (message instanceof SignalMessage) {
                type = 0;
            } else if (message instanceof PreKeySignalMessage) {
                type = 1;
            } else {
                throw new IllegalStateException("unknown message type " + message.getClass().getName());
            }
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                try (ObjectOutputStream objectStream = new ObjectOutputStream(outputStream)) {
                    objectStream.writeInt(type);
                    objectStream.write(message.serialize());
                }
                return outputStream.toByteArray();
            } catch (Throwable e) {
                throw new UnknownCipherException("Message crypting failed. Recipient " + recipient, e);
            }
        } catch (UntrustedIdentityException e) {
            throw new InvalidCipherSessionException("Identity is untrusted", e);
        }
    }

    @Override
    public byte[] decrypt(Identity sender, byte[] bytes) throws CipherException {
        SignalProtocolAddress remoteAddress = signalProtocolAddressFrom(sender);
        SessionCipher sessionCipher = new SessionCipher(signalProtocolStore, remoteAddress);
        try {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                 ObjectInputStream objectStream = new ObjectInputStream(inputStream)) {
                int type = objectStream.readInt();
                byte[] serialized = IO.toByteArray(objectStream);
                switch (type) {
                    case 0:
                        return sessionCipher.decrypt(new SignalMessage(serialized));
                    case 1:
                        SessionRecord sessionRecord = signalProtocolStore.loadSession(remoteAddress);
                        PreKeySignalMessage message = new PreKeySignalMessage(serialized);
                        ECKeyPair ourSignedPreKey = signalProtocolStore.loadSignedPreKey(message.getSignedPreKeyId()).getKeyPair();
                        BobSignalProtocolParameters.Builder parameters = BobSignalProtocolParameters.newBuilder();
                        parameters.setTheirBaseKey(message.getBaseKey())
                                .setTheirIdentityKey(message.getIdentityKey())
                                .setOurIdentityKey(signalProtocolStore.getIdentityKeyPair())
                                .setOurSignedPreKey(ourSignedPreKey)
                                .setOurRatchetKey(ourSignedPreKey);
                        if (message.getPreKeyId().isPresent() && signalProtocolStore.containsPreKey(message.getPreKeyId().get())) {
                            PreKeyRecord preKeyRecord = signalProtocolStore.loadPreKey(message.getPreKeyId().get());
                            parameters.setOurOneTimePreKey(Optional.of(preKeyRecord.getKeyPair()));
                        } else {
                            parameters.setOurOneTimePreKey(Optional.absent());
                        }
                        parameters.setOurOneTimePreKey(Optional.absent());
                        if (!sessionRecord.isFresh()) sessionRecord.archiveCurrentState();
                        RatchetingSession.initializeSession(sessionRecord.getSessionState(), parameters.create());
                        sessionRecord.getSessionState().setLocalRegistrationId(signalProtocolStore.getLocalRegistrationId());
                        sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());
                        sessionRecord.getSessionState().setAliceBaseKey(message.getBaseKey().serialize());
                        return sessionCipher.decrypt(message);
                    default:
                        throw new UnknownCipherException("unknown message type '" + type + "'");
                }
            }
        } catch (InvalidMessageException e) {
            if (e.getMessage().equals("No valid sessions.")) {
                throw new InvalidCipherSessionException(e.getMessage(), e);
            } else {
                throw new UnknownCipherException(e.getMessage(), e);
            }
        } catch (Throwable e) {
            throw new UnknownCipherException("Problem decrypting message from " + sender, e);
        }
    }

    protected static SignalProtocolAddress signalProtocolAddressFrom(Identity identity) {
        return new SignalProtocolAddress(identity.id().toString(), identity.deviceId());
    }
}
