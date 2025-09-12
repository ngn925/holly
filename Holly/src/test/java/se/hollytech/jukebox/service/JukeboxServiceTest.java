package se.hollytech.jukebox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import se.hollytech.jukebox.model.Artist;
import se.hollytech.jukebox.model.ArtistLookup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class JukeboxServiceTest {

    private JukeboxService jukeboxService;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        jukeboxService = new JukeboxService(restTemplate, objectMapper);
    }

    @Test
    void getArtistMbid_Success_ReturnsArtistLookup() throws JsonProcessingException {
        String artistName = "Electric Light Orchestra";
        String mbid = "0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e";
        String jsonResponse = "{\"artists\":[{\"id\":\"" + mbid + "\",\"name\":\"" + artistName + "\"}]}";
        JsonNode rootNode = mock(JsonNode.class);
        JsonNode artistNode = mock(JsonNode.class);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonResponse));
        when(objectMapper.readTree(jsonResponse)).thenReturn(rootNode);
        when(rootNode.path("artists")).thenReturn(rootNode);
        when(rootNode.get(0)).thenReturn(artistNode);
        when(artistNode.path("id")).thenReturn(mock(JsonNode.class));
        when(artistNode.path("name")).thenReturn(mock(JsonNode.class));
        when(artistNode.path("id").asText()).thenReturn(mbid);
        when(artistNode.path("name").asText()).thenReturn(artistName);

        ArtistLookup result = jukeboxService.getArtistMbid(artistName);

        assertEquals(artistName, result.name());
        assertEquals(mbid, result.mbid());
        verify(restTemplate).exchange(contains("query=artist:Electric%20Light%20Orchestra"), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void getArtistMbid_NoArtistsFound_ThrowsArtistNotFoundException() throws JsonProcessingException {
        String artistName = "NonExistentBand";
        String jsonResponse = "{\"artists\":[]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonResponse));
        when(objectMapper.readTree(jsonResponse)).thenReturn(mock(JsonNode.class));

        assertThrows(ArtistNotFoundException.class, () -> jukeboxService.getArtistMbid(artistName));
        verify(restTemplate).exchange(contains("query=artist:NonExistentBand"), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void getArtistMbid_InvalidInput_ThrowsIllegalArgumentException(String artistName) {
        assertThrows(IllegalArgumentException.class, () -> jukeboxService.getArtistMbid(artistName));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getArtistDetails_Success_ReturnsArtist() throws JsonProcessingException {
        // Arrange
        String mbid = "elo-mbid";
        String musicBrainzUrl = "https://musicbrainz.org/ws/2/artist/" + mbid + "?fmt=json&inc=url-rels+release-groups";
        String musicBrainzResponse = """
            {
                "id": "elo-mbid",
                "name": "Electric Light Orchestra",
                "relations": [
                    {
                        "type": "wikipedia",
                        "url": {"resource": "https://en.wikipedia.org/wiki/Electric_Light_Orchestra"}
                    }
                ],
                "release-groups": [
                    {
                        "id": "album1",
                        "title": "Eldorado",
                        "primary-type": "Album"
                    }
                ]
            }
            """;
        String wikipediaResponse = """
            {
                "query": {
                    "pages": {
                        "123": {
                            "pageid": 123,
                            "title": "Electric Light Orchestra",
                            "extract": "<p>ELO is...</p>"
                        }
                    }
                }
            }
            """;
        String coverArtResponse = """
            {
                "images": [
                    {
                        "front": true,
                        "image": "http://coverartarchive.org/release-group/album1/front.jpg"
                    }
                ]
            }
            """;

        // Validate JSON
        JsonNode musicBrainzNode = new ObjectMapper().readTree(musicBrainzResponse);
        assertNotNull(musicBrainzNode, "MusicBrainz JSON should parse to a valid node");
        JsonNode wikipediaNode = new ObjectMapper().readTree(wikipediaResponse);
        assertNotNull(wikipediaNode, "Wikipedia JSON should parse to a valid node");
        JsonNode coverArtNode = new ObjectMapper().readTree(coverArtResponse);
        assertNotNull(coverArtNode, "Cover Art JSON should parse to a valid node");

        // Mock RestTemplate
        when(restTemplate.exchange(
                eq(musicBrainzUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenAnswer(invocation -> {
            System.out.println("MusicBrainz URL invoked: " + invocation.getArgument(0));
            return ResponseEntity.ok(musicBrainzResponse);
        });

        // Mock Wikipedia API
        String wikipediaUrl = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=true&explaintext=false&redirects=true&titles=Electric_Light_Orchestra&format=json";
        when(restTemplate.exchange(
                eq(wikipediaUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenAnswer(invocation -> {
            System.out.println("Wikipedia URL invoked: " + invocation.getArgument(0));
            return ResponseEntity.ok(wikipediaResponse);
        });

        // Mock Cover Art API
        String coverArtUrl = "http://coverartarchive.org/release-group/album1";
        when(restTemplate.exchange(
                eq(coverArtUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenAnswer(invocation -> {
            System.out.println("Cover Art URL invoked: " + invocation.getArgument(0));
            return ResponseEntity.ok(coverArtResponse);
        });

        // Mock ObjectMapper
        when(objectMapper.readTree(musicBrainzResponse)).thenReturn(musicBrainzNode);
        when(objectMapper.readTree(wikipediaResponse)).thenReturn(wikipediaNode);
        when(objectMapper.readTree(coverArtResponse)).thenReturn(coverArtNode);

        // Debug: Log mocked responses
        System.out.println("Mocked MusicBrainz response: " + musicBrainzResponse);
        System.out.println("Mocked MusicBrainz node: " + musicBrainzNode);

        // Act
        Artist result = jukeboxService.getArtistDetails(mbid);

        // Assert
        assertNotNull(result, "Artist should not be null");
        assertEquals("Electric Light Orchestra", result.name(), "Artist name should match");
        assertEquals("<p>ELO is...</p>", result.description(), "Description should match expected");
        assertEquals(mbid, result.mbid(), "MBID should match");
        assertEquals(1, result.albums().size(), "Should have one album");
        assertEquals("Eldorado", result.albums().get(0).title(), "Album title should match");
        assertEquals("http://coverartarchive.org/release-group/album1/front.jpg", result.albums().get(0).image(), "Album image should match");

        // Verify API calls
        verify(restTemplate).exchange(eq(musicBrainzUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(restTemplate).exchange(eq(wikipediaUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(restTemplate).exchange(eq(coverArtUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(objectMapper, times(3)).readTree(anyString());
    }

    @Test
    void getArtistDetails_NoData_ThrowsArtistNotFoundException() throws JsonProcessingException {
        String mbid = "invalid-mbid";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(null));

        assertThrows(ArtistNotFoundException.class, () -> jukeboxService.getArtistDetails(mbid));
        verify(restTemplate).exchange(contains("artist/" + mbid), eq(HttpMethod.GET), any(), eq(String.class));
    }

//    @ParameterizedTest
//    @ValueSource(strings = {"Electric Light Orchestra", "Queen"})
//    void getArtistDiscography_Success_ReturnsArtist(String artistName) throws JsonProcessingException {
//        String mbid = "0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e";
//        String mbResponse = "{\"artists\":[{\"id\":\"" + mbid + "\",\"name\":\"" + artistName + "\"}]}";
//        String detailsResponse = "{\"id\":\"" + mbid + "\",\"name\":\"" + artistName + "\",\"release-groups\":[{\"id\":\"c2e4b8f1-2a4e-4d10-a46a-e9e041da8eb3\",\"title\":\"Eldorado\",\"primary-type\":\"Album\"}]}";
//        String coverArtResponse = "{\"images\":[{\"front\":true,\"image\":\"http://coverartarchive.org/release-group/c2e4b8f1-2a4e-4d10-a46a-e9e041da8eb3/front\"}]}";
//
//        JsonNode mbRoot = mock(JsonNode.class);
//        JsonNode artistNode = mock(JsonNode.class);
//        JsonNode detailsRoot = mock(JsonNode.class);
//        JsonNode releaseGroupsNode = mock(JsonNode.class);
//        JsonNode releaseGroupNode = mock(JsonNode.class);
//        JsonNode coverArtRoot = mock(JsonNode.class);
//        JsonNode imagesNode = mock(JsonNode.class);
//        JsonNode imageNode = mock(JsonNode.class);
//
//        // MusicBrainz MBID
//        when(restTemplate.exchange(contains("query=artist:" + artistName.replace(" ", "%20")), eq(HttpMethod.GET), any(), eq(String.class)))
//                .thenReturn(ResponseEntity.ok(mbResponse));
//        when(objectMapper.readTree(mbResponse)).thenReturn(mbRoot);
//        when(mbRoot.path("artists")).thenReturn(mbRoot);
//        when(mbRoot.get(0)).thenReturn(artistNode);
//        when(artistNode.path("id")).thenReturn(mock(JsonNode.class));
//        when(artistNode.path("name")).thenReturn(mock(JsonNode.class));
//        when(artistNode.path("id").asText()).thenReturn(mbid);
//        when(artistNode.path("name").asText()).thenReturn(artistName);
//
//        // MusicBrainz Details
//        when(restTemplate.exchange(contains("artist/" + mbid), eq(HttpMethod.GET), any(), eq(String.class)))
//                .thenReturn(ResponseEntity.ok(detailsResponse));
//        when(objectMapper.readTree(detailsResponse)).thenReturn(detailsRoot);
//        when(detailsRoot.path("name")).thenReturn(mock(JsonNode.class));
//        when(detailsRoot.path("id")).thenReturn(mock(JsonNode.class));
//        when(detailsRoot.path("name").asText()).thenReturn(artistName);
//        when(detailsRoot.path("id").asText()).thenReturn(mbid);
//        when(detailsRoot.path("release-groups")).thenReturn(releaseGroupsNode);
//        when(releaseGroupsNode.isArray()).thenReturn(true);
//        when(releaseGroupsNode.iterator()).thenReturn(List.of(releaseGroupNode).iterator());
//        when(releaseGroupNode.path("primary-type")).thenReturn(mock(JsonNode.class));
//        when(releaseGroupNode.path("primary-type").asText()).thenReturn("Album");
//        when(releaseGroupNode.path("id")).thenReturn(mock(JsonNode.class));
//        when(releaseGroupNode.path("title")).thenReturn(mock(JsonNode.class));
//        when(releaseGroupNode.path("id").asText()).thenReturn("c2e4b8f1-2a4e-4d10-a46a-e9e041da8eb3");
//        when(releaseGroupNode.path("title").asText()).thenReturn("Eldorado");
//
//        // Cover Art
//        when(restTemplate.exchange(contains("release-group/c2e4b8f1"), eq(HttpMethod.GET), any(), eq(String.class)))
//                .thenReturn(ResponseEntity.ok(coverArtResponse));
//        when(objectMapper.readTree(coverArtResponse)).thenReturn(coverArtRoot);
//        when(coverArtRoot.path("images")).thenReturn(imagesNode);
//        when(imagesNode.isArray()).thenReturn(true);
//        when(imagesNode.iterator()).thenReturn(List.of(imageNode).iterator());
//        when(imageNode.path("front")).thenReturn(mock(JsonNode.class));
//        when(imageNode.path("front").asBoolean()).thenReturn(true);
//        when(imageNode.path("image")).thenReturn(mock(JsonNode.class));
//        when(imageNode.path("image").asText()).thenReturn("http://coverartarchive.org/release-group/c2e4b8f1-2a4e-4d10-a46a-e9e041da8eb3/front");
//
//        Artist result = jukeboxService.getArtistDiscography(artistName);
//
//        assertEquals(artistName, result.name());
//        assertEquals(mbid, result.mbid());
//        assertEquals(1, result.albums().size());
//        assertEquals("Eldorado", result.albums().get(0).title());
//        verify(restTemplate).exchange(contains("query=artist:" + artistName.replace(" ", "%20")), eq(HttpMethod.GET), any(), eq(String.class));
//        verify(restTemplate).exchange(contains("artist/" + mbid), eq(HttpMethod.GET), any(), eq(String.class));
//        verify(restTemplate).exchange(contains("release-group/c2e4b8f1"), eq(HttpMethod.GET), any(), eq(String.class));
//    }

    @Test
    void getArtistDiscography_ArtistNotFound_ThrowsArtistNotFoundException() throws JsonProcessingException {
        String artistName = "NonExistentBand";
        String jsonResponse = "{\"artists\":[]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonResponse));
        when(objectMapper.readTree(jsonResponse)).thenReturn(mock(JsonNode.class));

        assertThrows(ArtistNotFoundException.class, () -> jukeboxService.getArtistDiscography(artistName));
        verify(restTemplate).exchange(contains("query=artist:NonExistentBand"), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void getArtistDiscography_InvalidInput_ThrowsIllegalArgumentException(String artistName) {
        assertThrows(IllegalArgumentException.class, () -> jukeboxService.getArtistDiscography(artistName));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void evictArtistDetailsCache_Success_DoesNotThrow() {
        jukeboxService.evictArtistDetailsCache("0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e");
        // No exception means success
    }
}