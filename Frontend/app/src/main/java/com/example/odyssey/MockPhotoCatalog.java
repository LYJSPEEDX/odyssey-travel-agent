package com.example.odyssey;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
/** 只复用已批准的固定素材；绝不把城市或用户文字发送给图库搜索。 */
final class MockPhotoCatalog {
    static String url(String city, boolean thumbnail) {
        String id;
        switch (InputValidation.text(city).toLowerCase(java.util.Locale.ROOT)) {
            case "kyoto": id = "1493976040374-85c8e12f0c0e"; break;
            case "tokyo": id = "1540959733332-eab4deabeeaf"; break;
            case "paris": id = "1502602898657-3e91760cbb34"; break;
            case "bali": id = "1537996194471-e657df975ab4"; break;
            case "new york": id = "1485871981521-5b1fd3805eee"; break;
            case "canberra": id = "1672264597620-d792bb6de88d"; break;
            default: return null;
        }
        return "https://images.unsplash.com/photo-" + id + "?auto=format&fit=crop&w=" + (thumbnail ? 640 : 1200) + "&q=80";
    }
    static JSONObject media(String city) throws JSONException {
        String url = url(city, false); if (url == null) return null;
        return new JSONObject().put("id", "mock_cover").put("placeId", "destination").put("usage", "COVER")
                .put("status", "READY").put("provider", "UNSPLASH").put("providerAssetId", JSONObject.NULL)
                .put("assetKey", JSONObject.NULL).put("url", url).put("thumbnailUrl", url(city, true))
                .put("altText", city + " atmosphere photo; illustrative")
                .put("attributions", new JSONArray().put(new JSONObject().put("text", "Photo: Unsplash · illustrative")
                        .put("url", "https://unsplash.com/license"))).put("expiresAt", JSONObject.NULL);
    }
    private MockPhotoCatalog() { }
}
