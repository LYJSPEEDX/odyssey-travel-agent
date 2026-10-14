package com.example.odyssey;

/**
 * 五步表单的本地假数据目录：目的地=城市+关键词，其余选项=标题+说明。
 * 这些固定值用于验证交互，不是实时推荐；接入接口后应替换数据来源。
 */
final class TripOptions {
    static final class Option {
        final String title;
        final String description;
        Option(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    static final class Destination {
        final String city;
        final String[] keywords;
        final int dailyBase;
        Destination(String city, int dailyBase, String... keywords) {
            this.city = city;
            this.dailyBase = dailyBase;
            this.keywords = keywords;
        }
        String description() { return String.join(" · ", keywords); }
    }

    static final String[] DEPARTURES = {"Sydney", "Melbourne", "Shanghai"};
    static final Destination[] DESTINATIONS = {
            new Destination("Kyoto", 100, "Temples", "Tea", "Slow travel"),
            new Destination("Tokyo", 130, "Design", "Food", "Nightlife"),
            new Destination("Bali", 70, "Beaches", "Nature", "Wellness"),
            new Destination("Paris", 160, "Art", "Cafés", "Architecture")
    };
    static final Option[] COMPANIONS = {
            new Option("Solo", "Your own pace, with room for spontaneous discoveries."),
            new Option("Couple", "Shared moments, cosy stays and time for two."),
            new Option("Friends", "Mix local food, activities and places to hang out."),
            new Option("Family", "Flexible days with something for every generation.")
    };
    static final Option[] CONSTRAINTS = {
            new Option("Vegetarian", "Prioritise places with meat-free choices."),
            new Option("With pets", "Look for pet-friendly stays and activities."),
            new Option("With older adults", "Allow more breaks and easier connections."),
            new Option("Less walking", "Keep walking distances short and use transport."),
            new Option("See more", "Fit more places into each day."),
            new Option("Take it slow", "Leave space to rest and enjoy each stop.")
    };
    static Destination destination(String city) {
        for (Destination destination : DESTINATIONS) {
            if (destination.city.equalsIgnoreCase(city)) return destination;
        }
        return null;
    }
    static Option[] budgets(String city) {
        // 根据示例城市的基准值生成三档展示文案，未知城市使用默认值；不是实际报价或总预算。
        Destination destination = destination(city);
        int base = destination == null ? 100 : destination.dailyBase;
        return new Option[]{
                new Option("Essentials", "AUD " + base + "–" + (base + 80)
                        + " · Simple stays, casual meals and free local sights."),
                new Option("Comfort", "AUD " + (base + 80) + "–" + (base + 220)
                        + " · Comfortable stays, good food and a few paid experiences."),
                new Option("Treat yourself", "AUD " + (base + 220)
                        + "+ · Boutique stays, special meals and more convenience.")
        };
    }
    static String budgetDescription(TripPlanState state) {
        for (Option option : budgets(state.getDestination())) {
            if (option.title.equals(state.getBudget())) return option.description;
        }
        return state.getBudget();
    }
    private TripOptions() {}
}
