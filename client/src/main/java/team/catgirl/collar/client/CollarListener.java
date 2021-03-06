package team.catgirl.collar.client;

import team.catgirl.collar.client.Collar.State;
import team.catgirl.collar.client.security.ClientIdentityStore;
import team.catgirl.collar.security.mojang.MinecraftSession;

public interface CollarListener {
    /**
     * Fired on new installation, to confirm that the device is trusted with an authorized collar user
     * @param collar client
     * @param token the approval token
     * @param approvalUrl for the user to follow and approve installation on the collar server website
     */
    default void onConfirmDeviceRegistration(Collar collar, String token, String approvalUrl) {}

    /**
     * Fired when the state of the client changes
     * @param collar client
     * @param state of the client connection
     */
    default void onStateChanged(Collar collar, State state) {}

    /**
     * Fired when the server or client cannot negotiate trust with the client
     * When this occurs, reset the store using {@link ClientIdentityStore#reset()} and reconnect.
     * @param collar client
     * @param store identity store to be reset
     */
    default void onClientUntrusted(Collar collar, ClientIdentityStore store) {}

    /**
     * Fired when the server could not validate the minecraft session
     * @param collar client
     * @param session of minecraft client
     */
    default void onMinecraftAccountVerificationFailed(Collar collar, MinecraftSession session) {}

    /**
     * Fired when the server detects a mismatch between the stored private identity and the one provided by the session
     * User will have to visit a page on the collar web app to confirm that they want to delete encrypted blobs stored
     * against their user or provide the identity file and start again
     * @param collar client
     * @param url to visit
     */
    default void onPrivateIdentityMismatch(Collar collar, String url) {}

    /**
     * Fired whenever Collar encounters an unrecoverable error. Used for debugging.
     * @param collar client
     * @param throwable to log
     */
    default void onError(Collar collar, Throwable throwable) {}
}
