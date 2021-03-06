package team.catgirl.collar.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import team.catgirl.collar.api.authentication.AuthenticationService;
import team.catgirl.collar.api.profiles.ProfileService;
import team.catgirl.collar.server.configuration.Configuration;
import team.catgirl.collar.server.http.AppUrlProvider;
import team.catgirl.collar.server.security.ServerIdentityStore;
import team.catgirl.collar.server.security.hashing.PasswordHashing;
import team.catgirl.collar.server.security.mojang.MinecraftSessionVerifier;
import team.catgirl.collar.server.security.signal.SignalServerIdentityStore;
import team.catgirl.collar.server.services.authentication.ServerAuthenticationService;
import team.catgirl.collar.server.services.authentication.TokenCrypter;
import team.catgirl.collar.server.services.devices.DeviceService;
import team.catgirl.collar.server.services.friends.FriendsService;
import team.catgirl.collar.server.services.groups.GroupService;
import team.catgirl.collar.server.services.groups.GroupStore;
import team.catgirl.collar.server.services.location.PlayerLocationService;
import team.catgirl.collar.server.services.location.WaypointService;
import team.catgirl.collar.server.services.profiles.ProfileCache;
import team.catgirl.collar.server.services.profiles.ProfileServiceServer;
import team.catgirl.collar.server.services.profiles.storage.ProfileStorage;
import team.catgirl.collar.server.services.textures.TextureService;
import team.catgirl.collar.server.session.DeviceRegistrationService;
import team.catgirl.collar.server.session.SessionManager;
import team.catgirl.collar.utils.Utils;

public final class Services {
    public final ObjectMapper jsonMapper;
    public final ObjectMapper packetMapper;
    public final AppUrlProvider urlProvider;
    public final ServerIdentityStore identityStore;
    public final SessionManager sessions;
    public final PasswordHashing passwordHashing;
    public final ProfileService profiles;
    public final ProfileStorage profileStorage;
    public final DeviceService devices;
    public final TokenCrypter tokenCrypter;
    public final AuthenticationService auth;
    public final MinecraftSessionVerifier minecraftSessionVerifier;
    public final GroupService groups;
    public final GroupStore groupStore;
    public final PlayerLocationService playerLocations;
    public final TextureService textures;
    public final FriendsService friends;
    public final WaypointService waypoints;
    public final DeviceRegistrationService deviceRegistration;
    public final ProfileCache profileCache;

    public Services(Configuration configuration) {
        this.jsonMapper = Utils.jsonMapper();
        this.packetMapper = Utils.messagePackMapper();
        this.urlProvider = configuration.appUrlProvider;
        this.identityStore = new SignalServerIdentityStore(configuration.database);
        this.sessions = new SessionManager(packetMapper, identityStore);
        this.deviceRegistration = new DeviceRegistrationService(sessions);
        this.passwordHashing = configuration.passwordHashing;
        this.profiles = new ProfileServiceServer(configuration.database, passwordHashing);
        this.profileCache = new ProfileCache(profiles);
        this.profileStorage = new ProfileStorage(configuration.database);
        this.devices = new DeviceService(configuration.database);
        this.tokenCrypter = configuration.tokenCrypter;
        this.auth = new ServerAuthenticationService(profiles, passwordHashing, tokenCrypter, configuration.email, urlProvider);
        this.minecraftSessionVerifier = configuration.minecraftSessionVerifier;
        this.groupStore = new GroupStore(profileCache, sessions, configuration.database);
        this.groups = new GroupService(groupStore, identityStore.getIdentity(), profileCache, sessions);
        this.playerLocations = new PlayerLocationService(sessions, profileCache, groups, identityStore.getIdentity());
        this.textures = new TextureService(configuration.database);
        this.friends = new FriendsService(configuration.database, profileCache, sessions);
        this.waypoints = new WaypointService(profileStorage);
    }
}
