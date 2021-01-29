package team.catgirl.collar.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoDatabase;
import spark.ModelAndView;
import spark.Request;
import team.catgirl.collar.http.ServerStatusResponse;
import team.catgirl.collar.profiles.PublicProfile;
import team.catgirl.collar.server.common.ServerVersion;
import team.catgirl.collar.server.http.*;
import team.catgirl.collar.server.http.HttpException.UnauthorisedException;
import team.catgirl.collar.server.mongo.Mongo;
import team.catgirl.collar.server.security.ServerIdentityStore;
import team.catgirl.collar.server.security.hashing.PasswordHashing;
import team.catgirl.collar.server.security.signal.SignalServerIdentityStore;
import team.catgirl.collar.server.services.authentication.AuthenticationService;
import team.catgirl.collar.server.services.authentication.AuthenticationService.CreateAccountRequest;
import team.catgirl.collar.server.services.authentication.AuthenticationService.LoginRequest;
import team.catgirl.collar.server.services.authentication.TokenCrypter;
import team.catgirl.collar.server.services.devices.DeviceService;
import team.catgirl.collar.server.services.devices.DeviceService.FindDevicesRequest;
import team.catgirl.collar.server.services.groups.GroupService;
import team.catgirl.collar.server.services.profiles.Profile;
import team.catgirl.collar.server.services.profiles.ProfileService;
import team.catgirl.collar.server.services.profiles.ProfileService.GetProfileRequest;
import team.catgirl.collar.utils.Utils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spark.Spark.*;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        String portValue = System.getenv("PORT");
        if (portValue != null) {
            port(Integer.parseInt(portValue));
        } else {
            port(3000);
        }

        LOGGER.info("Reticulating splines...");

        // Services
        MongoDatabase db = Mongo.database();

        ObjectMapper mapper = Utils.createObjectMapper();
        SessionManager sessions = new SessionManager(mapper);
        ServerIdentityStore serverIdentityStore = new SignalServerIdentityStore(db);
        ProfileService profiles = new ProfileService(db);
        DeviceService devices = new DeviceService(db);
        // TODO: pass this in as configuration
        TokenCrypter tokenCrypter = new TokenCrypter("mycoolpassword");
        PasswordHashing passwordHashing = new PasswordHashing();
        AuthenticationService auth = new AuthenticationService(profiles, passwordHashing, tokenCrypter);

        // Collar feature services
        GroupService groups = new GroupService(serverIdentityStore.getIdentity(), sessions);

        // Always serialize objects returned as JSON
        defaultResponseTransformer(mapper::writeValueAsString);
        exception(HttpException.class, (e, request, response) -> {
            response.status(e.code);
            response.body(e.getMessage());
            LOGGER.log(Level.SEVERE, request.pathInfo(), e);
        });

        exception(Exception.class, (e, request, response) -> {
            response.status(500);
            response.body(e.getMessage());
            LOGGER.log(Level.SEVERE, request.pathInfo(), e);
        });

        // Setup WebSockets
        webSocketIdleTimeoutMillis((int) TimeUnit.SECONDS.toMillis(60));

        // WebSocket server
        webSocket("/api/1/listen", new Collar(mapper, sessions, groups, serverIdentityStore));

        // API routes
        path("/api", () -> {
            // Version 1
            path("/1", () -> {

                before("/*", (request, response) -> {
                    setupRequest(tokenCrypter, request);
                });

                // Used to test if API is available
                get("/", (request, response) -> new ServerStatusResponse("OK"));

                path("/profile", () -> {
                    before("/*", (request, response) -> {
                        RequestContext.from(request).assertIsUser();
                    });
                    // Get your own profile
                    get("/me", (request, response) -> {
                        RequestContext context = RequestContext.from(request);
                        return profiles.getProfile(context, GetProfileRequest.byId(context.profileId)).profile;
                    });
                    // Get someone elses profile
                    get("/:id", (request, response) -> {
                        String id = request.params("id");
                        UUID uuid = UUID.fromString(id);
                        return profiles.getProfile(RequestContext.from(request), GetProfileRequest.byId(uuid)).profile.toPublic();
                    });
                    get("/devices", (request, response) -> {
                        return devices.findDevices(RequestContext.from(request), mapper.readValue(request.bodyAsBytes(), FindDevicesRequest.class));
                    });
                    delete("/devices/:id", (request, response) -> {
                        RequestContext context = RequestContext.from(request);
                        String deviceId = request.params("id");
                        return devices.deleteDevice(context, new DeviceService.DeleteDeviceRequest(context.profileId, Integer.parseInt(deviceId)));
                    });
                });

                path("/auth", () -> {
                    before("/*", (request, response) -> {
                        RequestContext.from(request).assertAnonymous();
                    });
                    // Login
                    get("/login", (request, response) -> {
                        LoginRequest req = mapper.readValue(request.bodyAsBytes(), LoginRequest.class);
                        return auth.login(RequestContext.from(request), req);
                    });
                    // Create an account
                    get("/create", (request, response) -> {
                        CreateAccountRequest req = mapper.readValue(request.bodyAsBytes(), CreateAccountRequest.class);
                        return auth.createAccount(RequestContext.from(request), req);
                    });
                });
            });
        });

        // Reports server version
        // This contract is forever, please change with care!
        get("/api/version", (request, response) -> ServerVersion.version());
        // Query this route to discover what version of the APIs are supported
        get("/api/discover", (request, response) -> {
            List<Integer> apiVersions = new ArrayList<>();
            apiVersions.add(1);
            return apiVersions;
        });

        // App Endpoints
        get("/", (request, response) -> {
            response.redirect("/app/login");
            return "";
        });
        path("/app", () -> {
            before("/*", (request, response) -> {
                response.header("Content-Type", "text/html; charset=UTF-8");
            });
            get("/login", (request, response) -> {
                Cookie cookie = Cookie.from(tokenCrypter, request);
                if (cookie == null) {
                    return render("login");
                } else {
                    response.redirect("/app");
                    return "";
                }
            });
            post("/login", (request, response) -> {
                return "";
            });
            get("/logout", (request, response) -> {
                Cookie.remove(response);
                response.redirect("/app/login");
                return "";
            });
            get("/signup", (request, response) -> {
                return render("signup");
            });
            post("/signup", (request, response) -> {
                String name = request.queryParamsSafe("name");
                String email = request.queryParamsSafe("email");
                String password = request.queryParamsSafe("password");
                String confirmPassword = request.queryParamsSafe("confirmPassword");
                PublicProfile profile = auth.createAccount(RequestContext.ANON, new CreateAccountRequest(email, name, password, confirmPassword)).profile;
                Cookie cookie = new Cookie(profile.id, new Date().getTime() * TimeUnit.DAYS.toMillis(1));
                cookie.set(tokenCrypter, response);
                response.redirect("/app");
                return "";
            });
            get("/", (request, response) -> {
                Cookie cookie = Cookie.from(tokenCrypter, request);
                if (cookie == null) {
                    response.redirect("/app/login");
                    return "";
                } else {

                    Profile profile = profiles.getProfile(new RequestContext(cookie.profileId), GetProfileRequest.byId(cookie.profileId)).profile;

                    Map<String, Object> ctx = new HashMap<>();
                    ctx.put("name", profile.name);

                    return render(ctx,"home");
                }
            });
        });

        LOGGER.info("Collar server started. Do you want to play a block game game?");
    }

    public static String render(Map<String, Object> context, String templatePath) {
        return new HandlebarsTemplateEngine("/templates", false).render(new ModelAndView(context, templatePath));
    }

    public static String render(String templatePath) {
        return render(new HashMap<>(), templatePath);
    }

    /**
     * @param request http request
     * @throws IOException on token decoding
     */
    private static void setupRequest(TokenCrypter crypter, Request request) throws IOException {
        String authorization = request.headers("Authorization");
        RequestContext context;
        if (authorization == null) {
            context = RequestContext.ANON;
        } else if (authorization.startsWith("Bearer ")) {
            String tokenString = authorization.substring(authorization.lastIndexOf(" "));
            AuthToken token = AuthToken.deserialize(crypter, tokenString);
            context = token.fromToken();
        } else {
            throw new UnauthorisedException("bad authorization header");
        }
        request.attribute("requestContext", context);
    }

}
