package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;

public interface PageRepository extends JpaRepository<PageModel, Integer> {
    void deleteBySite(SiteModel site);
    Boolean existsByPath(String path);
    PageModel findByPath(String path);
}
