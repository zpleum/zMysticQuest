package me.zpleum.zmystic.quest.models;

import java.util.Map;
import java.util.UUID;

public class Quest {
    
    private final UUID id;
    private final String typeId;
    private final String name;
    private final String description;
    private final QuestType type;
    private final Map<String, Object> data;
    private final int xpReward;
    private final UUID playerId;
    private int progress;
    private long startTime;
    
    public Quest(UUID id, String typeId, String name, String description, QuestType type, 
                 Map<String, Object> data, int xpReward, UUID playerId) {
        this.id = id;
        this.typeId = typeId;
        this.name = name;
        this.description = description;
        this.type = type;
        this.data = data;
        this.xpReward = xpReward;
        this.playerId = playerId;
        this.progress = 0;
        this.startTime = System.currentTimeMillis();
    }
    
    public UUID getId() {
        return id;
    }
    
    public String getTypeId() {
        return typeId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public QuestType getType() {
        return type;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public int getXpReward() {
        return xpReward;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public int getProgress() {
        return progress;
    }
    
    public void setProgress(int progress) {
        this.progress = progress;
    }
    
    public void incrementProgress() {
        this.progress++;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public int getTargetAmount() {
        switch (type) {
            case KILL:
            case COLLECT:
            case CRAFT:
            case INTERACT:
                return (int) data.getOrDefault("amount", 1);
            case EXPLORE:
                return 1; // Exploration quests typically just need to visit a location once
            default:
                return 1;
        }
    }
    
    public boolean isCompleted() {
        return progress >= getTargetAmount();
    }
    
    public boolean isExpired() {
        if (!data.containsKey("timeLimit")) {
            return false; // No time limit
        }
        
        int timeLimit = (int) data.get("timeLimit"); // in seconds
        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        
        return elapsedTime > timeLimit;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quest quest = (Quest) o;
        return id.equals(quest.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
} 