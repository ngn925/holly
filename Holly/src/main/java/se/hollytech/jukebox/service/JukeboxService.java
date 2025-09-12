package se.hollytech.jukebox.service;

import se.hollytech.jukebox.model.Artist;
import se.hollytech.jukebox.model.ArtistLookup;
import se.hollytech.jukebox.model.Album;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class JukeboxService {

    private static final Logger logger = LoggerFactory.getLogger(JukeboxService.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private static final String MUSICBRAINZ_API_URL = "https://musicbrainz.org/ws/2/artist/";
    private static final String COVER_ART_API_URL = "http://coverartarchive.org/release-group/";
    private static final String WIKIPEDIA_API_URL = "https://en.wikipedia.org/w/api.php";
    private static final String WIKIDATA_API_URL = "https://www.wikidata.org/w/api.php";

    public JukeboxService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "artistLookupCache", key = "#artistName.toLowerCase()")
    @RateLimiter(name = "musicBrainzApi", fallbackMethod = "rateLimitFallback")
    public ArtistLookup getArtistMbid(String artistName) {
        if (artistName == null || artistName.trim().isEmpty()) {
            logger.warn("Invalid artist name provided: artistName={}", artistName);
            throw new IllegalArgumentException("Artist name cannot be empty");
        }

        logger.info("Processing MBID lookup request: artistName={}", artistName);

        String url = UriComponentsBuilder.fromHttpUrl(MUSICBRAINZ_API_URL)
                .queryParam("query", "artist:" + artistName)
                .queryParam("fmt", "json")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "JukeboxApi/1.0 (your.email@example.com)");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        logger.debug("Calling MusicBrainz API for lookup: url={}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String jsonResponse = response.getBody();

        if (jsonResponse == null || jsonResponse.contains("\"artists\":[]")) {
            logger.warn("No artists found: artistName={}", artistName);
            throw new ArtistNotFoundException("No artists found for query: " + artistName);
        }

        try {
            logger.debug("Parsing MusicBrainz API response: artistName={}", artistName);
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode artist = root.path("artists").get(0);
            if (artist.isMissingNode()) {
                logger.warn("No valid artist data in response: artistName={}", artistName);
                throw new ArtistNotFoundException("No valid artist data found for query: " + artistName);
            }

            String mbid = artist.path("id").asText();
            String name = artist.path("name").asText();

            if (mbid.isEmpty() || name.isEmpty()) {
                logger.warn("Invalid artist data: artistName={}, mbid={}, name={}", artistName, mbid, name);
                throw new ArtistNotFoundException("Invalid artist data for query: " + artistName);
            }

            logger.info("Successfully retrieved MBID: artistName={}, mbid={}", artistName, mbid);
            return new ArtistLookup(name, mbid);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse MusicBrainz API response: artistName={}, error={}", artistName, e.getMessage(), e);
            throw new MusicBrainzApiException("Failed to parse response from MusicBrainz API", e);
        } catch (Exception e) {
            logger.error("Failed to fetch artist MBID: artistName={}, error={}", artistName, e.getMessage(), e);
            throw new MusicBrainzApiException("Failed to fetch artist MBID: " + e.getMessage(), e);
        }
    }

    @Cacheable(value = "artistDetailsCache", key = "#mbid")
    @RateLimiter(name = "musicBrainzApi", fallbackMethod = "rateLimitDetailsFallback")
    public Artist getArtistDetails(String mbid) {
        if (mbid == null || mbid.trim().isEmpty()) {
            logger.warn("Invalid MBID provided: mbid={}", mbid);
            throw new IllegalArgumentException("MBID cannot be empty");
        }

        logger.info("Processing artist details request: mbid={}", mbid);

        String url = UriComponentsBuilder.fromHttpUrl(MUSICBRAINZ_API_URL + mbid)
                .queryParam("fmt", "json")
                .queryParam("inc", "url-rels+release-groups")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "JukeboxApi/1.0 (your.email@example.com)");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        logger.debug("Calling MusicBrainz API for details: url={}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String jsonResponse = response.getBody();

        if (jsonResponse == null) {
            logger.warn("No data returned for MBID: mbid={}", mbid);
            throw new ArtistNotFoundException("No data found for MBID: " + mbid);
        }

        try {
            logger.debug("Parsing MusicBrainz API response for details: mbid={}", mbid);
            JsonNode root = objectMapper.readTree(jsonResponse);
            String name = root.path("name").asText();
            String responseMbid = root.path("id").asText();

            if (responseMbid.isEmpty() || name.isEmpty()) {
                logger.warn("Invalid artist data: mbid={}, name={}", mbid, name);
                throw new ArtistNotFoundException("Invalid artist data for MBID: " + mbid);
            }

            // Extract Wikipedia page title
            String wikipediaPageTitle = extractWikipediaPageTitle(root, mbid, name);
            String description = wikipediaPageTitle != null ? fetchWikipediaDescription(wikipediaPageTitle) : null;
            if (description == null) {
                logger.debug("No Wikipedia description found: mbid={}, pageTitle={}", mbid, wikipediaPageTitle);
            } else {
                logger.debug("Wikipedia description retrieved: mbid={}, descriptionLength={}", mbid, description.length());
            }

            // Parse release-groups for albums
            List<Album> albums = new ArrayList<>();
            JsonNode releaseGroups = root.path("release-groups");
            logger.debug("Inspecting release-groups array: mbid={}, releaseGroupsCount={}", mbid, releaseGroups.isArray() ? releaseGroups.size() : 0);
            if (!releaseGroups.isMissingNode() && releaseGroups.isArray()) {
                for (JsonNode releaseGroup : releaseGroups) {
                    String primaryType = releaseGroup.path("primary-type").asText();
                    if ("Album".equalsIgnoreCase(primaryType)) {
                        String albumId = releaseGroup.path("id").asText();
                        String title = releaseGroup.path("title").asText();
                        if (!albumId.isEmpty() && !title.isEmpty()) {
                            logger.debug("Found album: mbid={}, albumId={}, title={}", mbid, albumId, title);
                            String imageUrl = fetchCoverArt(albumId);
                            if (imageUrl != null) {
                                albums.add(new Album(title, albumId, imageUrl));
                                logger.debug("Added album with cover art: mbid={}, albumId={}, title={}, image={}", mbid, albumId, title, imageUrl);
                            } else {
                                logger.debug("No cover art found for album: mbid={}, albumId={}, title={}", mbid, albumId, title);
                            }
                        }
                    }
                }
            } else {
                logger.debug("No release-groups found in response: mbid={}", mbid);
            }

            logger.info("Successfully retrieved artist details: mbid={}, name={}, descriptionLength={}, albumsCount={}",
                    mbid, name, description != null ? description.length() : 0, albums.size());
            return new Artist(name, description, mbid, Collections.unmodifiableList(albums));
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse MusicBrainz API response: mbid={}, error={}", mbid, e.getMessage(), e);
            throw new MusicBrainzApiException("Failed to parse response from MusicBrainz API", e);
        } catch (Exception e) {
            logger.error("Failed to fetch artist details: mbid={}, error={}", mbid, e.getMessage(), e);
            throw new MusicBrainzApiException("Failed to fetch artist details: " + e.getMessage(), e);
        }
    }

    @Cacheable(value = "artistDiscographyCache", key = "#artistName.toLowerCase()")
    @RateLimiter(name = "musicBrainzApi", fallbackMethod = "rateLimitDiscographyFallback")
    public Artist getArtistDiscography(String artistName) {
        if (artistName == null || artistName.trim().isEmpty()) {
            logger.warn("Invalid artist name provided for discography: artistName={}", artistName);
            throw new IllegalArgumentException("Artist name cannot be empty");
        }

        logger.info("Processing artist discography request: artistName={}", artistName);

        // Step 1: Get MBID
        logger.debug("Fetching MBID for artist: artistName={}", artistName);
        ArtistLookup artistLookup = getArtistMbid(artistName);
        String mbid = artistLookup.mbid();
        logger.debug("Retrieved MBID: artistName={}, mbid={}", artistName, mbid);

        // Step 2: Get artist details using MBID
        logger.debug("Fetching artist details for: mbid={}", mbid);
        Artist artist = getArtistDetails(mbid);
        logger.info("Successfully retrieved artist discography: artistName={}, mbid={}, name={}, albumsCount={}",
                artistName, mbid, artist.name(), artist.albums().size());
        return artist;
    }

    private String extractWikipediaPageTitle(JsonNode root, String mbid, String artistName) {
        JsonNode relations = root.path("relations");
        logger.debug("Inspecting relations array for Wikipedia/Wikidata: mbid={}, relationsCount={}", mbid, relations.isArray() ? relations.size() : 0);
        String wikidataId = null;

        // First, try to find a direct Wikipedia relation
        if (!relations.isMissingNode() && relations.isArray()) {
            for (JsonNode relation : relations) {
                String type = relation.path("type").asText();
                JsonNode urlNode = relation.path("url");
                String resource = urlNode.path("resource").asText();
                logger.debug("Processing relation: mbid={}, type={}, resource={}", mbid, type, resource);
                if ("wikipedia".equalsIgnoreCase(type) && !resource.isEmpty()) {
                    try {
                        String pageTitle = resource.substring(resource.lastIndexOf("/") + 1);
                        logger.debug("Found Wikipedia page title from MusicBrainz: mbid={}, pageTitle={}", mbid, pageTitle);
                        return pageTitle;
                    } catch (Exception e) {
                        logger.error("Failed to parse Wikipedia page title from resource: mbid={}, resource={}, error={}", mbid, resource, e.getMessage());
                    }
                }
                if ("wikidata".equalsIgnoreCase(type) && !resource.isEmpty()) {
                    try {
                        wikidataId = resource.substring(resource.lastIndexOf("/") + 1);
                        logger.debug("Found Wikidata ID: mbid={}, wikidataId={}", mbid, wikidataId);
                    } catch (Exception e) {
                        logger.error("Failed to parse Wikidata ID from resource: mbid={}, resource={}, error={}", mbid, resource, e.getMessage());
                    }
                }
            }
        } else {
            logger.debug("No relations found in response: mbid={}", mbid);
        }

        // If no direct Wikipedia relation, use Wikidata to get the page title
        if (wikidataId != null) {
            String pageTitle = fetchWikipediaPageTitleFromWikidata(wikidataId, mbid);
            if (pageTitle != null) {
                logger.debug("Retrieved Wikipedia page title from Wikidata: mbid={}, wikidataId={}, pageTitle={}", mbid, wikidataId, pageTitle);
                return pageTitle;
            }
        }

        logger.debug("No Wikipedia page title found: mbid={}, artistName={}", mbid, artistName);
        return null;
    }

    @RateLimiter(name = "wikidataApi", fallbackMethod = "wikidataFallback")
    private String fetchWikipediaPageTitleFromWikidata(String wikidataId, String mbid) {
        String url = UriComponentsBuilder.fromHttpUrl(WIKIDATA_API_URL)
                .queryParam("action", "wbgetentities")
                .queryParam("ids", wikidataId)
                .queryParam("format", "json")
                .queryParam("props", "sitelinks")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "JukeboxApi/1.0 (your.email@example.com)");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            logger.debug("Calling Wikidata API: url={}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String jsonResponse = response.getBody();

            if (jsonResponse == null) {
                logger.debug("No Wikidata data returned: wikidataId={}", wikidataId);
                return null;
            }

            logger.debug("Wikidata API raw response: wikidataId={}, response={}", wikidataId, jsonResponse);

            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode rentity = root.path("entities").path(wikidataId).path("sitelinks").path("enwiki");
            if (!rentity.isMissingNode()) {
                String pageTitle = rentity.path("title").asText();
                if (!pageTitle.isEmpty()) {
                    logger.debug("Found Wikipedia page title from Wikidata: wikidataId={}, pageTitle={}", wikidataId, pageTitle);
                    return pageTitle;
                }
            }
            logger.debug("No English Wikipedia page title found in Wikidata: wikidataId={}", wikidataId);
            return null;
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse Wikidata API response: wikidataId={}, error={}", wikidataId, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Failed to fetch Wikidata page title: wikidataId={}, error={}", wikidataId, e.getMessage(), e);
            return null;
        }
    }

    @RateLimiter(name = "wikipediaApi", fallbackMethod = "wikipediaFallback")
    private String fetchWikipediaDescription(String pageTitle) {
        if (pageTitle == null || pageTitle.trim().isEmpty()) {
            logger.warn("Invalid Wikipedia page title: pageTitle={}", pageTitle);
            return null;
        }

        // Normalize pageTitle: replace spaces with underscores to match Wikipedia URL format
        String normalizedPageTitle = pageTitle.replace(" ", "_");
        logger.debug("Normalized pageTitle: original={}, normalized={}", pageTitle, normalizedPageTitle);

        // Use the normalized pageTitle directly, as it's URL-safe for Wikipedia
        String url = UriComponentsBuilder.fromHttpUrl(WIKIPEDIA_API_URL)
                .queryParam("action", "query")
                .queryParam("prop", "extracts")
                .queryParam("exintro", "true")
                .queryParam("explaintext", "false")
                .queryParam("redirects", "true")
                .queryParam("titles", normalizedPageTitle)
                .queryParam("format", "json")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "JukeboxApi/1.0 (your.email@example.com)");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            logger.debug("Calling Wikipedia API: pageTitle={}, normalizedPageTitle={}, url={}", pageTitle, normalizedPageTitle, url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String jsonResponse = response.getBody();

            if (jsonResponse == null) {
                logger.debug("No Wikipedia data returned: pageTitle={}", pageTitle);
                return null;
            }

            logger.debug("Wikipedia API raw response: pageTitle={}, response={}", pageTitle, jsonResponse);

            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode pages = root.path("query").path("pages");
            logger.debug("Wikipedia response pages: pageTitle={}, pagesCount={}", pageTitle, pages.size());
            if (!pages.isMissingNode() && pages.isObject()) {
                for (JsonNode page : pages) {
                    String pageId = page.path("pageid").asText();
                    String title = page.path("title").asText();
                    String extract = page.path("extract").asText();
                    logger.debug("Processing page: pageTitle={}, pageId={}, title={}, extractLength={}",
                            pageTitle, pageId, title, extract.length());
                    if (!extract.isEmpty() && !extract.equals("null")) {
                        logger.debug("Found Wikipedia description: pageTitle={}, extractLength={}", pageTitle, extract.length());
                        return extract;
                    }
                }
            }
            logger.debug("No valid Wikipedia description found: pageTitle={}", pageTitle);
            return null;
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse Wikipedia API response: pageTitle={}, error={}", pageTitle, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Failed to fetch Wikipedia description: pageTitle={}, error={}", pageTitle, e.getMessage(), e);
            return null;
        }
    }

    @RateLimiter(name = "coverArtApi", fallbackMethod = "coverArtFallback")
    private String fetchCoverArt(String releaseGroupId) {
        String url = COVER_ART_API_URL + releaseGroupId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "JukeboxApi/1.0 (your.email@example.com)");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            logger.debug("Calling Cover Art Archive API: url={}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String jsonResponse = response.getBody();

            if (jsonResponse == null) {
                logger.debug("No cover art data returned: releaseGroupId={}", releaseGroupId);
                return null;
            }

            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode images = root.path("images");
            if (!images.isMissingNode() && images.isArray()) {
                for (JsonNode image : images) {
                    if (image.path("front").asBoolean()) {
                        String imageUrl = image.path("image").asText();
                        logger.debug("Found cover art: releaseGroupId={}, imageUrl={}", releaseGroupId, imageUrl);
                        return imageUrl;
                    }
                }
            }
            logger.debug("No front cover art found: releaseGroupId={}", releaseGroupId);
            return null;
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse Cover Art Archive response: releaseGroupId={}, error={}", releaseGroupId, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Failed to fetch cover art: releaseGroupId={}, error={}", releaseGroupId, e.getMessage());
            return null;
        }
    }

    private String coverArtFallback(String releaseGroupId, Throwable t) {
        logger.warn("Rate limit exceeded for Cover Art Archive API: releaseGroupId={}, error={}", releaseGroupId, t.getMessage());
        return null;
    }

    private String wikipediaFallback(String pageTitle, Throwable t) {
        logger.warn("Rate limit exceeded for Wikipedia API: pageTitle={}, error={}", pageTitle, t.getMessage());
        return null;
    }

    private String wikidataFallback(String wikidataId, String mbid, Throwable t) {
        logger.warn("Rate limit exceeded for Wikidata API: wikidataId={}, mbid={}, error={}", wikidataId, mbid, t.getMessage());
        return null;
    }

    public ArtistLookup rateLimitFallback(String artistName, Throwable t) {
        logger.warn("Rate limit exceeded for MusicBrainz API lookup: artistName={}, error={}", artistName, t.getMessage());
        throw new MusicBrainzApiException("Rate limit exceeded for MusicBrainz API, please try again later", t);
    }

    public Artist rateLimitDetailsFallback(String mbid, Throwable t) {
        logger.warn("Rate limit exceeded for MusicBrainz API details: mbid={}, error={}", mbid, t.getMessage());
        throw new MusicBrainzApiException("Rate limit exceeded for MusicBrainz API, please try again later", t);
    }

    @CacheEvict(value = "artistDetailsCache", key = "#mbid")
    public void evictArtistDetailsCache(String mbid) {
        logger.info("Evicted artist details cache: mbid={}", mbid);
    }

    @CacheEvict(value = "artistDiscographyCache", key = "#artistName.toLowerCase()")
    public void evictArtistDiscographyCache(String artistName) {
        logger.info("Evicted artist discography cache: mbid={}", artistName);
    }

    @CacheEvict(value = "artistLookupCache", key = "#artistName.toLowerCase()")
    public void evictArtistLookupCache(String artistName) {
        logger.info("Evicted artist details cache: mbid={}", artistName);
    }
}