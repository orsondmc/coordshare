package team.catgirl.collar.protocol.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import team.catgirl.collar.api.session.Player;
import team.catgirl.collar.protocol.ProtocolResponse;
import team.catgirl.collar.security.ClientIdentity;
import team.catgirl.collar.security.ServerIdentity;

import java.util.UUID;

/**
 * Sent to any client who is listening for the players location
 * For example, members of groups.
 * It is never sent to the sender
 */
public final class LocationUpdatedResponse extends ProtocolResponse {

    @JsonProperty("sender")
    public final ClientIdentity sender;
    @JsonProperty("group")
    public final UUID group;
    @JsonProperty("player")
    public final Player player;
    @JsonProperty("location")
    public final byte[] location;

    public LocationUpdatedResponse(@JsonProperty("identity") ServerIdentity identity,
                                   @JsonProperty("sender") ClientIdentity sender,
                                   @JsonProperty("groupId") UUID group,
                                   @JsonProperty("player") Player player,
                                   @JsonProperty("location") byte[] location) {
        super(identity);
        this.sender = sender;
        this.group = group;
        this.player = player;
        this.location = location;
    }
}
