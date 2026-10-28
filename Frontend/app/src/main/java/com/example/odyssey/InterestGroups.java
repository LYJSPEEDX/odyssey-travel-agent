package com.example.odyssey;

import java.util.LinkedHashMap;
import java.util.Map;

/** 初始显示 12 个兴趣；点选后展开同一主题的细分标签。保留原有 ID 并扩展为 96 个兴趣。 */
final class InterestGroups {
    static Map<String, String[]> all() {
        Map<String, String[]> groups = new LinkedHashMap<>();
        groups.put("INTERNATIONAL", new String[]{"DOMESTIC", "LONG_HAUL", "MULTI_COUNTRY", "CROSS_BORDER_RAIL", "OVERSEAS_ROAD_TRIPS"});
        groups.put("WEEKEND_BREAKS", new String[]{"CITY_BREAKS", "ROAD_TRIPS", "RAIL_TRAVEL", "STAYCATIONS", "DAY_TRIPS", "SCENIC_DRIVES"});
        groups.put("ISLANDS", new String[]{"BEACHES", "ISLAND_HOPPING", "COASTAL_WALKS", "BEACH_CAMPING"});
        groups.put("MOUNTAINS", new String[]{"COUNTRYSIDE", "NATURE", "CAMPING", "STARGAZING", "NATIONAL_PARKS"});
        groups.put("SHOPPING", new String[]{"LUXURY", "BUDGET_FRIENDLY", "VINTAGE_MARKETS", "LOCAL_CRAFTS", "DESIGN_BOUTIQUES"});
        groups.put("FOOD", new String[]{"CAFES", "STREET_FOOD", "COOKING_CLASSES", "FOOD_MARKETS"});
        groups.put("CULTURE", new String[]{"MUSEUMS", "ART", "HISTORY", "ARCHITECTURE", "PHOTOGRAPHY", "HERITAGE_SITES", "WALKING_TOURS", "CULTURAL_WORKSHOPS"});
        groups.put("MUSIC", new String[]{"FESTIVALS", "NIGHTLIFE", "SPORTS_EVENTS", "JAZZ_CLUBS", "LIVE_CONCERTS", "DANCE_EVENTS"});
        groups.put("WATER_ACTIVITIES", new String[]{"DIVING", "SNORKELING", "FREEDIVING", "SURFING", "KITESURFING", "WINDSURFING", "KAYAKING", "CANOEING", "PADDLEBOARDING", "SAILING", "RAFTING", "SWIMMING", "WRECK_DIVING", "REEF_DIVING", "WAKEBOARDING"});
        groups.put("ADVENTURE", new String[]{"HIKING", "CYCLING", "WILDLIFE", "SKIING", "SNOWBOARDING", "ICE_SKATING", "ROCK_CLIMBING", "BOULDERING", "TRAIL_RUNNING", "RUNNING", "MOUNTAIN_BIKING", "HORSE_RIDING", "TENNIS", "GOLF", "PARAGLIDING", "ZIPLINING", "CANYONING"});
        groups.put("WELLNESS", new String[]{"SLOW_TRAVEL", "ECO_CONSCIOUS", "HOT_SPRINGS", "YOGA_RETREATS", "SPA_BREAKS"});
        groups.put("PACKED_ITINERARY", new String[]{"HIDDEN_GEMS", "CITY_HOPPING", "LANDMARKS", "SUNRISE_EXCURSIONS"});
        return groups;
    }
    private InterestGroups() { }
}
