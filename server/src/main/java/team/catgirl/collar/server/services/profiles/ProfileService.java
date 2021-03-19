package team.catgirl.collar.server.services.profiles;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.apache.commons.validator.routines.EmailValidator;
import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.types.Binary;
import team.catgirl.collar.api.http.HttpException.BadRequestException;
import team.catgirl.collar.api.http.HttpException.ConflictException;
import team.catgirl.collar.api.http.HttpException.NotFoundException;
import team.catgirl.collar.api.http.HttpException.ServerErrorException;
import team.catgirl.collar.api.profiles.TexturePreference;
import team.catgirl.collar.server.http.RequestContext;
import team.catgirl.collar.server.security.hashing.PasswordHashing;

import java.util.*;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.push;

public class ProfileService {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_PROFILE_ID = "profileId";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_HASHED_PASSWORD = "hashedPassword";
    private static final String FIELD_EMAIL_VERIFIED = "emailVerified";
    private static final String FIELD_PRIVATE_IDENTITY_TOKEN = "privateIdentityToken";
    private static final String FIELD_CAPE_TEXTURE = "capeTexture";
    private static final String FIELD_CAPE_TEXTURE_ID = "texture";
    private static final String FIELD_KNOWN_ACCOUNTS = "knownAccounts";

    private final MongoCollection<Document> docs;
    private final PasswordHashing passwordHashing;

    public ProfileService(MongoDatabase db, PasswordHashing passwordHashing) {
        this.docs = db.getCollection("profiles");
        Map<String, Object> index = new HashMap<>();
        index.put(FIELD_PROFILE_ID, 1);
        index.put(FIELD_EMAIL, 1);
        docs.createIndex(new Document(index));
        this.passwordHashing = passwordHashing;
    }

    public CreateProfileResponse createProfile(RequestContext context, CreateProfileRequest req) {
        context.assertAnonymous();
        if (docs.find(eq(FIELD_EMAIL, req.email)).iterator().hasNext()) {
            throw new ConflictException("profile with this email already exists");
        }
        if (req.name == null) {
            throw new BadRequestException("name missing");
        }
        if (req.email == null) {
            throw new BadRequestException("email missing");
        }
        if (req.password == null) {
            throw new BadRequestException("password missing");
        }
        if (!EmailValidator.getInstance().isValid(req.email)) {
            throw new BadRequestException("email address is invalid");
        }
        String hashedPassword = passwordHashing.hash(req.password);
        Map<String, Object> state = new HashMap<>();
        state.put(FIELD_PROFILE_ID, UUID.randomUUID());
        state.put(FIELD_NAME, req.name);
        state.put(FIELD_EMAIL, req.email.toLowerCase());
        state.put(FIELD_HASHED_PASSWORD, hashedPassword);
        state.put(FIELD_EMAIL_VERIFIED, false);
        InsertOneResult insertOneResult = docs.insertOne(new Document(state));
        if (insertOneResult.wasAcknowledged()) {
            BsonObjectId id = Objects.requireNonNull(insertOneResult.getInsertedId()).asObjectId();
            MongoCursor<Document> cursor = docs.find(eq(FIELD_ID, id.getValue())).iterator();
            if (cursor.hasNext()) {
                return new CreateProfileResponse(map(cursor.next()));
            } else {
                throw new ServerErrorException("could not find created profile");
            }
        } else {
            throw new ServerErrorException("could not create new profile");
        }
    }

    public GetProfileResponse getProfile(RequestContext context, GetProfileRequest req) {
        context.assertNotAnonymous();
        MongoCursor<Document> cursor;
        if (req.byId != null) {
            cursor = docs.find(eq(FIELD_PROFILE_ID, req.byId)).iterator();
        } else if (req.byEmail != null) {
            cursor = docs.find(eq(FIELD_EMAIL, req.byEmail.toLowerCase())).iterator();
        } else {
            throw new BadRequestException("empty request");
        }
        if (!cursor.hasNext()) {
            throw new NotFoundException("profile not found");
        }
        Document doc = cursor.next();
        return new GetProfileResponse(map(doc));
    }

    public UpdateProfileResponse updateProfile(RequestContext context, UpdateProfileRequest req) {
        context.assertNotAnonymous();
        UpdateResult result;
        if (req.emailVerified != null) {
            result = docs.updateOne(eq(FIELD_PROFILE_ID, req.profile), new Document("$set", new Document(FIELD_EMAIL_VERIFIED, req.emailVerified)));
        } else if (req.hashedPassword != null) {
            result = docs.updateOne(eq(FIELD_PROFILE_ID, req.profile), new Document("$set", new Document(FIELD_HASHED_PASSWORD, req.hashedPassword)));
        } else if (req.privateIdentityToken != null) {
            Binary token = req.privateIdentityToken.length == 0 ? null : new Binary(req.privateIdentityToken);
            result = docs.updateOne(eq(FIELD_PROFILE_ID, req.profile), new Document("$set", new Document(FIELD_PRIVATE_IDENTITY_TOKEN, token)));
        } else if (req.cape != null) {
            result = docs.updateOne(eq(FIELD_PROFILE_ID, req.profile), new Document("$set", new Document(FIELD_CAPE_TEXTURE, map(req.cape))));
        } else if (req.addMinecraftAccount != null) {
            result = docs.updateOne(eq(FIELD_PROFILE_ID, req.profile), push(FIELD_KNOWN_ACCOUNTS, req.addMinecraftAccount));
        } else {
            throw new BadRequestException("bad request");
        }
        if (result.wasAcknowledged()) {
            Document first = docs.find(eq(FIELD_PROFILE_ID, req.profile)).first();
            if (first == null) {
                throw new NotFoundException("could not find profile");
            }
            return new UpdateProfileResponse(map(first));
        } else {
            throw new ServerErrorException("could not update profile");
        }
    }

    public static class CreateProfileRequest {
        public final String email;
        public final String password;
        public final String name;

        public CreateProfileRequest(String email, String password, String name) {
            this.email = email;
            this.password = password;
            this.name = name;
        }
    }

    public static class CreateProfileResponse {
        public final Profile profile;

        public CreateProfileResponse(Profile profile) {
            this.profile = profile;
        }
    }

    public static class GetProfileRequest {
        public final UUID byId;
        public final String byEmail;

        public GetProfileRequest(UUID byId, String byEmail) {
            this.byId = byId;
            this.byEmail = byEmail;
        }

        public static GetProfileRequest byId(UUID uuid) {
            return new GetProfileRequest(uuid, null);
        }

        public static GetProfileRequest byEmail(String email) {
            return new GetProfileRequest(null, email);
        }
    }

    public static class GetProfileResponse {
        public final Profile profile;

        public GetProfileResponse(Profile profile) {
            this.profile = profile;
        }
    }

    private static Profile map(Document doc) {
        UUID profileId = doc.get(FIELD_PROFILE_ID, UUID.class);
        String email = doc.getString(FIELD_EMAIL);
        String name = doc.getString(FIELD_NAME);
        String hashedPassword = doc.getString(FIELD_HASHED_PASSWORD);
        boolean emailVerified = doc.getBoolean(FIELD_EMAIL_VERIFIED, false);
        Binary privateIdentityToken = doc.get(FIELD_PRIVATE_IDENTITY_TOKEN, Binary.class);
        TexturePreference capeTexture = mapTexturePreference(doc.get(FIELD_CAPE_TEXTURE, Document.class));
        List<UUID> knownAccounts = doc.getList("knownAccounts", UUID.class);
        return new Profile(
                profileId,
                email,
                name,
                hashedPassword,
                emailVerified,
                privateIdentityToken != null ? privateIdentityToken.getData() : null,
                capeTexture,
                knownAccounts == null ? Set.of() : Set.copyOf(knownAccounts)
        );
    }

    private static TexturePreference mapTexturePreference(Document doc) {
        if (doc == null) {
            return null;
        }
        return new TexturePreference(doc.get(FIELD_CAPE_TEXTURE_ID, UUID.class));
    }

    private Document map(TexturePreference capeTexture) {
        return new Document(Map.of(
            FIELD_CAPE_TEXTURE_ID, capeTexture.texture
        ));
    }

    public static final class UpdateProfileRequest {
        @JsonProperty("profile")
        public final UUID profile;
        @JsonProperty("emailVerified")
        public final Boolean emailVerified;
        @JsonProperty("hashedPassword")
        public final String hashedPassword;
        @JsonProperty("privateIdentityToken")
        public final byte[] privateIdentityToken;
        @JsonProperty("cape")
        public final TexturePreference cape;
        @JsonProperty("addMinecraftAccount")
        public final UUID addMinecraftAccount;

        public UpdateProfileRequest(@JsonProperty("profile") UUID profile,
                                    @JsonProperty("emailVerified") Boolean emailVerified,
                                    @JsonProperty("hashedPassword") String hashedPassword,
                                    @JsonProperty("privateIdentityToken") byte[] privateIdentityToken,
                                    @JsonProperty("cape") TexturePreference cape,
                                    @JsonProperty("addAccount") UUID addMinecraftAccount) {
            this.profile = profile;
            this.emailVerified = emailVerified;
            this.hashedPassword = hashedPassword;
            this.privateIdentityToken = privateIdentityToken;
            this.cape = cape;
            this.addMinecraftAccount = addMinecraftAccount;
        }

        public static UpdateProfileRequest emailVerified(UUID profile) {
            return new UpdateProfileRequest(profile, true, null, null, null, null);
        }

        public static UpdateProfileRequest hashedPassword(UUID profile, String newPassword) {
            return new UpdateProfileRequest(profile, null, newPassword, null, null, null);
        }

        public static UpdateProfileRequest privateIdentityToken(UUID profile, byte[] privateIdentityToken) {
            return new UpdateProfileRequest(profile, null, null, privateIdentityToken, null, null);
        }

        public static UpdateProfileRequest capeTexturePreference(UUID profile, TexturePreference capeTexture) {
            return new UpdateProfileRequest(profile, null, null, null, capeTexture, null);
        }

        public static UpdateProfileRequest addMinecraftAccount(UUID addAccount) {
            return new UpdateProfileRequest(null, null, null, null, null, addAccount);
        }
    }

    public static final class UpdateProfileResponse {
        @JsonProperty("profile")
        public final Profile profile;

        public UpdateProfileResponse(@JsonProperty("profile") Profile profile) {
            this.profile = profile;
        }
    }
}
