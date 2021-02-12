package team.catgirl.collar.server;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import team.catgirl.collar.api.groups.Group;
import team.catgirl.collar.api.http.HttpException.NotFoundException;
import team.catgirl.collar.api.profiles.PublicProfile;
import team.catgirl.collar.protocol.PacketIO;
import team.catgirl.collar.protocol.ProtocolRequest;
import team.catgirl.collar.protocol.ProtocolResponse;
import team.catgirl.collar.protocol.devices.RegisterDeviceResponse;
import team.catgirl.collar.protocol.identity.IdentifyRequest;
import team.catgirl.collar.protocol.identity.IdentifyResponse;
import team.catgirl.collar.protocol.keepalive.KeepAliveRequest;
import team.catgirl.collar.protocol.keepalive.KeepAliveResponse;
import team.catgirl.collar.protocol.session.FinishSessionHandshakeResponse;
import team.catgirl.collar.protocol.session.FinishSessionRequest;
import team.catgirl.collar.protocol.session.SessionFailedResponse.MojangVerificationFailedResponse;
import team.catgirl.collar.protocol.session.StartSessionRequest;
import team.catgirl.collar.protocol.session.StartSessionResponse;
import team.catgirl.collar.protocol.signal.SendPreKeysRequest;
import team.catgirl.collar.protocol.signal.SendPreKeysResponse;
import team.catgirl.collar.protocol.trust.CheckTrustRelationshipRequest;
import team.catgirl.collar.protocol.trust.CheckTrustRelationshipResponse;
import team.catgirl.collar.protocol.trust.CheckTrustRelationshipResponse.IsTrustedRelationshipResponse;
import team.catgirl.collar.protocol.trust.CheckTrustRelationshipResponse.IsUntrustedRelationshipResponse;
import team.catgirl.collar.security.ClientIdentity;
import team.catgirl.collar.security.ServerIdentity;
import team.catgirl.collar.security.mojang.MinecraftPlayer;
import team.catgirl.collar.security.mojang.MinecraftProtocolEncryption;
import team.catgirl.collar.server.http.RequestContext;
import team.catgirl.collar.server.protocol.BatchProtocolResponse;
import team.catgirl.collar.server.protocol.GroupsProtocolHandler;
import team.catgirl.collar.server.protocol.ProtocolHandler;
import team.catgirl.collar.server.services.groups.GroupService;
import team.catgirl.collar.server.services.profiles.ProfileService.GetProfileRequest;
import team.catgirl.collar.server.session.SessionManager;
import team.catgirl.collar.utils.Utils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebSocket
public class CollarServer {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private final List<ProtocolHandler> protocolHandlers;
    private final BiConsumer<ClientIdentity, MinecraftPlayer> sessionStopped;
    private final Services services;

    public CollarServer(Services services, List<ProtocolHandler> protocolHandlers) {
        this.services = services;
        this.protocolHandlers = protocolHandlers;
        this.sessionStopped = (identity, player) -> protocolHandlers.forEach(protocolHandler -> protocolHandler.onSessionStopped(identity, player, protocolResponse -> {
            send(null, protocolResponse);
        }));
    }

    @OnWebSocketConnect
    public void connected(Session session) throws IOException {
        LOGGER.log(Level.INFO, "New socket connected");
    }

    @OnWebSocketClose
    public void closed(Session session, int statusCode, String reason) {
        LOGGER.log(Level.INFO, "Session closed " + statusCode + " " + reason);
        services.sessions.stopSession(session, reason, null, sessionStopped);
    }

    @OnWebSocketError
    public void onError(Session session, Throwable e) {
        LOGGER.log(Level.SEVERE, "Unrecoverable error", e);
        services.sessions.stopSession(session, "Unrecoverable error", null, sessionStopped);
    }

    @OnWebSocketMessage
    public void message(Session session, InputStream is) throws IOException {
        ProtocolRequest req = read(session, is);
        LOGGER.log(Level.INFO, req.getClass().getSimpleName() + " from " + req.identity);
        ServerIdentity serverIdentity = services.identityStore.getIdentity();
        if (req instanceof KeepAliveRequest) {
            LOGGER.log(Level.INFO, "KeepAliveRequest received. Sending KeepAliveRequest.");
            sendPlain(session, new KeepAliveResponse(serverIdentity));
        } else if (req instanceof IdentifyRequest) {
            IdentifyRequest request = (IdentifyRequest)req;
            if (request.identity == null) {
                LOGGER.log(Level.INFO, "Signaling client to register");
                String token = services.sessions.createDeviceRegistrationToken(session);
                String url = services.urlProvider.deviceVerificationUrl(token);
                sendPlain(session, new RegisterDeviceResponse(serverIdentity, url, token));
            } else {
                PublicProfile profile;
                try {
                    profile = services.profiles.getProfile(RequestContext.SERVER, GetProfileRequest.byId(req.identity.id())).profile.toPublic();
                } catch (NotFoundException e) {
                    LOGGER.log(Level.SEVERE, "Profile " + request.identity.id() + " does not exist but the client thinks it should.");
                    sendPlain(session, new IsUntrustedRelationshipResponse(serverIdentity));
                    services.sessions.stopSession(session, "Identity " + request.identity.id() + " was not found", null, null);
                    return;
                }
                LOGGER.log(Level.INFO, "Profile found for " + req.identity.id());
                sendPlain(session, new IdentifyResponse(serverIdentity, profile));
            }
        } else if (req instanceof SendPreKeysRequest) {
            SendPreKeysRequest request = (SendPreKeysRequest) req;
            services.identityStore.trustIdentity(request);
            SendPreKeysResponse response = services.identityStore.createSendPreKeysResponse();
            sendPlain(session, response);
        } else if (req instanceof StartSessionRequest) {
            LOGGER.log(Level.INFO, "Starting session with " + req.identity);
            StartSessionRequest request = (StartSessionRequest) req;
            sendPlain(session, new StartSessionResponse(serverIdentity, Utils.MINECRAFT_KEYPAIR.getPublic().getEncoded()));
        } else if (req instanceof FinishSessionRequest) {
            LOGGER.log(Level.INFO, "Finishing session handshake with " + req.identity);
            FinishSessionRequest request = (FinishSessionRequest) req;
            if (services.minecraftSessionVerifier.getName().contains("nojang")) {
                services.sessions.identify(session, req.identity, new MinecraftPlayer(request.player.id, request.player.server, request.player.name));
                sendPlain(session, new FinishSessionHandshakeResponse(serverIdentity));
            } else {
                if (request.player.name != null && MinecraftProtocolEncryption.verifyClient(request.player.name, request.player.id)) {
                    services.sessions.identify(session, req.identity, new MinecraftPlayer(request.player.id, request.player.server, request.player.name));
                    sendPlain(session, new FinishSessionHandshakeResponse(serverIdentity));
                } else {
                    sendPlain(session, new MojangVerificationFailedResponse(serverIdentity, ((FinishSessionRequest) req).player));
                    services.sessions.stopSession(session, "Minecraft session invalid", null, sessionStopped);
                }
            }
        } else if (req instanceof CheckTrustRelationshipRequest) {
            LOGGER.log(Level.INFO, "Checking if client/server have a trusted relationship");
            if (services.identityStore.isTrustedIdentity(req.identity)) {
                LOGGER.log(Level.INFO, req.identity + " is trusted. Signaling client to start encryption.");
                CheckTrustRelationshipResponse response = new IsTrustedRelationshipResponse(serverIdentity);
                sendPlain(session, response);
            } else {
                LOGGER.log(Level.INFO, req.identity + " is NOT trusted. Signaling client to restart registration.");
                CheckTrustRelationshipResponse response = new IsUntrustedRelationshipResponse(serverIdentity);
                sendPlain(session, response);
                services.sessions.stopSession(session, req.identity + " identity is not trusted", null, null);
            }
        } else {
            for (ProtocolHandler handler : protocolHandlers) {
                if (handler.handleRequest(this, req, response -> send(session, response))) {
                    break;
                }
            }
        }
    }

    @Nonnull
    public ProtocolRequest read(@Nonnull Session session, @Nonnull InputStream message) {
        PacketIO packetIO = new PacketIO(services.packetMapper, services.identityStore.createCypher());
        try {
            ClientIdentity identity = services.sessions.getIdentity(session).orElse(null);
            return packetIO.decode(identity, message, ProtocolRequest.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void send(Session session, ProtocolResponse resp) {
        if (session != null && !session.isOpen()) {
            return;
        }
        if (resp instanceof BatchProtocolResponse) {
            BatchProtocolResponse batchResponse = (BatchProtocolResponse)resp;
            batchResponse.responses.forEach((response, identity) -> {
                services.sessions.getSession(identity).ifPresent(anotherSession -> {
                    send(anotherSession, response);
                });
            });
        } else {
            PacketIO packetIO = new PacketIO(services.packetMapper, services.identityStore.createCypher());
            byte[] bytes;
            if (services.sessions.isIdentified(session)) {
                try {
                    ClientIdentity identity = services.sessions.getIdentity(session).orElseThrow(() -> new IllegalStateException("Could not find identity"));
                    bytes = packetIO.encodeEncrypted(identity, resp);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                try {
                    bytes = packetIO.encodePlain(resp);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            sendBytes(session, bytes);
        }
    }

    public void sendPlain(@Nonnull Session session, @Nonnull ProtocolResponse resp) {
        if (!session.isOpen()) {
            return;
        }
        PacketIO packetIO = new PacketIO(services.packetMapper, null);
        byte[] bytes;
        try {
            bytes = packetIO.encodePlain(resp);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        sendBytes(session, bytes);
    }

    private void sendBytes(@Nonnull Session session, @Nonnull byte[] bytes) {
        session.getRemote().sendBytesByFuture(ByteBuffer.wrap(bytes));
    }
}
