package team.catgirl.collar.client.security;

import team.catgirl.collar.security.PlayerIdentity;
import team.catgirl.collar.security.ServerIdentity;

public interface PlayerIdentityStore {
    /**
     * @return the players identity
     */
    PlayerIdentity currentIdentity();

    /**
     * Tests if the server identity is trusted
     * @param identity to test
     * @return trusted or not
     */
    boolean isTrustedIdentity(ServerIdentity identity);

    /**
     * Trust the server identity
     * @param identity to trust
     */
    void trustIdentity(ServerIdentity identity);
}
