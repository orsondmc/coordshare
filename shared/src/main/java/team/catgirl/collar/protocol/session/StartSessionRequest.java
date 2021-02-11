package team.catgirl.collar.protocol.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import team.catgirl.collar.protocol.ProtocolRequest;
import team.catgirl.collar.security.ClientIdentity;
import team.catgirl.collar.security.mojang.MinecraftSession;

public final class StartSessionRequest extends ProtocolRequest {
    @JsonCreator
    public StartSessionRequest(@JsonProperty("identity") ClientIdentity identity) {
        super(identity);
    }
}
