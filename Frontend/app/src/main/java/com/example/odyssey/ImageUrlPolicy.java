package com.example.odyssey;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
/** 唯一远程图片域名配置；增加提供商时在这里审核，重定向逐跳复查。 */
final class ImageUrlPolicy {
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList("images.unsplash.com", "images.pexels.com"));
    static String accept(Object raw) {
        try {
            String value = InputValidation.optionalText(raw, 2000, "Image URL");
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
                    && uri.getHost() != null && HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))
                    && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getFragment() == null ? uri.toASCIIString() : null;
        } catch (Exception ignored) { return null; }
    }
    static String redirect(String from, Object location) {
        try { return accept(new URI(from).resolve(InputValidation.requiredText(location, 2000, "Redirect")).toString()); }
        catch (Exception ignored) { return null; }
    }
    private ImageUrlPolicy() { }
}
