package se.hollytech.jukebox.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import se.hollytech.jukebox.model.Artist;
import se.hollytech.jukebox.model.ArtistLookup;
import se.hollytech.jukebox.service.ArtistNotFoundException;
import se.hollytech.jukebox.service.JukeboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.hollytech.jukebox.service.MusicBrainzApiException;

@RestController
public class ArtistController {

    private static final Logger logger = LoggerFactory.getLogger(ArtistController.class);
    private final JukeboxService jukeboxService;

    public ArtistController(JukeboxService jukeboxService) {
        this.jukeboxService = jukeboxService;
    }

    @GetMapping("/api/artist/mbid")
    public ArtistLookup getArtistMbid(@RequestParam String artistName) {
        logger.info("Received MBID lookup request: artistName={}", artistName);
        ArtistLookup artist = jukeboxService.getArtistMbid(artistName);
        logger.debug("Returning artist lookup data: artistName={}, mbid={}", artistName, artist.mbid());
        return artist;
    }

    @GetMapping("/api/artist/details")
    public Artist getArtistDetails(@RequestParam String mbid) {
        logger.info("Received artist details request: mbid={}", mbid);
        Artist artist = jukeboxService.getArtistDetails(mbid);
        logger.debug("Returning artist details: mbid={}", mbid);
        return artist;
    }

    @GetMapping("api/artist/discography")
    public ResponseEntity<Artist> getArtistDiscography(@RequestParam String artistName) {
        try {
            logger.info("Received artist discography request: artistName={}", artistName);
            Artist artist = jukeboxService.getArtistDiscography(artistName);
            return ResponseEntity.ok(artist);
        } catch (ArtistNotFoundException e) {
            logger.warn("Artist not found: artistName={}", artistName);
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("api/artist/details/cache")
    public ResponseEntity<Void> evictArtistDetailsCache(@RequestParam String mbid) {
        try {
            logger.info("Received details cache eviction request: mbid={}", mbid);
            jukeboxService.evictArtistDetailsCache(mbid);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Failed to evict details cache: mbid={}, error={}", mbid, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("api/artist/lookup/cache")
    public ResponseEntity<Void> evictArtistLookupCache(@RequestParam String artistName) {
        try {
            logger.info("Received lookup cache eviction request: artistName={}", artistName);
            jukeboxService.evictArtistLookupCache(artistName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Failed to evict lookup cache: artistName={}, error={}", artistName, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("api/artist/discography/cache")
    public ResponseEntity<Void> evictArtistDiscographyCache(@RequestParam String artistName) {
        try {
            logger.info("Received discography cache eviction request: artistName={}", artistName);
            jukeboxService.evictArtistDiscographyCache(artistName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Failed to evict discography cache: artistName={}, error={}", artistName, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
}