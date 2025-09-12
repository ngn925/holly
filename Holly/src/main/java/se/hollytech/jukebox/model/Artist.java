package se.hollytech.jukebox.model;

import java.util.List;

public record Artist(String name, String description, String mbid, List<Album> albums) {
}