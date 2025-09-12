# JukeboxService API Documentation

This document provides a comprehensive overview of the JukeboxService API, a RESTful service for retrieving music artist information, including MBID lookup, detailed artist profiles, and discography data. The API is built using Spring Boot and integrates with external services like MusicBrainz, Wikidata, Wikipedia, and Cover Art Archive.

## Base URL
All endpoints are relative to the base URL:
```
https://localhost:8080/api/artist
```
(Adjust for production deployment.)

## Authentication
No authentication is required. However, the API respects external service rate limits (e.g., MusicBrainz: 1 request/second).

## Error Handling
- **Common HTTP Status Codes**:
  - 200 OK: Successful response.
  - 400 Bad Request: Invalid input (e.g., empty artistName).
  - 404 Not Found: Artist or MBID not found.
  - 429 Too Many Requests: Rate limit exceeded (handled by GlobalExceptionHandler for rate limit errors).
  - 500 Internal Server Error: Unexpected server error.
- **Error Response Format** (JSON):
  ```json
  {
    "error": "Bad Request",
    "message": "Artist name cannot be empty"
  }
  ```
- **GlobalExceptionHandler**: Catches `IllegalArgumentException` (400), `ArtistNotFoundException` (404), and `MusicBrainzApiException` (429 for rate limits, 500 otherwise).

## Endpoints

### 1. GET /api/artist/mbid
**Description**: Retrieves the MusicBrainz ID (MBID) for an artist by name.

**Path Parameters**: None.

**Query Parameters**:
| Name        | Type   | Required | Description                  |
|-------------|--------|----------|------------------------------|
| artistName  | string | Yes      | Artist name (e.g., "ABBA").  |

**Request Example**:
```
GET /api/artist/mbid?artistName=ABBA
```

**Response**:
- **Success (200)**:
  ```json
  {
    "name": "ABBA",
    "mbid": "d87e52c5-bb8d-4da8-b941-9f4928627dc8"
  }
  ```
- **Error (400)**: Empty artistName.
  ```json
  {
    "error": "Bad Request",
    "message": "Artist name cannot be empty"
  }
  ```
- **Error (404)**: No artist found.
  ```json
  {
    "error": "Not Found",
    "message": "No artists found for query: NonExistentBand"
  }
  ```
- **Error (429)**: Rate limit exceeded.
  ```json
  {
    "error": "Too Many Requests",
    "message": "Rate limit exceeded for MusicBrainz API, please try again later"
  }
  ```

**Caching**: Cached in `artistLookupCache` (1 hour TTL).

**Rate Limiting**: Applied via `musicBrainzApi` (1 request/second).

### 2. GET /api/artist/details
**Description**: Retrieves detailed artist information, including description and albums, using the MBID.

**Path Parameters**: None.

**Query Parameters**:
| Name | Type   | Required | Description                  |
|------|--------|----------|------------------------------|
| mbid | string | Yes      | MusicBrainz ID (e.g., "d87e52c5-bb8d-4da8-b941-9f4928627dc8"). |

**Request Example**:
```
GET /api/artist/details?mbid=d87e52c5-bb8d-4da8-b941-9f4928627dc8
```

**Response**:
- **Success (200)**:
  ```json
  {
    "name": "ABBA",
    "description": "<p>ABBA is a Swedish pop supergroup...</p>",
    "mbid": "d87e52c5-bb8d-4da8-b941-9f4928627dc8",
    "albums": [
      {
        "title": "Arrival",
        "id": "b3b6e8e0-1d73-4a9f-9f4b-4e7c6e4f2c3d",
        "image": "http://coverartarchive.org/release-group/b3b6e8e0-1d73-4a9f-9f4b-4e7c6e4f2c3d/front"
      },
      {
        "title": "Super Trouper",
        "id": "a7f7f7f7-2a4e-4d10-a46a-e9e041da8eb3",
        "image": "http://coverartarchive.org/release-group/a7f7f7f7-2a4e-4d10-a46a-e9e041da8eb3/front"
      }
    ]
  }
  ```
- **Error (400)**: Invalid MBID.
  ```json
  {
    "error": "Bad Request",
    "message": "MBID cannot be empty"
  }
  ```
- **Error (404)**: No artist data found.
  ```json
  {
    "error": "Not Found",
    "message": "No data found for MBID: invalid-mbid"
  }
  ```
- **Error (429)**: Rate limit exceeded.
  ```json
  {
    "error": "Too Many Requests",
    "message": "Rate limit exceeded for MusicBrainz API, please try again later"
  }
  ```

**Caching**: Cached in `artistDetailsCache` (1 hour TTL).

**Rate Limiting**: Applied via `musicBrainzApi` (1 request/second).

### 3. GET /api/artist/discography
**Description**: Retrieves the full discography for an artist by name, combining MBID lookup and details.

**Path Parameters**: None.

**Query Parameters**:
| Name        | Type   | Required | Description                  |
|-------------|--------|----------|------------------------------|
| artistName  | string | Yes      | Artist name (e.g., "Electric Light Orchestra"). |

**Request Example**:
```
GET /api/artist/discography?artistName=ABBA
```

**Response**:
- **Success (200)**: Same as `/api/artist/details`.
  ```json
  {
    "name": "ABBA",
    "description": "<p>ABBA is a Swedish pop supergroup...</p>",
    "mbid": "d87e52c5-bb8d-4da8-b941-9f4928627dc8",
    "albums": [
      {
        "title": "Arrival",
        "id": "b3b6e8e0-1d73-4a9f-9f4b-4e7c6e4f2c3d",
        "image": "http://coverartarchive.org/release-group/b3b6e8e0-1d73-4a9f-9f4b-4e7c6e4f2c3d/front"
      }
    ]
  }
  ```
- **Error (400)**: Empty artistName.
  ```json
  {
    "error": "Bad Request",
    "message": "Artist name cannot be empty"
  }
  ```
- **Error (404)**: No artist found.
  ```json
  {
    "error": "Not Found",
    "message": "No artists found for query: NonExistentBand"
  }
  ```
- **Error (429)**: Rate limit exceeded.
  ```json
  {
    "error": "Too Many Requests",
    "message": "Rate limit exceeded for MusicBrainz API, please try again later"
  }
  ```

**Caching**: Cached in `artistDiscographyCache` (1 hour TTL).

**Rate Limiting**: Applied via `musicBrainzApi` (1 request/second).

### 4. DELETE /api/artist/details/cache
**Description**: Evicts the cached artist details for a specific MBID.

**Path Parameters**: None.

**Query Parameters**:
| Name | Type   | Required | Description                  |
|------|--------|----------|------------------------------|
| mbid | string | Yes      | MusicBrainz ID to evict.     |

**Request Example**:
```
DELETE /api/artist/details/cache?mbid=d87e52c5-bb8d-4da8-b941-9f4928627dc8
```

**Response**:
- **Success (200)**: No body.
- **Error (500)**: Cache eviction failed.
  ```json
  {
    "error": "Internal Server Error",
    "message": "Cache eviction failed"
  }
  ```

**Caching**: Evicts from `artistDetailsCache`.

**Rate Limiting**: Not applied.

## Data Models

### Artist
```json
{
  "name": "string",
  "description": "string (HTML Wikipedia extract, or null)",
  "mbid": "string",
  "albums": [
    {
      "title": "string",
      "id": "string (release-group ID)",
      "image": "string (cover art URL, or null)"
    }
  ]
}
```

### ArtistLookup
```json
{
  "name": "string",
  "mbid": "string"
}
```

### Album
```json
{
  "title": "string",
  "id": "string (release-group ID)",
  "image": "string (cover art URL, or null)"
}
```

### ErrorResponse
```json
{
  "error": "string",
  "message": "string"
}
```

## Caching
- **Cache Names**: `artistLookupCache` (MBID lookup), `artistDetailsCache` (details by MBID), `artistDiscographyCache` (discography by name).
- **TTL**: 1 hour (configurable via `spring.cache.caffeine.spec`).
- **Eviction**: Use `/api/artist/details/cache?mbid={mbid}` to evict details cache.

## Rate Limiting
- **musicBrainzApi**: 1 request/second (fallback to `rateLimitFallback`).
- **wikidataApi**: 1 request/second (fallback to `wikidataFallback`).
- **wikipediaApi**: 1 request/second (fallback to `wikipediaFallback`).
- **coverArtApi**: 1 request/second (fallback to `coverArtFallback`).
- **Fallbacks**: Log warnings and throw `MusicBrainzApiException` for MusicBrainz errors, or return `null` for non-critical APIs (e.g., cover art).

## External Dependencies
- **MusicBrainz API**: Artist lookup and details (rate limit: 1 request/second).
- **Wikidata API**: Wikipedia page title from Wikidata ID.
- **Wikipedia API**: Artist description (extract).
- **Cover Art Archive**: Album cover images.

## Version
v1.0.0 (September 12, 2025)

## Changelog
- **v1.0.0**: Initial release with MBID lookup, details, discography, and caching/rate limiting.