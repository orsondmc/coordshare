package team.catgirl.collar.protocol.groups;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import team.catgirl.collar.api.groups.GroupType;
import team.catgirl.collar.api.session.Player;
import team.catgirl.collar.protocol.ProtocolResponse;
import team.catgirl.collar.security.ServerIdentity;
import team.catgirl.collar.security.mojang.MinecraftPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Sent by the group owner to invite players to an existing group using a {@link GroupInviteRequest}
 */
public final class GroupInviteResponse extends ProtocolResponse {
    @JsonProperty("groupId")
    public final UUID groupId;
    @JsonProperty("groupType")
    public final GroupType groupType;
    @JsonProperty("player")
    public final Player player;
    @JsonProperty("members")
    public final List<Player> members;

    @JsonCreator
    public GroupInviteResponse(
            @JsonProperty("identity") ServerIdentity identity,
            @JsonProperty("groupId") UUID groupId,
            @JsonProperty("groupType") GroupType groupType,
            @JsonProperty("player") Player player,
            @JsonProperty("members") List<Player> members) {
        super(identity);
        this.groupId = groupId;
        this.groupType = groupType;
        this.player = player;
        this.members = members;
    }
}
