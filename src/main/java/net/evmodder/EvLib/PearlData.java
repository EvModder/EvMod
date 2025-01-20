package net.evmodder.EvLib;

import java.util.UUID;

public record PearlData(UUID owner, int submittedBy, long created, long lastAccessed){}