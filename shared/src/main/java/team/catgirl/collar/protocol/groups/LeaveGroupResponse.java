package team.catgirl.collar.protocol.groups;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import team.catgirl.collar.api.session.Player;
import team.catgirl.collar.protocol.ProtocolResponse;
import team.catgirl.collar.security.ClientIdentity;
import team.catgirl.collar.security.ServerIdentity;

import java.util.UUID;

/**
 * Sent to all group members whenever someone leaves the group
 */
public final class LeaveGroupResponse extends ProtocolResponse {
    @JsonProperty("groupId")
    public final UUID groupId;
    @JsonProperty("sender")
    public final ClientIdentity sender;
    @JsonProperty("player")
    public final Player player;

    @JsonCreator
    public LeaveGroupResponse(@JsonProperty("identity") ServerIdentity identity,
                              @JsonProperty("groupId") UUID groupId,
                              @JsonProperty("sender") ClientIdentity sender,
                              @JsonProperty("player") Player player) {
        super(identity);
        this.groupId = groupId;
        this.sender = sender;
        this.player = player;
    }
}
