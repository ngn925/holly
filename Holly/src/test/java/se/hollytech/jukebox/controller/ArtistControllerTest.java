package se.hollytech.jukebox.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import se.hollytech.jukebox.model.Artist;
import se.hollytech.jukebox.model.ArtistLookup;
import se.hollytech.jukebox.model.Album;
import se.hollytech.jukebox.service.ArtistNotFoundException;
import se.hollytech.jukebox.service.JukeboxService;
import se.hollytech.jukebox.service.MusicBrainzApiException;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ArtistController.class)
class ArtistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JukeboxService jukeboxService;

    @Test
    void getArtistMbid_Success_ReturnsArtistLookup() throws Exception {
        ArtistLookup artistLookup = new ArtistLookup("Electric Light Orchestra", "0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e");
        when(jukeboxService.getArtistMbid("Electric Light Orchestra")).thenReturn(artistLookup);

        mockMvc.perform(get("/api/artist/mbid")
                        .param("artistName", "Electric Light Orchestra")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Electric Light Orchestra"))
                .andExpect(jsonPath("$.mbid").value("0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e"));

        verify(jukeboxService).getArtistMbid("Electric Light Orchestra");
    }

    @Test
    void getArtistMbid_ArtistNotFound_Returns404() throws Exception {
        when(jukeboxService.getArtistMbid("NonExistentBand"))
                .thenThrow(new ArtistNotFoundException("No artists found for query: NonExistentBand"));

        mockMvc.perform(get("/api/artist/mbid")
                        .param("artistName", "NonExistentBand")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(jukeboxService).getArtistMbid("NonExistentBand");
    }

    @Test
    void getArtistMbid_ApiError_Returns429() throws Exception {
        when(jukeboxService.getArtistMbid("Electric Light Orchestra"))
                .thenThrow(new MusicBrainzApiException("Rate limit exceeded", new Exception("API error")));

        mockMvc.perform(get("/api/artist/mbid")
                        .param("artistName", "Electric Light Orchestra")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"));

        verify(jukeboxService).getArtistMbid("Electric Light Orchestra");
    }

    @Test
    void getArtistDetails_Success_ReturnsArtist() throws Exception {
        List<Album> albums = List.of(
                new Album("Eldorado", "c2e4b8f1-2a4e-4d10-a46a-e9e041da8eb3", "http://coverartarchive.org/release-group/c2e4b8f1-2a4e-4d10-a46a-e9e041da8eb3/front")
        );
        Artist artist = new Artist("Electric Light Orchestra", "<p>ELO is...</p>", "0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e", albums);
        when(jukeboxService.getArtistDetails("0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e")).thenReturn(artist);

        mockMvc.perform(get("/api/artist/details")
                        .param("mbid", "0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Electric Light Orchestra"))
                .andExpect(jsonPath("$.mbid").value("0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e"))
                .andExpect(jsonPath("$.description").value("<p>ELO is...</p>"))
                .andExpect(jsonPath("$.albums[0].title").value("Eldorado"));

        verify(jukeboxService).getArtistDetails("0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e");
    }

    @Test
    void getArtistDetails_ArtistNotFound_Returns404() throws Exception {
        when(jukeboxService.getArtistDetails("invalid-mbid"))
                .thenThrow(new ArtistNotFoundException("No data found for MBID: invalid-mbid"));

        mockMvc.perform(get("/api/artist/details")
                        .param("mbid", "invalid-mbid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(jukeboxService).getArtistDetails("invalid-mbid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Electric Light Orchestra", "Queen"})
    void getArtistDiscography_Success_ReturnsArtist(String artistName) throws Exception {
        List<Album> albums = List.of(
                new Album("Eldorado", "c2e4b8f1-2a4e-4d10-a46a-e9e041da8eb3", "http://coverartarchive.org/release-group/c2e4b8f1-2a4e-4d10-a46a-e9e041da8eb3/front")
        );
        Artist artist = new Artist(artistName, "<p>" + artistName + " is...</p>", "0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e", albums);
        when(jukeboxService.getArtistDiscography(artistName)).thenReturn(artist);

        mockMvc.perform(get("/api/artist/discography")
                        .param("artistName", artistName)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(artistName))
                .andExpect(jsonPath("$.mbid").value("0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e"))
                .andExpect(jsonPath("$.description").value("<p>" + artistName + " is...</p>"))
                .andExpect(jsonPath("$.albums[0].title").value("Eldorado"));

        verify(jukeboxService).getArtistDiscography(artistName);
    }

    @Test
    void getArtistDiscography_ArtistNotFound_Returns404() throws Exception {
        when(jukeboxService.getArtistDiscography("NonExistentBand"))
                .thenThrow(new ArtistNotFoundException("No artists found for query: NonExistentBand"));

        mockMvc.perform(get("/api/artist/discography")
                        .param("artistName", "NonExistentBand")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(jukeboxService).getArtistDiscography("NonExistentBand");
    }

    @Test
    void getArtistDiscography_ApiError_Returns429() throws Exception {
        when(jukeboxService.getArtistDiscography("Electric Light Orchestra"))
                .thenThrow(new MusicBrainzApiException("Rate limit exceeded", new Exception("API error")));

        mockMvc.perform(get("/api/artist/discography")
                        .param("artistName", "Electric Light Orchestra")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"));

        verify(jukeboxService).getArtistDiscography("Electric Light Orchestra");
    }

    @Test
    void evictArtistDetailsCache_Success_Returns200() throws Exception {
        doNothing().when(jukeboxService).evictArtistDetailsCache("0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e");

        mockMvc.perform(delete("/api/artist/details/cache")
                        .param("mbid", "0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(jukeboxService).evictArtistDetailsCache("0c0b7ac3-266f-47e4-8e87-02d1d1eb4f0e");
    }

    @Test
    void evictArtistDetailsCache_Error_Returns500() throws Exception {
        doThrow(new RuntimeException("Cache eviction failed")).when(jukeboxService).evictArtistDetailsCache("invalid-mbid");

        mockMvc.perform(delete("/api/artist/details/cache")
                        .param("mbid", "invalid-mbid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(jukeboxService).evictArtistDetailsCache("invalid-mbid");
    }
}