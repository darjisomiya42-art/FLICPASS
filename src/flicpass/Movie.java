package flicpass;

import java.time.LocalDate;

public class Movie {
    int movieId;
    String title;
    String genre;
    String language;
    int duration;
    LocalDate releaseDate;

    public Movie(int movieId, String title, String genre, String language, int duration) {
        this.movieId = movieId;
        this.title = title;
        this.genre = genre;
        this.language = language;
        this.duration = duration;
    }

    public Movie(int movieId, String title, String genre, String language, int duration, LocalDate releaseDate) {
        this.movieId = movieId;
        this.title = title;
        this.genre = genre;
        this.language = language;
        this.duration = duration;
        this.releaseDate = releaseDate;
    }

    public int getDuration() { return duration; }
    public String getTitle() { return title; }
    public LocalDate getReleaseDate() { return releaseDate; }
}

