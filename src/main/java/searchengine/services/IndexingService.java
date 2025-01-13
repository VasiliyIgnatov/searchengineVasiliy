package searchengine.services;

public interface IndexingService<T> {
    T startIndexing();
    T stopIndexing();
    T indexPage(String url);
}
