package com.example.odyssey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 不可变的共享偏好目录，稳定 ID 用于本地保存。 */
final class PreferenceTags {
    static final Map<String, String> ALL;
    static {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("INTERNATIONAL", "International");
        tags.put("DOMESTIC", "Domestic");
        tags.put("WEEKEND_BREAKS", "Weekend breaks");
        tags.put("LONG_HAUL", "Long-haul trips");
        tags.put("ROAD_TRIPS", "Road trips");
        tags.put("RAIL_TRAVEL", "Train journeys");
        tags.put("ISLANDS", "Islands");
        tags.put("BEACHES", "Beaches");
        tags.put("MOUNTAINS", "Mountains");
        tags.put("COUNTRYSIDE", "Countryside");
        tags.put("CITY_BREAKS", "City breaks");
        tags.put("NATURE", "Nature");
        tags.put("SHOPPING", "Shopping");
        tags.put("FOOD", "Local food");
        tags.put("CAFES", "Cafés");
        tags.put("MUSEUMS", "Museums");
        tags.put("ART", "Art & galleries");
        tags.put("HISTORY", "History");
        tags.put("ARCHITECTURE", "Architecture");
        tags.put("CULTURE", "Local culture");
        tags.put("PHOTOGRAPHY", "Photography");
        tags.put("MUSIC", "Live music");
        tags.put("FESTIVALS", "Festivals");
        tags.put("NIGHTLIFE", "Nightlife");
        tags.put("HIKING", "Hiking");
        tags.put("CYCLING", "Cycling");
        tags.put("WATER_ACTIVITIES", "Water activities");
        tags.put("WILDLIFE", "Wildlife");
        tags.put("ADVENTURE", "Adventure");
        tags.put("WELLNESS", "Wellness");
        tags.put("SLOW_TRAVEL", "Slow travel");
        tags.put("PACKED_ITINERARY", "See more places");
        tags.put("HIDDEN_GEMS", "Hidden gems");
        tags.put("LUXURY", "Luxury stays");
        tags.put("BUDGET_FRIENDLY", "Budget-friendly");
        tags.put("ECO_CONSCIOUS", "Eco-conscious");
        tags.put("DIVING", "Diving");
        tags.put("SNORKELING", "Snorkeling");
        tags.put("FREEDIVING", "Freediving");
        tags.put("SURFING", "Surfing");
        tags.put("KITESURFING", "Kitesurfing");
        tags.put("WINDSURFING", "Windsurfing");
        tags.put("KAYAKING", "Kayaking");
        tags.put("CANOEING", "Canoeing");
        tags.put("PADDLEBOARDING", "Paddleboarding");
        tags.put("SAILING", "Sailing");
        tags.put("RAFTING", "Rafting");
        tags.put("SWIMMING", "Swimming");
        tags.put("SKIING", "Skiing");
        tags.put("SNOWBOARDING", "Snowboarding");
        tags.put("ICE_SKATING", "Ice skating");
        tags.put("ROCK_CLIMBING", "Rock climbing");
        tags.put("BOULDERING", "Bouldering");
        tags.put("TRAIL_RUNNING", "Trail running");
        tags.put("RUNNING", "Running");
        tags.put("MOUNTAIN_BIKING", "Mountain biking");
        tags.put("HORSE_RIDING", "Horse riding");
        tags.put("TENNIS", "Tennis");
        tags.put("GOLF", "Golf");
        tags.put("SPORTS_EVENTS", "Sports events");
        ALL = Collections.unmodifiableMap(tags);
    }
    private PreferenceTags() { }
}
