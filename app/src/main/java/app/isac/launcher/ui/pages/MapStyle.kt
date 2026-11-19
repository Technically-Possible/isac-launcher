package app.isac.launcher.ui.pages

/**
 * Map style in the spirit of the game's city map: dark slate land, black water, grey roads and
 * building outlines, white uppercase place names, with the fog of war and an orange explored edge on top.
 * Uses OpenFreeMap's vector tiles (OpenMapTiles schema) and fonts.
 */
internal val ISAC_MAP_STYLE = """
{
  "version": 8,
  "name": "ISAC",
  "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
  "sources": {
    "omt": { "type": "vector", "url": "https://tiles.openfreemap.org/planet" },
    "fog": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "edges": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "caches": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "access": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "me": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "base": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } }
  },
  "layers": [
    { "id": "bg", "type": "background", "paint": { "background-color": "#26313D" } },
    { "id": "landuse", "type": "fill", "source": "omt", "source-layer": "landuse",
      "filter": ["in", "class", "residential", "suburb", "neighbourhood", "commercial", "industrial"],
      "paint": { "fill-color": "#2A3541" } },
    { "id": "landcover", "type": "fill", "source": "omt", "source-layer": "landcover",
      "filter": ["in", "class", "grass", "wood", "farmland", "scrub"],
      "paint": { "fill-color": "#28372F", "fill-opacity": 0.8 } },
    { "id": "park", "type": "fill", "source": "omt", "source-layer": "park", "paint": { "fill-color": "#2A3B32" } },
    { "id": "water", "type": "fill", "source": "omt", "source-layer": "water", "paint": { "fill-color": "#111C27" } },
    { "id": "waterway", "type": "line", "source": "omt", "source-layer": "waterway",
      "paint": { "line-color": "#111C27", "line-width": 1.6 } },
    { "id": "building", "type": "fill", "source": "omt", "source-layer": "building", "minzoom": 13,
      "paint": { "fill-color": "#313D4A", "fill-outline-color": "#44525F" } },
    { "id": "road-minor", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 12,
      "filter": ["in", "class", "minor", "service", "track", "path"],
      "paint": { "line-color": "#4B5866", "line-width": ["interpolate", ["linear"], ["zoom"], 12, 0.5, 17, 4] } },
    { "id": "road-main", "type": "line", "source": "omt", "source-layer": "transportation",
      "filter": ["in", "class", "primary", "secondary", "tertiary", "trunk"],
      "paint": { "line-color": "#6A7887", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.6, 17, 7] } },
    { "id": "road-motorway", "type": "line", "source": "omt", "source-layer": "transportation",
      "filter": ["==", "class", "motorway"],
      "paint": { "line-color": "#8A97A4", "line-width": ["interpolate", ["linear"], ["zoom"], 6, 0.8, 17, 8] } },
    { "id": "rail", "type": "line", "source": "omt", "source-layer": "transportation",
      "filter": ["==", "class", "rail"],
      "paint": { "line-color": "#55606C", "line-width": 1, "line-dasharray": [3, 3] } },
    { "id": "boundary", "type": "line", "source": "omt", "source-layer": "boundary",
      "filter": ["<=", "admin_level", 4],
      "paint": { "line-color": "#5C4A3D", "line-width": 1, "line-dasharray": [4, 2] } },
    { "id": "road-label", "type": "symbol", "source": "omt", "source-layer": "transportation_name", "minzoom": 14,
      "layout": { "symbol-placement": "line", "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"],
        "text-size": 10, "text-transform": "uppercase", "text-letter-spacing": 0.1 },
      "paint": { "text-color": "#A9B4BE", "text-halo-color": "#0C1015", "text-halo-width": 1.2 } },
    { "id": "fog", "type": "fill", "source": "fog", "paint": { "fill-color": "#0A0E13", "fill-opacity": 0.62 } },
    { "id": "edges", "type": "line", "source": "edges",
      "paint": { "line-color": "#FF6D10", "line-opacity": 0.85, "line-width": 1.8 } },
    { "id": "place-label", "type": "symbol", "source": "omt", "source-layer": "place",
      "filter": ["in", "class", "city", "town", "village", "suburb"],
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Bold"],
        "text-size": ["match", ["get", "class"], "city", 16, "town", 13, 11],
        "text-transform": "uppercase", "text-letter-spacing": 0.15 },
      "paint": { "text-color": "#D8DDE2", "text-halo-color": "#0A0D11", "text-halo-width": 1.5 } },
    { "id": "access", "type": "circle", "source": "access", "minzoom": 12, "layout": { "visibility": "none" },
      "paint": { "circle-radius": ["interpolate", ["linear"], ["zoom"], 12, 3, 16, 7],
        "circle-color": ["case", ["get", "toilet"], "#3B8FE6", ["==", ["get", "access"], "limited"], "#F0C23B", "#5FD08A"],
        "circle-stroke-color": "#0A0D11", "circle-stroke-width": 1.5 } },
    { "id": "access-label", "type": "symbol", "source": "access", "minzoom": 15.5, "layout": { "visibility": "none",
        "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 10, "text-offset": [0, 1.2], "text-optional": true },
      "paint": { "text-color": "#CFE9D8", "text-halo-color": "#0A0D11", "text-halo-width": 1.3 } },
    { "id": "caches", "type": "circle", "source": "caches", "minzoom": 11,
      "paint": { "circle-radius": ["interpolate", ["linear"], ["zoom"], 11, 3, 16, 8],
        "circle-color": ["case", ["get", "found"], "#FF6D10", "#0B0C0D"],
        "circle-stroke-color": ["case", ["get", "found"], "#F2F2F0", "#FF6D10"], "circle-stroke-width": 2 } },
    { "id": "caches-label", "type": "symbol", "source": "caches", "minzoom": 14.5,
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Bold"], "text-size": 10, "text-offset": [0, 1.4],
        "text-letter-spacing": 0.08, "text-optional": true, "text-max-width": 9 },
      "paint": { "text-color": "#FFB27A", "text-halo-color": "#0A0D11", "text-halo-width": 1.4 } },
    { "id": "base", "type": "circle", "source": "base",
      "paint": { "circle-radius": 7, "circle-color": "#0B0C0D", "circle-stroke-color": "#FF6D10", "circle-stroke-width": 2.5 } },
    { "id": "base-label", "type": "symbol", "source": "base",
      "layout": { "text-field": "BASE", "text-font": ["Noto Sans Bold"], "text-size": 11, "text-offset": [0, 1.5],
        "text-letter-spacing": 0.15, "text-allow-overlap": true },
      "paint": { "text-color": "#FF6D10", "text-halo-color": "#0A0D11", "text-halo-width": 1.5 } },
    { "id": "me-halo", "type": "circle", "source": "me",
      "paint": { "circle-radius": 18, "circle-color": "#FF6D10", "circle-opacity": 0.18 } },
    { "id": "me", "type": "circle", "source": "me",
      "paint": { "circle-radius": 6, "circle-color": "#FF6D10", "circle-stroke-color": "#F2F2F0", "circle-stroke-width": 1.5 } }
  ]
}
"""

internal fun pointJson(lat: Double, lon: Double) =
    """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[$lon,$lat]}}"""

/** DIAMOND's map: void black ground, steel roads, crimson explored edge and caches. */
private val DIAMOND_COLOURS = mapOf(
    "#26313D" to "#12151A", "#2A3541" to "#15191E", "#28372F" to "#15191E", "#2A3B32" to "#171B21",
    "#111C27" to "#06080B", "#313D4A" to "#1B2026", "#44525F" to "#2C333C", "#4B5866" to "#3A414B",
    "#6A7887" to "#59616D", "#8A97A4" to "#8B93A0", "#55606C" to "#3A414B", "#5C4A3D" to "#4A2A30",
    "#A9B4BE" to "#B8C0CA", "#0A0E13" to "#050608", "#D8DDE2" to "#F2F5F8", "#3B8FE6" to "#00A8E8",
    "#F0C23B" to "#FF9E00", "#5FD08A" to "#F2F5F8", "#CFE9D8" to "#CDEBF7", "#FFB27A" to "#FF8A96",
    "#F2F2F0" to "#F2F5F8", "#0B0C0D" to "#0B0D10",
)

/** The map style for the current look, with the explored edge and caches in the accent colour. */
internal fun mapStyleFor(diamond: Boolean, accentHex: String): String {
    if (!diamond) return ISAC_MAP_STYLE.replace("#FF6D10", accentHex)
    var s = ISAC_MAP_STYLE.replace("#FF6D10", "#F22D41")
    DIAMOND_COLOURS.forEach { (from, to) -> s = s.replace(from, to) }
    return s
}
