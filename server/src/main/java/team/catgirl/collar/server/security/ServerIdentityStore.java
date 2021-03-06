package team.catgirl.collar.server.security;

import team.catgirl.collar.protocol.signal.SendPreKeysRequest;
import team.catgirl.collar.protocol.signal.SendPreKeysResponse;
import team.catgirl.collar.security.ClientIdentity;
import team.catgirl.collar.security.ServerIdentity;
import team.catgirl.collar.security.cipher.Cipher;

import java.util.UUID;

public interface ServerIdentityStore {
    /**
     * @return identity of the server
     */
    ServerIdentity getIdentity();

    /**
     * Creates a new identity for a client
     * @param req to create
     */
    void trustIdentity(SendPreKeysRequest req);

    /**
     * Tests if the identity trusted
     * @param identity to test
     * @return trusted or not
     */
    boolean isTrustedIdentity(ClientIdentity identity);

    /**
     * @return creates a new {@link Cipher}
     */
    Cipher createCypher();

    SendPreKeysResponse createSendPreKeysResponse();

    UUID findIdentity(ClientIdentity identity, int deviceId);
}
