package team.catgirl.collar.client.api.groups;

import team.catgirl.collar.api.groups.Group;
import team.catgirl.collar.api.waypoints.Waypoint;
import team.catgirl.collar.client.Collar;
import team.catgirl.collar.client.api.ApiListener;
import team.catgirl.collar.security.mojang.MinecraftPlayer;

public interface GroupsListener extends ApiListener {
    default void onGroupCreated(Collar collar, GroupsApi groupsApi, Group<Waypoint> group) {};
    default void onGroupJoined(Collar collar, GroupsApi groupsApi, Group<Waypoint> group, MinecraftPlayer player) {};
    default void onGroupLeft(Collar collar, GroupsApi groupsApi, Group<Waypoint> group, MinecraftPlayer player) {};
    default void onGroupInvited(Collar collar, GroupsApi groupsApi, GroupInvitation invitation) {};
    ///
    /// Waypoint listeners
    ///
    default void onWaypointCreated(Collar collar, GroupsApi feature, Group<Waypoint> group, Waypoint waypoint) {};
    default void onWaypointRemoved(Collar collar, GroupsApi feature, Group<Waypoint> group, Waypoint waypoint) {};
}
