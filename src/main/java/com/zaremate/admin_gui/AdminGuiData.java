package com.zaremate.admin_gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminGuiData {
    private AdminGuiData() {}

    public static String buildPlayerList(MinecraftServer server) {
        JsonObject root = new JsonObject();
        JsonArray players = new JsonArray();
        Map<UUID, String> names = collectPlayers(server);

        names.entrySet().stream()
                .sorted((a, b) -> {
                    boolean ao = server.getPlayerList().getPlayer(a.getKey()) != null;
                    boolean bo = server.getPlayerList().getPlayer(b.getKey()) != null;
                    if (ao != bo) return ao ? -1 : 1;
                    return a.getValue().compareToIgnoreCase(b.getValue());
                })
                .forEach(e -> {
                    JsonObject p = new JsonObject();
                    p.addProperty("uuid", e.getKey().toString());
                    p.addProperty("name", e.getValue());
                    p.addProperty("online", server.getPlayerList().getPlayer(e.getKey()) != null);
                    players.add(p);
                });

        root.add("players", players);
        root.addProperty("serverPlayers", server.getPlayerList().getPlayerCount());
        AdminGui.LOGGER.info("Detail diagnostics for {}: {}", uuid, debug);
        return root.toString();
    }

    public static String buildPlayerDetail(MinecraftServer server, UUID uuid) {
        return buildPlayerDetail(server, uuid, null);
    }

    public static String buildPlayerDetail(
            MinecraftServer server,
            UUID uuid,
            UUID viewerUuid
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("uuid", uuid.toString());

        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        String name = online != null ? online.getGameProfile().getName() : resolveName(server, uuid);
        root.addProperty("name", name);
        root.addProperty("online", online != null);

        JsonObject identity = new JsonObject();
        identity.addProperty("uuid", uuid.toString());
        identity.addProperty("name", name);
        identity.addProperty("online", online != null);
        root.add("identity", identity);

        JsonObject debug = new JsonObject();
        root.add("debug", debug);
        debug.addProperty("tsa.classPresent", classPresent("com.zaremate.tsa_anticheat.api.TsaAnticheatAPI"));
        debug.addProperty("ass.classPresent", classPresent("com.zaremate.airport_security_system.AirportSecuritySystemAPI"));
        debug.addProperty("teams.classPresent", classPresent("com.zaremate.ftb_teams_util.FTBTeamsUtilAPI"));
        debug.addProperty("discord.classPresent", classPresent("com.zaremate.discordlink.DiscordLinkAPI"));
        debug.addProperty("clockin.classPresent", classPresent("com.zaremate.clockin.ClockInMod"));

        root.add("tsa", tsa(uuid, debug));
        root.add("ass", ass(uuid, debug));
        root.addProperty("notesAvailable", classAvailable("com.zaremate.admin_notes.AdminNotesAPI"));
        root.add("notes", notes(uuid, viewerUuid));
        root.add("teams", teams(uuid, debug));
        root.add("discord", discord(uuid, debug));
        root.add("clockin", clockin(uuid, debug));

        return root.toString();
    }

    private static Map<UUID, String> collectPlayers(MinecraftServer server) {
        Map<UUID, String> result = new LinkedHashMap<>();
        try {
            for (Object info : server.getProfileCache().load()) {
                Object profile = info.getClass().getMethod("getProfile").invoke(info);
                if (profile != null) {
                    UUID id = (UUID) profile.getClass().getMethod("getId").invoke(profile);
                    String name = String.valueOf(profile.getClass().getMethod("getName").invoke(profile));
                    if (id != null && name != null && !name.isBlank()) result.putIfAbsent(id, name);
                }
            }
        } catch (Throwable ignored) {}

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            result.put(p.getUUID(), p.getGameProfile().getName());
        }

        for (UUID u : reflectedUuids("com.zaremate.admin_notes.AdminNotesAPI", "getPlayers")) {
            result.putIfAbsent(u, resolveName(server, u));
        }
        for (UUID u : recordUuids("com.zaremate.airport_security_system.AirportSecuritySystemAPI", "getPlayerOffenses", "playerUuid")) {
            result.putIfAbsent(u, resolveName(server, u));
        }
        for (UUID u : recordUuids("com.zaremate.tsa_anticheat.api.TsaAnticheatAPI", "getPlayers", "playerUuid")) {
            result.putIfAbsent(u, resolveName(server, u));
        }
        for (UUID u : clockinPlayers()) {
            result.putIfAbsent(u, resolveName(server, u));
        }

        return result;
    }

    private static MinecraftServer serverForReflection() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    private static String resolveName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();

        try {
            Optional<?> profile = server.getProfileCache().get(uuid);
            if (profile.isPresent()) {
                Object gp = profile.get();
                Object n = gp.getClass().getMethod("getName").invoke(gp);
                if (n != null && !n.toString().isBlank()) return n.toString();
            }
        } catch (Throwable ignored) {}

        String n = stringRecordAccessor(
                "com.zaremate.tsa_anticheat.api.TsaAnticheatAPI",
                "getPlayer", uuid, "playerName");
        if (!n.isBlank()) return n;

        n = stringRecordAccessor(
                "com.zaremate.airport_security_system.AirportSecuritySystemAPI",
                "getPlayerOffense", uuid, "playerName");
        if (!n.isBlank()) return n;

        return uuid.toString();
    }

    private static JsonObject tsa(UUID uuid, JsonObject debug) {
        JsonObject o = new JsonObject();
        try {
            Class<?> c = Class.forName("com.zaremate.tsa_anticheat.api.TsaAnticheatAPI");
            debug.addProperty("tsa.class", c.getName());
            Object value = c.getMethod("getPlayer", UUID.class).invoke(null, uuid);
            debug.addProperty("tsa.getPlayer", value == null ? "null" : value.getClass().getName());

            if (!(value instanceof Optional<?> opt)) {
                debug.addProperty("tsa.result", "not Optional");
                return o;
            }
            if (opt.isEmpty()) {
                debug.addProperty("tsa.result", "empty");
                return o;
            }

            Object r = opt.get();
            debug.addProperty("tsa.result", "record " + r.getClass().getName());
            o.addProperty("playerName", recordString(r, "playerName"));
            o.addProperty("packetChecks", recordLong(r, "packetChecks"));
            o.addProperty("packetPasses", recordLong(r, "packetPasses"));
            o.addProperty("packetModified", recordLong(r, "packetModified"));
            o.addProperty("packetTimeout", recordLong(r, "packetTimeout"));
            o.addProperty("lastPacketStatus", recordString(r, "lastPacketStatus"));
            o.addProperty("lastPacketDate", recordString(r, "lastPacketDate"));
            JsonArray d = new JsonArray();
            Object valueDetections = recordAccessor(r, "detections");
            if (valueDetections instanceof Iterable<?> it) {
                for (Object x : it) d.add(String.valueOf(x));
            }
            o.add("detections", d);
            o.addProperty("available", true);
        } catch (Throwable ex) {
            Throwable cause = rootCause(ex);
            String msg = cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
            debug.addProperty("tsa.error", msg);
            AdminGui.LOGGER.warn("Admin GUI TSA API failed for {}", uuid, cause);
        }
        return o;
    }


    private static JsonObject ass(UUID uuid, JsonObject debug) {
        JsonObject o = new JsonObject();
        try {
            Class<?> c = Class.forName("com.zaremate.airport_security_system.AirportSecuritySystemAPI");
            debug.addProperty("ass.class", c.getName());
            Object value = c.getMethod("getPlayerOffense", UUID.class).invoke(null, uuid);
            debug.addProperty("ass.getPlayerOffense", value == null ? "null" : value.getClass().getName());

            if (!(value instanceof Optional<?> opt)) {
                debug.addProperty("ass.result", "not Optional");
                return o;
            }
            if (opt.isEmpty()) {
                debug.addProperty("ass.result", "empty");
                return o;
            }

            Object r = opt.get();
            debug.addProperty("ass.result", "record " + r.getClass().getName());
            o.addProperty("playerName", recordString(r, "playerName"));
            o.addProperty("status", recordString(r, "status"));
            o.addProperty("clearedDate", recordString(r, "clearedDate"));
            o.addProperty("totalChecks", recordLong(r, "totalChecks"));
            o.addProperty("cleanChecks", recordLong(r, "cleanChecks"));
            o.addProperty("detectedChecks", recordLong(r, "detectedChecks"));
            o.addProperty("inconclusiveChecks", recordLong(r, "inconclusiveChecks"));
            o.add("detectionDates", mapToJson(recordAccessor(r, "detectionDates")));
            o.add("detectionCounts", mapToJson(recordAccessor(r, "detectionCounts")));
            o.addProperty("available", true);
        } catch (Throwable ex) {
            Throwable cause = rootCause(ex);
            String msg = cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
            debug.addProperty("ass.error", msg);
            AdminGui.LOGGER.warn("Admin GUI ASS API failed for {}", uuid, cause);
        }
        return o;
    }


    private static boolean classPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean classAvailable(String name) {
        try { Class.forName(name); return true; } catch (Throwable ignored) { return false; }
    }

    private static JsonArray notes(UUID uuid, UUID viewerUuid) {
        JsonArray result = new JsonArray();
        try {
            Class<?> c = Class.forName("com.zaremate.admin_notes.AdminNotesAPI");
            Method m = c.getMethod("getNotes", UUID.class);
            Object value = m.invoke(null, uuid);
            if (value instanceof Iterable<?> it) {
                for (Object note : it) {
                    JsonObject n = new JsonObject();
                    n.addProperty("id", recordString(note, "id"));
                    String authorUuid = recordString(note, "authorUuid");
                    n.addProperty("authorUuid", authorUuid);
                    n.addProperty("author", recordString(note, "author"));
                    n.addProperty(
                            "canEdit",
                            viewerUuid != null
                                    && viewerUuid.toString().equalsIgnoreCase(authorUuid)
                                    && !Boolean.TRUE.equals(recordAccessor(note, "isSystem"))
                    );
                    n.addProperty("text", recordString(note, "text"));
                    n.addProperty("createdAt", recordLong(note, "createdAt"));
                    n.addProperty("system", Boolean.TRUE.equals(recordAccessor(note, "isSystem")));
                    result.add(n);
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    static boolean canEditNote(
            UUID targetPlayerUuid,
            UUID noteId,
            UUID editorUuid
    ) {
        if (targetPlayerUuid == null || noteId == null || editorUuid == null) {
            return false;
        }

        try {
            Class<?> c = Class.forName("com.zaremate.admin_notes.AdminNotesAPI");
            Method m = c.getMethod("getNote", UUID.class, UUID.class);
            Object value = m.invoke(null, targetPlayerUuid, noteId);

            if (!(value instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }

            Object note = optional.get();
            if (Boolean.TRUE.equals(recordAccessor(note, "isSystem"))) {
                return false;
            }

            Object owner = recordAccessor(note, "authorUuid");
            return owner instanceof UUID ownerUuid
                    && ownerUuid.equals(editorUuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static JsonObject teams(UUID uuid, JsonObject debug) {
        JsonObject o = new JsonObject();
        try {
            Class<?> c = Class.forName("com.zaremate.ftb_teams_util.FTBTeamsUtilAPI");
            debug.addProperty("teams.class", c.getName());
            Object value = c.getMethod("getTeam", UUID.class).invoke(null, uuid);
            debug.addProperty("teams.getTeam", value == null ? "null" : value.getClass().getName());

            if (!(value instanceof Optional<?> opt)) {
                debug.addProperty("teams.result", "not Optional");
                return o;
            }
            if (opt.isEmpty()) {
                debug.addProperty("teams.result", "empty");
                return o;
            }

            Object team = opt.get();
            debug.addProperty("teams.result", "team " + team.getClass().getName());
            o.addProperty("available", true);
            o.addProperty("id", firstString(team, "getTeamId", "getId", "getTeamID"));
            o.addProperty("name", firstString(team, "getName", "getTeamName"));

            JsonArray members = new JsonArray();
            Object raw = firstObject(team, "getMembers");
            debug.addProperty("teams.membersType", raw == null ? "null" : raw.getClass().getName());

            if (raw instanceof Iterable<?> it) {
                for (Object valueMember : it) {
                    if (!(valueMember instanceof UUID memberUuid)) continue;

                    JsonObject member = new JsonObject();
                    member.addProperty("uuid", memberUuid.toString());
                    String memberName = resolveName(serverForReflection(), memberUuid);
                    member.addProperty("name", memberName);

                    Object rank = firstObjectWithArgs(
                            team, new Class<?>[]{UUID.class}, new Object[]{memberUuid},
                            "getRankForPlayer"
                    );
                    String rankName = rank == null ? "NONE" : String.valueOf(rank);
                    String rankDisplay = rankName;

                    if (rank != null) {
                        Object display = firstObject(rank, "getDisplayName");
                        if (display instanceof net.minecraft.network.chat.Component component) {
                            rankDisplay = component.getString();
                        }
                    }

                    member.addProperty("rank", rankName);
                    member.addProperty("rankDisplay", rankDisplay);
                    members.add(member);
                }
            }
            o.add("members", members);
            debug.addProperty("teams.memberCount", members.size());
        } catch (Throwable ex) {
            Throwable cause = rootCause(ex);
            String msg = cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
            debug.addProperty("teams.error", msg);
            AdminGui.LOGGER.warn("Admin GUI FTB Teams API failed for {}", uuid, cause);
        }
        return o;
    }


    private static JsonObject discord(UUID uuid, JsonObject debug) {
        JsonObject o = new JsonObject();
        try {
            Class<?> c = Class.forName("com.zaremate.discordlink.DiscordLinkAPI");
            debug.addProperty("discord.class", c.getName());
            Object value = c.getMethod("getPlayerLink", UUID.class).invoke(null, uuid);
            debug.addProperty("discord.getPlayerLink", value == null ? "null" : value.getClass().getName());

            if (!(value instanceof Optional<?> opt)) {
                debug.addProperty("discord.result", "not Optional");
                return o;
            }
            if (opt.isEmpty()) {
                debug.addProperty("discord.result", "empty");
                return o;
            }

            Object r = opt.get();
            debug.addProperty("discord.result", "record " + r.getClass().getName());
            o.addProperty("available", true);
            o.addProperty("discordId", recordString(r, "discordId"));
            o.addProperty("discordTag", recordString(r, "discordTag"));
            o.addProperty("displayName", recordString(r, "displayName"));
            o.addProperty("linkedAt", recordLong(r, "linkedAt"));
            Object rewarded = recordAccessor(r, "rewarded");
            o.addProperty("rewarded", rewarded instanceof Boolean b && b);
        } catch (Throwable ex) {
            Throwable cause = rootCause(ex);
            String msg = cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
            debug.addProperty("discord.error", msg);
            AdminGui.LOGGER.warn("Admin GUI Discord API failed for {}", uuid, cause);
        }
        return o;
    }


    private static JsonObject clockin(UUID uuid, JsonObject debug) {
        JsonObject o = new JsonObject();
        try {
            Class<?> c = Class.forName("com.zaremate.clockin.ClockInMod");
            debug.addProperty("clockin.class", c.getName());

            Field f = c.getDeclaredField("PLAYERS");
            f.setAccessible(true);
            Object map = f.get(null);
            debug.addProperty("clockin.PLAYERS", map == null ? "null" : map.getClass().getName());

            if (!(map instanceof Map<?, ?> players)) {
                debug.addProperty("clockin.result", "PLAYERS is not Map");
                return o;
            }

            Object data = players.get(uuid);
            if (data == null) {
                debug.addProperty("clockin.result", "no player entry");
                return o;
            }

            debug.addProperty("clockin.result", "record " + data.getClass().getName());
            o.addProperty("available", true);
            o.addProperty("name", fieldString(data, "name"));
            o.addProperty("clockedIn", fieldBoolean(data, "clockedIn"));
            long total = fieldLong(data, "totalSeconds");
            if (fieldBoolean(data, "clockedIn")) {
                long at = fieldLong(data, "clockInAt");
                total += Math.max(0L, (System.currentTimeMillis() - at) / 1000L);
            }
            o.addProperty("totalSeconds", total);
        } catch (Throwable ex) {
            Throwable cause = rootCause(ex);
            String msg = cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
            debug.addProperty("clockin.error", msg);
            AdminGui.LOGGER.warn("Admin GUI ClockIn API/reflection failed for {}", uuid, cause);
        }
        return o;
    }


    private static Set<UUID> clockinPlayers() {
        Set<UUID> result = new HashSet<>();
        try {
            Class<?> c = Class.forName("com.zaremate.clockin.ClockInMod");
            Field f = c.getDeclaredField("PLAYERS");
            f.setAccessible(true);
            Object map = f.get(null);
            if (map instanceof Map<?, ?> m) for (Object k : m.keySet()) if (k instanceof UUID u) result.add(u);
        } catch (Throwable ignored) {}
        return result;
    }

    private static Set<UUID> reflectedUuids(String className, String methodName) {
        Set<UUID> result = new HashSet<>();
        try {
            Class<?> c = Class.forName(className);
            Object value = c.getMethod(methodName).invoke(null);
            if (value instanceof Iterable<?> it) {
                for (Object x : it) if (x instanceof UUID u) result.add(u);
            } else if (value instanceof Map<?, ?> m) {
                for (Object x : m.keySet()) if (x instanceof UUID u) result.add(u);
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static Set<UUID> recordUuids(String className, String methodName, String accessor) {
        Set<UUID> result = new HashSet<>();
        try {
            Class<?> c = Class.forName(className);
            Object value = c.getMethod(methodName).invoke(null);
            if (value instanceof Iterable<?> it) {
                for (Object x : it) {
                    Object v = recordAccessor(x, accessor);
                    if (v instanceof UUID u) result.add(u);
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static Optional<?> optionalInvoke(String className, String method, UUID uuid) throws Exception {
        Class<?> c = Class.forName(className);
        Object value = c.getMethod(method, UUID.class).invoke(null, uuid);
        return value instanceof Optional<?> o ? o : Optional.empty();
    }

    private static String stringRecordAccessor(String className, String method, UUID uuid, String accessor) {
        try {
            Optional<?> o = optionalInvoke(className, method, uuid);
            return o.map(x -> recordString(x, accessor)).orElse("");
        } catch (Throwable ignored) { return ""; }
    }

    private static Object recordAccessor(Object record, String name) {
        try {
            Method method;
            try {
                method = record.getClass().getMethod(name);
            } catch (NoSuchMethodException ex) {
                method = record.getClass().getDeclaredMethod(name);
                method.setAccessible(true);
            }
            return method.invoke(record);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String recordString(Object record, String name) {
        Object v = recordAccessor(record, name);
        return v == null ? "" : String.valueOf(v);
    }

    private static long recordLong(Object record, String name) {
        Object v = recordAccessor(record, name);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && current.getCause() != current
                && (current instanceof java.lang.reflect.InvocationTargetException
                    || current.getCause() instanceof java.lang.reflect.InvocationTargetException
                    || current.getCause() != null)) {
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return current;
    }

    private static Object firstObjectWithArgs(
            Object target,
            Class<?>[] parameterTypes,
            Object[] args,
            String... methods
    ) {
        for (String method : methods) {
            try {
                Method m;
                try {
                    m = target.getClass().getMethod(method, parameterTypes);
                } catch (NoSuchMethodException ex) {
                    m = target.getClass().getDeclaredMethod(method, parameterTypes);
                    m.setAccessible(true);
                }
                return m.invoke(target, args);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String firstString(Object target, String... methods) {
        for (String methodName : methods) {
            Object value = firstObject(target, methodName);
            if (value instanceof net.minecraft.network.chat.Component component) {
                String text = component.getString();
                if (!text.isBlank()) return text;
            }
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return "";
    }

    private static Object firstObject(Object target, String... methods) {
        for (String methodName : methods) {
            try {
                Method method;
                try {
                    method = target.getClass().getMethod(methodName);
                } catch (NoSuchMethodException ex) {
                    method = target.getClass().getDeclaredMethod(methodName);
                    method.setAccessible(true);
                }
                return method.invoke(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static JsonObject mapToJson(Object value) {
        JsonObject o = new JsonObject();
        if (value instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Number n) o.addProperty(String.valueOf(e.getKey()), n);
                else o.addProperty(String.valueOf(e.getKey()), String.valueOf(v));
            }
        }
        return o;
    }

    public static void addNote(UUID player, UUID author, String authorName, String text) throws Exception {
        Class<?> c = Class.forName("com.zaremate.admin_notes.AdminNotesAPI");
        c.getMethod("addNote", UUID.class, UUID.class, String.class, String.class)
                .invoke(null, player, author, authorName, text);
    }

    public static void editNote(UUID player, UUID note, String text) throws Exception {
        Class<?> c = Class.forName("com.zaremate.admin_notes.AdminNotesAPI");
        c.getMethod("editNote", UUID.class, UUID.class, String.class)
                .invoke(null, player, note, text);
    }

    public static void removeNote(UUID player, UUID note) throws Exception {
        Class<?> c = Class.forName("com.zaremate.admin_notes.AdminNotesAPI");
        c.getMethod("removeNote", UUID.class, UUID.class)
                .invoke(null, player, note);
    }

    private static String fieldString(Object o, String field) {
        try { return String.valueOf(o.getClass().getField(field).get(o)); }
        catch (Throwable ignored) { return ""; }
    }

    private static long fieldLong(Object o, String field) {
        try {
            Object v = o.getClass().getField(field).get(o);
            return v instanceof Number n ? n.longValue() : 0L;
        } catch (Throwable ignored) { return 0L; }
    }

    private static boolean fieldBoolean(Object o, String field) {
        try { return o.getClass().getField(field).getBoolean(o); }
        catch (Throwable ignored) { return false; }
    }
}
