package com.example.odyssey;

import android.content.Context;
import android.util.Xml;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;

/** 国家由 Android 的 ISO 国家目录提供；州/省来自随应用附带的 Unicode CLDR 数据。 */
final class RegionCatalog {
    static final class Country {
        final String code;
        final String name;
        Country(String code, String name) { this.code = code; this.name = name; }
        @Override public String toString() { return name; }
    }
    private final List<Country> countries = new ArrayList<>();
    private final Map<String, List<String>> regions = new HashMap<>();

    RegionCatalog(Context context) {
        for (String code : Locale.getISOCountries()) {
            String name = new Locale("en", code).getDisplayCountry(Locale.ENGLISH);
            if (!name.isEmpty()) countries.add(new Country(code, name));
        }
        Collections.sort(countries, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name));
        try (InputStream source = context.getAssets().open("geo/subdivisions-en.xml")) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(source, "UTF-8");
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG || !"subdivision".equals(parser.getName())
                        || parser.getAttributeValue(null, "alt") != null) continue;
                String code = parser.getAttributeValue(null, "type");
                if (code == null || code.length() < 3) continue;
                String name = parser.nextText().trim();
                if (name.isEmpty() || name.equals("↑↑↑")) continue;
                String country = code.substring(0, 2).toUpperCase(Locale.ROOT);
                List<String> items = regions.computeIfAbsent(country, ignored -> new ArrayList<>());
                if (!items.contains(name)) items.add(name);
            }
            for (List<String> list : regions.values()) Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        } catch (Exception ignored) {
            // 缺少地区数据时依然能选择国家，不把读取错误变成崩溃或删除已有资料。
            regions.clear();
        }
    }
    List<Country> countries() { return Collections.unmodifiableList(countries); }
    List<String> regions(String code) { return regions.containsKey(code) ? regions.get(code) : Collections.emptyList(); }
}
