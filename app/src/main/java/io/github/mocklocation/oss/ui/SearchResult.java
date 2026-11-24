package io.github.mocklocation.oss.ui;

/** 一条 Nominatim 地址搜索结果。 */
public final class SearchResult {

    public final String displayName;
    public final double latitude;
    public final double longitude;

    public SearchResult(String displayName, double latitude, double longitude) {
        this.displayName = displayName;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
